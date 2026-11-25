package app.mcleaner.auto

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mcleaner.MCleanerApp
import app.mcleaner.core.log.L
import app.mcleaner.core.maintenance.MaintenanceGate
import app.mcleaner.privileged.ShizukuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Weekly «Обслуживание»: checked a few times a day, runs when due (see [MaintenanceGate]). */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MCleanerApp).container
        val s = c.settings.current()
        c.shizuku.refresh()
        val ready = withTimeoutOrNull(10_000) { c.shizuku.state.first { it == ShizukuState.READY } } != null
        val due = MaintenanceGate.due(s.weeklyMaintenance, ready, s.lastMaintenanceMs, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        L.i("Maintenance", "weekly check: due $due (enabled ${s.weeklyMaintenance}, Shizuku ready $ready, last ${s.lastMaintenanceMs})")
        if (!due) return Result.success()
        val plan = c.compilePlan(c.maintenance.dexoptStatus(), c.apps.installedApps())
        c.maintenance.compile(plan)
        c.maintenance.trimStorage()
        c.settings.setLastMaintenance(System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-maintenance"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(MaintenanceGate.MIN_UPTIME_MS, TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
