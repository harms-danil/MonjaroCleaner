package app.mcleaner.update

import app.mcleaner.core.i18n.tr
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.mcleaner.BuildConfig
import app.mcleaner.core.update.ApkFiles
import app.mcleaner.core.update.ApkVerdict
import app.mcleaner.core.update.DownloadUrl
import app.mcleaner.core.update.HttpStatusException
import app.mcleaner.core.update.InstallLog
import app.mcleaner.core.update.InstallOutcome
import app.mcleaner.core.update.LATEST_RELEASE_URL
import app.mcleaner.core.update.ReleaseInfo
import app.mcleaner.core.update.ReleaseParser
import app.mcleaner.core.update.Retry
import app.mcleaner.core.update.RollbackScript
import app.mcleaner.core.update.ShellSha
import app.mcleaner.core.update.UPDATE_REPO
import app.mcleaner.core.update.Version
import app.mcleaner.core.update.VersionCatalog
import app.mcleaner.core.update.VersionEntry
import app.mcleaner.core.update.parseList
import app.mcleaner.data.MCleanerFolder
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class CheckFailed(val message: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val release: ReleaseInfo) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val release: ReleaseInfo, val message: String) : UpdateState
}

/** «Другие версии»: what is on the device plus what is on GitHub. */
data class VersionsState(val loading: Boolean = false, val entries: List<VersionEntry> = emptyList(), val note: String? = null)

/**
 * Checks GitHub for a newer release, downloads the APK and installs it:
 * silently via Shizuku (`pm install`), otherwise through the system installer dialog.
 * Keeps the last [KEEP_VERSIONS] downloaded versions and can install an older one (Shizuku only).
 * Every install first checks that the APK is MCleaner signed with the installed app's key.
 */
