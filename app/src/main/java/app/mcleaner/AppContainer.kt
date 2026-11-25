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
import kotlinx.coroutines.launch
import app.mcleaner.core.log.L
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

    val maintenance = app.mcleaner.domain.Maintenance(appContext, shizuku)
    val backgroundLimiter = app.mcleaner.domain.BackgroundLimiter(shizuku)

    /** Whitelist and the launcher fully (navigator, music, home screen), other user apps by profile. */
    suspend fun compilePlan(
        statuses: Map<String, String>,
        installed: List<app.mcleaner.core.policy.InstalledApp>,
    ): List<app.mcleaner.core.maintenance.CompileStep> {
        val important = settings.current().whitelist + setOfNotNull(apps.launcherPackage())
        val userApps = installed.filter { !it.isSystem }.map { it.packageName }.toSet() - appContext.packageName
        return app.mcleaner.core.maintenance.Dexopt.plan(statuses, userApps, important)
    }

    val logExporter by lazy { app.mcleaner.domain.LogExporter(this, (appContext as MCleanerApp).logSink) }

    /** Journal: state changes of Shizuku, updates and settings, as they happen. */
    fun startJournalWatchers() {
        appScope.launch { shizuku.state.collect { L.i("Shizuku", "state $it") } }
        appScope.launch { updates.state.collect { L.i("Update", "state $it") } }
        appScope.launch {
            var before: app.mcleaner.data.Settings? = null
            settings.settings.collect { s ->
                val b = before
                if (b == null) L.i("Settings", "loaded $s") else if (b != s) L.i("Settings", "changed ${settingsDiff(b, s)}")
                before = s
            }
        }
    }

    private fun settingsDiff(a: app.mcleaner.data.Settings, b: app.mcleaner.data.Settings): String {
        fun <T> field(name: String, f: (app.mcleaner.data.Settings) -> T): String? {
            val x = f(a)
            val y = f(b)
            return when {
                x == y -> null
                x is Set<*> && y is Set<*> -> "$name +${y - x} -${x - y}"
                else -> "$name $x -> $y"
            }
        }
        return listOfNotNull(
            field("whitelist") { it.whitelist }, field("frozen") { it.frozen }, field("noBackground") { it.noBackground },
            field("autoBoost") { it.autoBoost }, field("stopGoogle") { it.stopGoogle }, field("scheduleMinutes") { it.scheduleMinutes },
            field("lowMemoryAlert") { it.lowMemoryAlert }, field("lowMemoryPercent") { it.lowMemoryPercent },
            field("shizukuAutostart") { it.shizukuAutostart }, field("keepRunning") { it.keepRunning }, field("gentleTrim") { it.gentleTrim },
            field("weeklyMaintenance") { it.weeklyMaintenance }, field("lastMaintenanceMs") { it.lastMaintenanceMs },
            field("lastAutoBoostMs") { it.lastAutoBoostMs },
        ).joinToString("; ")
    }

    /** Outlives activities: boosts started from the home-screen icon, widget or notification. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val boostLock = Mutex()

    /** Every boost goes through here so that it lands in the history. One at a time. */
    suspend fun runBoost(source: BoostSource): BoostReport = boostLock.withLock {
        L.i("Boost", "start, source $source, Shizuku ${shizuku.state.value}")
        val t0 = android.os.SystemClock.elapsedRealtime()
        val report = try {
            boost.run(clearCache = source != BoostSource.SCHEDULE)
        } catch (e: Exception) {
            L.e("Boost", "failed", e)
            throw e
        }
        L.i(
            "Boost",
            "done in ${android.os.SystemClock.elapsedRealtime() - t0} ms: stopped ${report.stoppedPackages}, failed ${report.failed}, " +
                "freed RAM ${report.freedRamKb} KB, cache ${report.freedCacheBytes} B, full ${report.fullMode}, protection unknown ${report.protectionUnavailable}",
        )
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
        app.mcleaner.auto.MaintenanceWorker.schedule(appContext, s.weeklyMaintenance)
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
            gentleTrim = s.gentleTrim,
        )
    }
}
