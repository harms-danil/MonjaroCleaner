package app.mcleaner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class MCleanerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Результат автоускорения", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WATCH, "Слежение за пробуждением", NotificationManager.IMPORTANCE_MIN))
    }

    companion object {
        const val CHANNEL_RESULT = "auto_boost_result"
        const val CHANNEL_WATCH = "wake_watch"
    }
}
