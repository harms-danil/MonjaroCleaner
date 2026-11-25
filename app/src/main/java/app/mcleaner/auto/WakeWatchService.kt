package app.mcleaner.auto

import app.mcleaner.core.i18n.tr
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.mcleaner.MCleanerApp
import app.mcleaner.R
import app.mcleaner.core.policy.AutoBoostGate

/** Keeps a dynamic receiver for screen-on / user-present: those broadcasts can't be declared in the manifest. */
class WakeWatchService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AutoBoostScheduler.schedule(context, AutoBoostGate.WAKE_DELAY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notification = NotificationCompat.Builder(this, MCleanerApp.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("MCleaner следит за пробуждением", "MCleaner is watching for wake-up"))
            .setContentText(tr("Автоускорение включено", "Auto-boost is on"))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WakeWatchService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWatchService::class.java))
        }
    }
}
