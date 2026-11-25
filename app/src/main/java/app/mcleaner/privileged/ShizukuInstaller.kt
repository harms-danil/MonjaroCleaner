package app.mcleaner.privileged

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.mcleaner.MCleanerApp
import app.mcleaner.core.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Installs the Shizuku APK bundled in MCleaner's assets (Shizuku is Apache-2.0, see assets/licenses).
 * Without Shizuku running, Android only allows this through the system installer dialog.
 * Starting Shizuku still needs ADB (laptop, phone or network ADB) — that cannot be done by installing.
 */
class ShizukuInstaller(private val context: Context) {
    /** null = idle; otherwise a message for the user. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /** Returns false when MCleaner first needs the «install unknown apps» permission (screen opened). */
    suspend fun install(): Boolean {
        // One install at a time: a second session for the same package fails in the system installer
        // with «App not installed» even though the first one succeeds.
        if (!_busy.compareAndSet(expect = false, update = true)) return false
        if (!context.packageManager.canRequestPackageInstalls()) {
            _busy.value = false
            _message.value = tr(
                "Разрешите MCleaner устанавливать приложения, вернитесь и нажмите «Установить Shizuku» ещё раз.",
                "Allow MCleaner to install apps, come back and tap Install Shizuku again.",
            )
            val pkg = Uri.parse("package:${context.packageName}")
            val opened = runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!opened) {
                // Some head unit firmwares strip this settings screen: fall back to MCleaner's app info.
                _message.value = tr(
                    "Экран «Установка неизвестных приложений» недоступен. В сведениях о MCleaner разрешите установку приложений, вернитесь и нажмите «Установить Shizuku» ещё раз.",
                    "The Install unknown apps screen is unavailable. In MCleaner's app info allow installing apps, come back and tap Install Shizuku again.",
                )
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            return false
        }
        return try {
            withContext(Dispatchers.IO) { commitSession() }
            true
        } catch (e: Exception) {
            _busy.value = false
            _message.value = tr("Не удалось установить Shizuku: ${e.message}", "Could not install Shizuku: ${e.message}")
            false
        }
    }

    private fun commitSession() {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(SHIZUKU_PACKAGE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            context.assets.open(ASSET).use { input ->
                session.openWrite("base.apk", 0, -1).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val intent = Intent(context, ShizukuInstallReceiver::class.java).setPackage(context.packageName)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    internal fun onResult(status: Int, message: String?) {
        _busy.value = false
        val installed = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
        _message.value = when {
            status == PackageInstaller.STATUS_SUCCESS || (installed && status != PackageInstaller.STATUS_FAILURE_ABORTED) -> tr(
                "Shizuku установлен. Теперь его нужно запустить — шаг 3 или 4 инструкции.",
                "Shizuku installed. Now it needs to be started — step 3 or 4 of the guide.",
            )
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> tr("Установка Shizuku отменена", "Shizuku installation cancelled")
            else -> tr("Не удалось установить Shizuku: ${message ?: status}", "Could not install Shizuku: ${message ?: status}")
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val BUNDLED_VERSION = "13.6.0"
        private const val ASSET = "shizuku.apk"
    }
}

/** System installer callbacks for [ShizukuInstaller]. */
class ShizukuInstallReceiver : BroadcastReceiver() {
    private companion object {
        /** Sessions whose confirmation screen was already shown (per process). */
        val confirmed: MutableSet<Int> = java.util.Collections.synchronizedSet(mutableSetOf())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Some firmwares deliver this twice: a second confirmation screen would end in «App not installed».
            val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            if (!confirmed.add(session)) return
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }
        val app = context.applicationContext as? MCleanerApp ?: return
        app.container.shizukuInstaller.onResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        app.container.shizuku.refresh()
    }
}
