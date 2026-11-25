package app.mcleaner

import app.mcleaner.core.i18n.tr
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

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
