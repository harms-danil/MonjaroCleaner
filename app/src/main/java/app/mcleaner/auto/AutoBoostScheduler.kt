package app.mcleaner.auto

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.mcleaner.core.history.BoostSource
import java.util.concurrent.TimeUnit

object AutoBoostScheduler {
    private const val WORK_NAME = "auto-boost"
    private const val PERIODIC_NAME = "auto-boost-periodic"
    const val KEY_SOURCE = "source"

    /** Boot and wake both land here; REPLACE keeps only the latest trigger. Cooldown is checked when the work runs. */
    fun schedule(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<AutoBoostWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_SOURCE to BoostSource.AUTO.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * «Ускорять по расписанию»: every [minutes] (15 is WorkManager's minimum) while the head unit is awake;
     * 0 cancels. WorkManager keeps the schedule across reboots.
     */
    fun schedulePeriodic(context: Context, minutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (minutes <= 0) {
            wm.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBoostWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_SOURCE to BoostSource.SCHEDULE.name))
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
