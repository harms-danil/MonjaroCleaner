package app.mcleaner.auto

import app.mcleaner.core.log.L
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mcleaner.MCleanerApp
import app.mcleaner.MainActivity
import app.mcleaner.R
import app.mcleaner.core.adb.AdbStartResult
import app.mcleaner.core.i18n.tr
import app.mcleaner.privileged.AdbAutostart
import app.mcleaner.privileged.ShizukuState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * «Запускать Shizuku автоматически»: after boot, on app start and on screen-on, if Shizuku is not running,
 * start it through the head unit's own network ADB. adbd may come up late after boot, hence the retries.
 */
class ShizukuAutostartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as MCleanerApp).container
        if (!c.settings.current().shizukuAutostart) return Result.success()

        c.shizuku.refresh()
        val state = withTimeoutOrNull(2_000) { c.shizuku.state.first { it != ShizukuState.NOT_RUNNING } } ?: c.shizuku.state.value
        L.i("Autostart", "worker run: Shizuku $state, attempt $runAttemptCount")
        if (state != ShizukuState.NOT_RUNNING) return Result.success() // running, or not installed: nothing to do

        val r = c.adbAutostart.startShizuku(AdbAutostart.BACKGROUND_AUTH_WAIT_MS)
        L.i("Autostart", "network ADB start: $r")
        return when (r) {
            AdbStartResult.Started -> {
                delay(2_000)
                c.shizuku.refresh()
                cancelProblemNotification()
                Result.success()
            }
            AdbStartResult.NoNetworkAdb, is AdbStartResult.Failed ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else giveUp(r)
            AdbStartResult.NotAuthorized -> giveUp(r) // nobody to tap «Разрешить» in the background
        }
    }

    private fun giveUp(r: AdbStartResult): Result {
        L.w("Autostart", "gave up after ${runAttemptCount + 1} attempt(s): $r")
        val text = when (r) {
            AdbStartResult.NoNetworkAdb -> tr(
                "ADB по сети недоступен после перезагрузки. Запустите Shizuku вручную или включите ADB по сети.",
                "Network ADB is not available after reboot. Start Shizuku manually or enable network ADB.",
            )
            AdbStartResult.NotAuthorized -> tr(
                "Откройте MCleaner → «Настройки» → «Запустить Shizuku сейчас» и подтвердите «Разрешить отладку».",
                "Open MCleaner → Settings → Start Shizuku now and confirm «Allow USB debugging».",
            )
            is AdbStartResult.Failed -> tr("Не удалось запустить Shizuku: ${r.details}", "Could not start Shizuku: ${r.details}")
            AdbStartResult.Started -> return Result.success()
        }
        val open = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(applicationContext, MCleanerApp.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Shizuku не запущен", "Shizuku is not running"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { applicationContext.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n) }
        return Result.success()
    }

    private fun cancelProblemNotification() {
        applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val WORK_NAME = "shizuku-autostart"
        private const val MAX_RETRIES = 6 // ~30 s apart, linear: about 10 minutes after boot
        private const val NOTIFICATION_ID = 4

        /** KEEP: boot, app start and screen-on may all fire at once; one attempt chain is enough. */
        fun enqueue(context: Context, delayMs: Long = 0) {
            val request = OneTimeWorkRequestBuilder<ShizukuAutostartWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
