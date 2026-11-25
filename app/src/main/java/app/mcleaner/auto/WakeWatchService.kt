package app.mcleaner.auto

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.mcleaner.MCleanerApp
import app.mcleaner.R
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.parse.MemInfoParser
import app.mcleaner.core.policy.AutoBoostGate
import app.mcleaner.core.policy.LowMemoryGate
import app.mcleaner.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Background watcher (foreground service), runs while auto-boost or the low-memory alert is on:
 * - a dynamic receiver for screen-on / user-present (can't be declared in the manifest) → auto-boost;
 * - every 30 s a look at /proc/meminfo → «Мало памяти» notification with a «Ускорить» button
 *   once memory has stayed low for a minute (see [LowMemoryGate.shouldAlert]).
 */
class WakeWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var memoryJob: Job? = null
    private var lastAlertMs: Long? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AutoBoostScheduler.schedule(context, AutoBoostGate.WAKE_DELAY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, ongoing(null), type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val c = (application as MCleanerApp).container
        scope.launch {
            c.settings.settings.distinctUntilChanged().collect { s ->
                if (!LowMemoryGate.serviceNeeded(s.autoBoost, s.lowMemoryAlert)) {
                    stopSelf()
                    return@collect
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, ongoing(s))
                memoryJob?.cancel()
                memoryJob = if (s.lowMemoryAlert) scope.launch { watchMemory(s.lowMemoryPercent) } else null
            }
        }
    }

    private suspend fun watchMemory(thresholdPercent: Int) {
        var streak: LowMemoryGate.LowStreak? = null
        while (scope.isActive) {
            val mem = runCatching { MemInfoParser.parse(File("/proc/meminfo").readText()) }.getOrNull()
            if (mem != null) {
                val free = LowMemoryGate.freePercent(mem.totalKb, mem.availableKb)
                // Monotonic: the head unit wall clock is often wrong or jumps after GPS/network sync.
                val now = SystemClock.elapsedRealtime()
                streak = LowMemoryGate.track(streak, free, thresholdPercent, now)
                if (LowMemoryGate.shouldAlert(streak, lastAlertMs, now)) {
                    lastAlertMs = now
                    alert(free)
                }
            }
            delay(LowMemoryGate.CHECK_INTERVAL_MS)
        }
    }

    private fun alert(freePercent: Int) {
        val boost = PendingIntent.getActivity(
            this, 1, BoostShortcutActivity.intent(this, BoostSource.LOW_MEMORY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, MCleanerApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Мало свободной памяти", "Low on memory"))
            .setContentText(tr("Свободно $freePercent%. Нажмите «Ускорить», чтобы освободить память.", "$freePercent% free. Tap Boost to free memory."))
            .setContentIntent(boost)
            .addAction(R.drawable.ic_notification, tr("Ускорить", "Boost"), boost)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(LOW_MEMORY_NOTIFICATION_ID, n) }
    }

    private fun ongoing(s: Settings?) = NotificationCompat.Builder(this, MCleanerApp.CHANNEL_WATCH)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(tr("MCleaner работает в фоне", "MCleaner is running in the background"))
        .setContentText(
            listOfNotNull(
                if (s == null || s.autoBoost) tr("автоускорение", "auto-boost") else null,
                if (s?.lowMemoryAlert == true) tr("слежение за памятью", "memory watch") else null,
            ).joinToString(", ").replaceFirstChar(Char::uppercase),
        )
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        const val LOW_MEMORY_NOTIFICATION_ID = 3

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WakeWatchService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWatchService::class.java))
        }
    }
}