class UpdateManager(
    private val context: Context,
    private val privileged: Privileged,
    private val folder: MCleanerFolder,
) {
    /** An APK in app-private storage whose SHA-256 we computed ourselves. */
    private class LocalApk(val file: File, val sha256: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val verifier = ApkVerifier(context)
    private var job: Job? = null
    private var autoChecking = false
    private var watchdog: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The update window; closed by «Позже» until the next launch or manual action. */
    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog.asStateFlow()

    /** True while the target is older than the installed version. */
    private val _rollback = MutableStateFlow(false)
    val rollback: StateFlow<Boolean> = _rollback.asStateFlow()

    private val _versions = MutableStateFlow(VersionsState())
    val versions: StateFlow<VersionsState> = _versions.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME
    val canRollback: Boolean get() = privileged.isAvailable

    /** On app start: up to 5 attempts 15 s apart when offline, failures stay silent. */
    fun autoCheck() = check(manual = false)

    /** A manual check replaces an automatic one that is still waiting for the network. */
    fun manualCheck() {
        if (autoChecking) job?.cancel()
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        if (busy()) return
        autoChecking = !manual
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            val result = runCatching {
                if (manual) fetchLatest() else Retry.retrying(AUTO_ATTEMPTS, AUTO_PAUSE_MS) { fetchLatest() }
            }
            ensureActive()
            if (!manual) autoChecking = false
            val release = result.getOrNull()
            _state.value = when {
                result.isFailure -> if (manual) UpdateState.CheckFailed(describe(result.exceptionOrNull())) else UpdateState.Idle
                release != null && Version.isNewer(release.version, currentVersion) -> {
                    _rollback.value = false
                    _dialog.value = true
                    UpdateState.Available(release)
                }
                else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
        }
    }

    /** Only hides the window; a running download or install goes on. */
    fun later() {
        _dialog.value = false
    }

    fun showDialog() {
        _dialog.value = true
    }

    /** «Скачать и установить» / «Повторить» in the update window. */
    fun downloadAndInstall() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedsPermission -> s.release
            is UpdateState.Failed -> s.release
            else -> return
        }
        start(release, _rollback.value)
    }

    /** Install a version picked in «Другие версии»; older than current → rollback. */
    fun installVersion(entry: VersionEntry) {
        if (!entry.installable) return
        scope.launch {
            val release = entry.release ?: withContext(Dispatchers.IO) { localRelease(entry.version) } ?: return@launch
            start(release, rollback = !entry.newer)
        }
    }

    private fun start(release: ReleaseInfo, rollback: Boolean) {
        if (busy()) return
        if (rollback && !privileged.isAvailable) {
            _state.value = UpdateState.Failed(release, tr("Установить старую версию можно только с запущенным Shizuku", "Older versions can only be installed while Shizuku is running"))
            return
        }
        _rollback.value = rollback
        _dialog.value = true
        job = scope.launch {
            try {
                val apk = obtain(release)
                requireTrusted(apk.file)
                enterInstalling(release)
                if (rollback) rollbackTo(apk) else install(release, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(release, describe(e))
            }
        }
    }

    /** Installing ends in a kill (success), a result or [INSTALL_WATCHDOG_MS] without an answer → Failed. */
    private fun enterInstalling(release: ReleaseInfo) {
        val installing = UpdateState.Installing(release)
        _state.value = installing
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(INSTALL_WATCHDOG_MS)
            if (_state.value === installing) {
                job?.cancel()
                _state.value = UpdateState.Failed(release, tr("Нет ответа от установщика", "No answer from the installer"))
            }
        }
    }

    private fun busy() = job?.isActive == true ||
        _state.value.let { it is UpdateState.Downloading || it is UpdateState.Installing }

    /** A successful install kills MCleaner before it can delete the staged copy: remove it on the next start. */
    suspend fun cleanupStaged() {
        val installing = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Installing }
        if (installing) return
        withContext(Dispatchers.IO) { runCatching { stageFile()?.delete() } }
        if (privileged.isAvailable) privileged.exec("rm -f $STAGED $TMP/mcleaner-install.txt")
    }

    /** Called by [UpdateInstallReceiver] with the system installer's verdict. */
    fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release ?: return
        if (status != PackageInstaller.STATUS_SUCCESS) {
            _state.value = UpdateState.Failed(
                release,
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) tr("Установка отменена", "Installation cancelled") else tr("Не удалось установить: ${message ?: "ошибка $status"}", "Could not install: ${message ?: "error $status"}"),
            )
        }
    }

    /** Some firmwares lack the «unknown sources» screen: fall back to the app's settings card. */
    fun openInstallPermissionScreen() {
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (opened) return
        val fallback = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        val release = (_state.value as? UpdateState.NeedsPermission)?.release ?: return
        _state.value = UpdateState.Failed(
            release,
            if (fallback) tr("Экран разрешения не открылся. В карточке MCleaner разрешите установку приложений, вернитесь и нажмите «Повторить».",
                "The permission screen did not open. In the MCleaner app info allow installing apps, come back and tap \"Retry\".")
            else tr("Не удалось открыть настройки. Разрешите MCleaner установку приложений в системных настройках.",
                "Could not open settings. Allow MCleaner to install apps in the system settings."),
        )
    }

    // --- versions list ---------------------------------------------------------------------

    fun loadVersions() {
        _versions.value = _versions.value.copy(loading = true, note = null)
        scope.launch {
            val local = withContext(Dispatchers.IO) { localVersions() }
            val remote = runCatching { fetchAll() }
            _versions.value = VersionsState(
                loading = false,
                entries = VersionCatalog.merge(local, remote.getOrDefault(emptyList()), currentVersion),
                note = remote.exceptionOrNull()?.let { tr("Показаны только версии на устройстве. ${describe(it)}", "Only versions on the device are shown. ${describe(it)}") },
            )
        }
    }

    private fun localVersions(): Set<String> =
        folder.updatesDir().listFiles().orEmpty().mapNotNull { ApkFiles.versionOf(it.name) }.toSet()

    /** Files in app-private storage were written only by MCleaner: no published hash needed, the signature is still checked. */
    private fun localRelease(version: String): ReleaseInfo? {
        val f = apkFile(version)
        return if (f.isFile) ReleaseInfo(version, "", "", f.length(), null) else null
    }

    // --- network ---------------------------------------------------------------------------

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        get(LATEST_RELEASE_URL, allow404 = true)?.let(ReleaseParser::parse)
    }

    private suspend fun fetchAll(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        get("https://api.github.com/repos/$UPDATE_REPO/releases?per_page=50", allow404 = true)?.let(ReleaseParser::parseList).orEmpty()
    }

    private fun get(url: String, allow404: Boolean): String? {
        val conn = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (allow404 && conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** The APK for [release]: reuse a verified copy on the device, otherwise download it. */
    private suspend fun obtain(release: ReleaseInfo): LocalApk {
        val apk = apkFile(release.version)
        withContext(Dispatchers.IO) { if (apk.isFile) verified(apk, release) else null }?.let { return LocalApk(apk, it) }
        if (release.apkUrl.isEmpty()) throw IOException(tr("Файл версии ${release.version} повреждён, а на GitHub её нет", "The file for version ${release.version} is corrupted and it is not on GitHub"))
        _state.value = UpdateState.Downloading(release, 0f)
        val sha = download(release, apk)
        withContext(Dispatchers.IO) { prune(keep = apk) }
        return LocalApk(apk, sha)
    }

    /** Downloads into [target] and returns its SHA-256 (already checked against the release). */
    private suspend fun download(release: ReleaseInfo, target: File): String = withContext(Dispatchers.IO) {
        if (!DownloadUrl.isAllowed(release.apkUrl)) throw IOException(tr("Недопустимая ссылка на файл обновления", "Invalid update download link"))
        val tooBig = IOException(tr("Файл обновления слишком большой", "The update file is too large"))
        if (DownloadUrl.tooBig(release.apkSize)) throw tooBig
        val dir = target.parentFile ?: throw IOException(tr("Нет папки для загрузки", "No download folder"))
        dir.mkdirs()
        dir.listFiles().orEmpty().filter { it.name.endsWith(".part") }.forEach { it.delete() }
        val part = File(target.path + ".part")
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(conn.responseCode)
            if (DownloadUrl.tooBig(conn.contentLengthLong)) throw tooBig
            val total = release.apkSize.takeIf { it > 0 } ?: conn.contentLengthLong
            var done = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (DownloadUrl.tooBig(done)) throw tooBig
                        out.write(buf, 0, n)
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != shown) {
                            shown = pct
                            _state.value = UpdateState.Downloading(release, pct / 100f)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException(tr("Не удалось сохранить файл", "Could not save the file"))
        verified(target, release) ?: run {
            target.delete()
            throw IOException(tr("Файл скачался повреждённым, попробуйте ещё раз", "The downloaded file is corrupted, please try again"))
        }
    }

    /** Keep the [KEEP_VERSIONS] newest downloaded versions (never the one we are about to install). */
    private fun prune(keep: File) {
        val files = folder.updatesDir().listFiles().orEmpty().filter { ApkFiles.versionOf(it.name) != null }
        val byVersion = files.associateBy { ApkFiles.versionOf(it.name)!! }
        ApkFiles.toDelete(byVersion.keys.toList(), KEEP_VERSIONS)
            .mapNotNull(byVersion::get)
            .filter { it != keep }
            .forEach { it.delete() }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "MCleaner/$currentVersion")
    }

    /** SHA-256 of [apk] if it matches [release] (size; hash for GitHub releases), otherwise null. Blocking. */
    private fun verified(apk: File, release: ReleaseInfo): String? {
        if (release.apkSize > 0 && apk.length() != release.apkSize) return null
        val actual = sha256(apk)
        return actual.takeIf { release.sha256 == null || it == release.sha256 }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun apkFile(version: String) = File(folder.updatesDir(), ApkFiles.nameOf(version))

    /** Refuses anything but MCleaner signed with the installed app's key; a bad file is removed. */
    private suspend fun requireTrusted(apk: File) = withContext(Dispatchers.IO) {
        val reason = when (verifier.verdict(apk)) {
            ApkVerdict.OK -> return@withContext
            ApkVerdict.NOT_APK -> tr("Файл обновления не является приложением Android", "The update file is not an Android app")
            ApkVerdict.WRONG_PACKAGE -> tr("Файл обновления — не MCleaner, установка отменена", "The update file is not MCleaner; installation cancelled")
            ApkVerdict.WRONG_SIGNER -> tr("Файл обновления подписан чужим ключом, установка отменена", "The update file is signed with a different key; installation cancelled")
        }
        apk.delete()
        throw IOException(reason)
    }

    // --- install ---------------------------------------------------------------------------

    private suspend fun install(release: ReleaseInfo, apk: LocalApk) {
        if (privileged.isAvailable) {
            // On success the system kills this process and the detached shell starts MCleaner again.
            val staged = stage(apk)
            if (staged != null) {
                when (installDetached(staged, downgrade = false)) {
                    InstallOutcome.SUCCESS_PENDING -> return
                    InstallOutcome.TIMEOUT -> throw IOException(noAnswer())
                    InstallOutcome.FAILED -> {} // the system refused: try the installer dialog
                }
            }
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        // The new version opens itself after the update (see [UpdateRelaunchReceiver]).
        UpdateRelaunchReceiver.arm(context)
        withContext(Dispatchers.IO) { installWithSession(apk.file) }
    }

    /**
     * `pm install` in a detached shell that restarts MCleaner on success. On success this process is
     * killed while waiting; a timeout means the outcome is unknown, so callers must not try anything else.
     */
    private suspend fun installDetached(staged: String, downgrade: Boolean): InstallOutcome {
        val result = "$TMP/mcleaner-install.txt"
        privileged.exec("echo > $result")
        val script = RollbackScript.installAndRestart(staged, result, "${context.packageName}/.MainActivity", downgrade)
        if (!privileged.exec(RollbackScript.detached(script)).ok) return InstallOutcome.FAILED
        repeat(INSTALL_WAIT_S) {
            delay(1_000)
            when (InstallLog.verdict(privileged.exec("cat $result").out)) {
                InstallOutcome.FAILED -> {
                    privileged.exec("rm -f $staged")
                    return InstallOutcome.FAILED
                }
                InstallOutcome.SUCCESS_PENDING -> return InstallOutcome.SUCCESS_PENDING
                else -> {}
            }
        }
        return InstallOutcome.TIMEOUT
    }

    /**
     * Older version. First `pm install -r -d` (keeps data; allowed on debuggable firmware).
     * If the system refuses the downgrade: stage the (already verified) APK again and, in a detached shell,
     * uninstall → install the old one (or the current one back on failure) → start MCleaner.
     */
    private suspend fun rollbackTo(apk: LocalApk) {
        val prepare = tr("Не удалось подготовить файл для установки", "Could not prepare the file for installation")
        val staged = stage(apk) ?: throw IOException(prepare)
        when (installDetached(staged, downgrade = true)) {
            InstallOutcome.SUCCESS_PENDING -> return
            InstallOutcome.TIMEOUT -> throw IOException(noAnswer())
            InstallOutcome.FAILED -> {}
        }
        val target = stage(apk) ?: throw IOException(prepare)
        val current = "$TMP/mcleaner-current.apk"
        val copy = privileged.exec("cp ${shellQuote(context.applicationInfo.sourceDir)} $current && chmod 644 $current")
        if (!copy.ok) throw IOException(tr("Не удалось подготовить файлы для переустановки: ${copy.out.take(200)}", "Could not prepare files for reinstall: ${copy.out.take(200)}"))
        val script = RollbackScript.reinstallSteps(target, current, context.packageName, "${context.packageName}/.MainActivity")
        val r = privileged.exec(RollbackScript.detached(script))
        if (!r.ok) throw IOException(tr("Не удалось запустить переустановку: ${r.out.take(200)}", "Could not start reinstall: ${r.out.take(200)}"))
        // MCleaner is uninstalled within seconds; nothing else to do here.
    }

    /**
     * `pm install <path>` makes system_server open the file itself, so it must sit in /data/local/tmp.
     * Shell cannot read app-private files: hand it over through our external files dir, then compare
     * the hash shell sees with ours, so nobody can swap the file between the check and the install.
     * null = staging is not possible (use the installer dialog); a hash mismatch throws.
     */
    private suspend fun stage(apk: LocalApk): String? {
        val handOver = withContext(Dispatchers.IO) {
            stageFile()?.also { apk.file.copyTo(it, overwrite = true) }
        } ?: return null
        val copied = try {
            privileged.exec("cp ${shellQuote(handOver.absolutePath)} $STAGED && chmod 644 $STAGED").ok
        } finally {
            runCatching { handOver.delete() }
        }
        if (!copied) return null
        val seen = ShellSha.parse(privileged.exec("sha256sum $STAGED").out)
        if (seen != apk.sha256) {
            privileged.exec("rm -f $STAGED")
            if (seen == null) return null
            throw IOException(tr("Файл обновления подменили при подготовке к установке, установка отменена",
                "The update file was replaced while preparing the install; installation cancelled"))
        }
        return STAGED
    }

    private fun stageFile(): File? = context.getExternalFilesDir("stage")?.let { File(it, "mcleaner-update.apk") }

    private fun noAnswer() = tr("Установщик не ответил. Если MCleaner не обновился, нажмите «Повторить».",
        "The installer did not answer. If MCleaner did not update, tap \"Retry\".")

    private fun installWithSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    private fun describe(e: Throwable?): String = when (e) {
        is HttpStatusException -> if (e.code == 403) tr("GitHub временно ограничил запросы, попробуйте позже", "GitHub has temporarily rate-limited requests, try again later") else tr("GitHub ответил ошибкой ${e.code}", "GitHub returned error ${e.code}")
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException,
        is java.net.NoRouteToHostException, is javax.net.ssl.SSLException -> tr("Нет связи с GitHub", "Cannot reach GitHub")
        is java.io.FileNotFoundException -> tr("Нет доступа к папке для загрузки: ${e.message}", "No access to the download folder: ${e.message}")
        is IOException -> e.message?.replaceFirstChar(Char::uppercase) ?: tr("Ошибка ввода-вывода", "I/O error")
        null -> tr("Неизвестная ошибка", "Unknown error")
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val AUTO_ATTEMPTS = 5
        const val AUTO_PAUSE_MS = 15_000L
        const val TIMEOUT_MS = 15_000
        const val KEEP_VERSIONS = 5
        private const val INSTALL_WAIT_S = 120
        private const val INSTALL_WATCHDOG_MS = 3 * 60_000L
        private const val TMP = "/data/local/tmp"
        private const val STAGED = "$TMP/mcleaner-update.apk"
    }
}
