package app.mcleaner

import app.mcleaner.core.i18n.tr
import app.mcleaner.core.log.L
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MCleanerApp : Application() {
    lateinit var container: AppContainer
        private set

    lateinit var logSink: app.mcleaner.data.FileLogSink
        private set

    override fun onCreate() {
        super.onCreate()
        // Journal first, so that everything after it (and any crash) is recorded.
        logSink = app.mcleaner.data.FileLogSink(java.io.File(filesDir, "logs"))
        L.uptime = { android.os.SystemClock.elapsedRealtime() }
        L.sink = logSink
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            L.crash(t, e)
            previous?.uncaughtException(t, e)
        }
        L.i(
            "App",
            "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "Android ${android.os.Build.VERSION.RELEASE}, uptime ${android.os.SystemClock.elapsedRealtime() / 1000} s",
        )
        container = AppContainer(this)
        container.startJournalWatchers()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, tr("Результат автоускорения", "Auto-boost result"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WATCH, tr("Слежение за пробуждением", "Wake watching"), NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, tr("Мало памяти", "Low memory"), NotificationManager.IMPORTANCE_LOW)) // no sound while driving

        // Settings backup in /sdcard/MCleaner: restore on first run, then rewrite on every change.
        val c = container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { c.syncSettingsBackup() }
            c.settings.settings.map { it.toBackup() }.distinctUntilChanged().drop(1).collect {
                // Not the collected value: syncSettingsBackup snapshots the settings under the write lock,
                // so overlapping writers cannot put an older state over a newer one.
                runCatching { c.syncSettingsBackup() }
            }
        }
    }

    companion object {
        const val CHANNEL_RESULT = "auto_boost_result"
        const val CHANNEL_WATCH = "wake_watch"
        const val CHANNEL_ALERT = "low_memory"
    }
}
