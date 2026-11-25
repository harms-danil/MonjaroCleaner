package app.mcleaner.auto

import app.mcleaner.core.i18n.tr
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.mcleaner.MCleanerApp
import app.mcleaner.R
import app.mcleaner.core.policy.AutoBoostGate
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class AutoBoostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as MCleanerApp).container
        val settings = c.settings.current()
        if (!settings.autoBoost) return Result.success()

        c.shizuku.refresh()
        val ready = withTimeoutOrNull(READY_TIMEOUT_MS) {
            c.shizuku.state.first { it == ShizukuState.READY }
        } != null

        val now = System.currentTimeMillis()
        if (!AutoBoostGate.shouldRun(now, settings.lastAutoBoostMs, settings.autoBoost, ready)) return Result.success()

        c.settings.setLastAutoBoost(now)
        val report = c.boost.run()
        notify(tr("Освобождено ${Formatters.kb(report.freedRamKb)} ОЗУ, остановлено приложений: ${report.stopped}", "Freed ${Formatters.kb(report.freedRamKb)} of RAM, apps stopped: ${report.stopped}"))
        return Result.success()
    }

    private fun notify(text: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(applicationContext, MCleanerApp.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("MCleaner: автоускорение", "MCleaner: auto-boost"))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private companion object {
        const val READY_TIMEOUT_MS = 10_000L
        const val NOTIFICATION_ID = 2
    }
}
