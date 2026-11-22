package app.mcleaner.auto

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutoBoostScheduler {
    private const val WORK_NAME = "auto-boost"

    /** Boot and wake both land here; REPLACE keeps only the latest trigger. Cooldown is checked when the work runs. */
    fun schedule(context: Context, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<AutoBoostWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
