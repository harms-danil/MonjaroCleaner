package app.mcleaner

import android.app.ActivityManager
import android.content.Context
import app.mcleaner.core.junk.JunkScanner
import app.mcleaner.core.policy.BoostContext
import app.mcleaner.auto.AutoBoostScheduler
import app.mcleaner.auto.WakeWatchService
import app.mcleaner.core.history.BoostRecord
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.policy.LowMemoryGate
import app.mcleaner.data.AppsRepository
import app.mcleaner.data.HistoryStore
import app.mcleaner.domain.BoostReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.mcleaner.data.MCleanerFolder
import app.mcleaner.data.SettingsStore
import app.mcleaner.data.SystemStatsRepository
import app.mcleaner.domain.BoostUseCase
import app.mcleaner.domain.CacheCleaner
import app.mcleaner.domain.FreezeManager
import app.mcleaner.domain.JunkCleaner
import app.mcleaner.privileged.ShizukuPrivileged
import app.mcleaner.update.UpdateManager

/** Manual DI. `shizuku` is handed out as `Privileged`: it reports unavailable whenever Shizuku is not READY. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** First: sets the app language before any text is produced. */
    val ui = app.mcleaner.data.UiPrefs(appContext)
    val settings = SettingsStore(appContext)
    val shizuku = ShizukuPrivileged(appContext)
    val stats = SystemStatsRepository(appContext, shizuku)
    val apps = AppsRepository(appContext, shizuku)
    val cacheCleaner = CacheCleaner(shizuku)
    val junkCleaner = JunkCleaner(shizuku, JunkScanner())
    val folder = MCleanerFolder(appContext, apps::hasAllFilesAccess)
    val updates = UpdateManager(appContext, shizuku, folder)
    val history = HistoryStore(appContext)
    val shizukuInstaller = app.mcleaner.privileged.ShizukuInstaller(appContext)
    val adbAutostart = app.mcleaner.privileged.AdbAutostart(appContext)

    /** Outlives activities: boosts started from the home-screen icon, widget or notification. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val boostLock = Mutex()

    /** Every boost goes through here so that it lands in the history. One at a time. */
    suspend fun runBoost(source: BoostSource): BoostReport = boostLock.withLock {
        val report = boost.run(clearCache = source != BoostSource.SCHEDULE)
        history.add(
            BoostRecord(
                timeMs = System.currentTimeMillis(),
                source = source,
                freedRamKb = report.freedRamKb,
                freedCacheBytes = report.freedCacheBytes,
                stopped = report.stoppedPackages,
            ),
        )
        report
    }

    /** Start/stop the background service and the periodic boost to match the settings. */
    suspend fun syncBackgroundWork() {
        val s = settings.current()
        if (LowMemoryGate.serviceNeeded(s.autoBoost, s.lowMemoryAlert, s.keepRunning)) WakeWatchService.start(appContext) else WakeWatchService.stop(appContext)
        AutoBoostScheduler.schedulePeriodic(appContext, s.scheduleMinutes)
    }

    /**
     * Restores settings from `/sdcard/MCleaner/settings.json` on the first run of an install.
     * Waits (does nothing) while there is no file access, so a reinstall without access cannot wipe the backup.
     * Safe to call repeatedly and concurrently: at start, on every settings change and whenever access may have
     * appeared (Shizuku granted). File I/O runs on Dispatchers.IO; writes are serialized inside [folder].
     */
    suspend fun syncSettingsBackup() {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (!settings.isInitialized()) {
                val read = folder.readBackup() ?: return@withContext
                settings.initialize(read.getOrNull())
            }
            folder.writeBackup { settings.current().toBackup() }
        }
    }

    val freezeManager = FreezeManager(
        privileged = shizuku,
        currentUser = apps::currentUser,
        addFrozen = { settings.addFrozen(it) },
        removeFrozen = { settings.removeFrozen(it) },
    )

    val boost = BoostUseCase(
        privileged = shizuku,
        loadApps = apps::loadApps,
        buildContext = ::boostContext,
        readFreeRamKb = stats::freeRamKb,
        totalCacheBytes = apps::totalCacheBytes,
        killBackground = { pkg ->
            appContext.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
        },
        cacheCleaner = cacheCleaner,
        currentUser = apps::currentUser,
        externalRoot = android.os.Environment.getExternalStorageDirectory().path,
    )

    suspend fun boostContext(): BoostContext {
        val s = settings.current()
        val busy = apps.busyPackages()
        return BoostContext(
            whitelist = s.whitelist,
            stopGoogle = s.stopGoogle,
            foregroundPackage = null,
            launcherPackage = apps.launcherPackage(),
            imePackage = apps.imePackage(),
            selfPackage = appContext.packageName,
            busyPackages = busy.packages + apps.carUsabilityPackages(),
            busyKnown = busy.known,
        )
    }
}
