package app.mcleaner.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.mcleaner.BuildConfig
import app.mcleaner.core.update.HttpStatusException
import app.mcleaner.core.update.LATEST_RELEASE_URL
import app.mcleaner.core.update.ReleaseInfo
import app.mcleaner.core.update.ReleaseParser
import app.mcleaner.core.update.Retry
import app.mcleaner.core.update.Version
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/**
 * Checks GitHub for a newer release, downloads the APK and installs it:
 * silently via Shizuku (`pm install`), otherwise through the system installer dialog.
 */
class UpdateManager(private val context: Context, private val privileged: Privileged) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The "update available" window; closed by «Позже» until the next launch or manual check. */
    private val _dialog = MutableStateFlow(false)
    val dialog: StateFlow<Boolean> = _dialog.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** On app start: up to 5 attempts 15 s apart when offline, failures stay silent. */
    fun autoCheck() = check(manual = false)

    fun manualCheck() = check(manual = true)

    private fun check(manual: Boolean) {
        if (job?.isActive == true) return
        if (_state.value.let { it is UpdateState.Downloading || it is UpdateState.Installing }) return
        job = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            val result = runCatching {
                if (manual) fetchLatest() else Retry.retrying(AUTO_ATTEMPTS, AUTO_PAUSE_MS) { fetchLatest() }
            }
            val release = result.getOrNull()
            _state.value = when {
                result.isFailure -> if (manual) UpdateState.CheckFailed(describe(result.exceptionOrNull())) else UpdateState.Idle
                release != null && Version.isNewer(release.version, currentVersion) -> UpdateState.Available(release).also { _dialog.value = true }
                else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
        }
    }

    fun later() {
        _dialog.value = false
    }

    fun showDialog() {
        _dialog.value = true
    }

    fun downloadAndInstall() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedsPermission -> s.release
            is UpdateState.Failed -> s.release
            else -> return
        }
        if (job?.isActive == true) return
        _dialog.value = true
        job = scope.launch {
            try {
                val apk = apkFile(release)
                if (!(apk.exists() && verified(apk, release))) {
                    _state.value = UpdateState.Downloading(release, 0f)
                    download(release, apk)
                }
                install(release, apk)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(release, describe(e))
            }
        }
    }

    /** Called by [UpdateInstallReceiver] with the system installer's verdict. */
    fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release ?: return
        if (status != PackageInstaller.STATUS_SUCCESS) {
            _state.value = UpdateState.Failed(
                release,
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Установка отменена" else "Не удалось установить: ${message ?: "ошибка $status"}",
            )
        }
    }

    fun openInstallPermissionScreen() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // --- network ---------------------------------------------------------------------------

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = open(LATEST_RELEASE_URL).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null // no releases yet
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(conn.responseCode)
            ReleaseParser.parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun download(release: ReleaseInfo, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.listFiles()?.forEach { it.delete() }
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(conn.responseCode)
            val total = release.apkSize.takeIf { it > 0 } ?: conn.contentLengthLong
            var done = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != shown) {
                            shown = pct
                            _state.value = UpdateState.Downloading(release, pct / 100f)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!part.renameTo(target)) throw IOException("не удалось сохранить файл")
        if (!verified(target, release)) {
            target.delete()
            throw IOException("файл скачался повреждённым")
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "MCleaner/$currentVersion")
    }

    private fun verified(apk: File, release: ReleaseInfo): Boolean {
        if (release.apkSize > 0 && apk.length() != release.apkSize) return false
        val expected = release.sha256 ?: return true
        val md = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) } == expected
    }

    /** App-specific external dir: readable by the shell user, so `pm install` via Shizuku can open it. */
    private fun apkFile(release: ReleaseInfo): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "update")
        return File(dir, "MCleaner-${release.version}.apk")
    }

    // --- install ---------------------------------------------------------------------------

    private suspend fun install(release: ReleaseInfo, apk: File) {
        _state.value = UpdateState.Installing(release)
        if (privileged.isAvailable) {
            // On success the system replaces this process; we only get here again on failure.
            val r = privileged.exec("pm install -r ${shellQuote(apk.absolutePath)}")
            if (r.ok) return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        withContext(Dispatchers.IO) { installWithSession(apk) }
    }

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
        is HttpStatusException -> if (e.code == 403) "GitHub временно ограничил запросы, попробуйте позже" else "GitHub ответил ошибкой ${e.code}"
        is IOException -> "Нет связи с GitHub: ${e.message ?: "сеть недоступна"}"
        null -> "Неизвестная ошибка"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val AUTO_ATTEMPTS = 5
        const val AUTO_PAUSE_MS = 15_000L
        const val TIMEOUT_MS = 15_000
    }
}
