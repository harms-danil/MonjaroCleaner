package app.mcleaner

import android.app.ActivityManager
import android.content.Context
import app.mcleaner.core.junk.JunkScanner
import app.mcleaner.core.policy.BoostContext
import app.mcleaner.data.AppsRepository
import app.mcleaner.data.SettingsStore
import app.mcleaner.data.SystemStatsRepository
import app.mcleaner.domain.BoostUseCase
import app.mcleaner.domain.CacheCleaner
import app.mcleaner.domain.FreezeManager
import app.mcleaner.domain.JunkCleaner
import app.mcleaner.privileged.ShizukuPrivileged

/** Manual DI. `shizuku` is handed out as `Privileged`: it reports unavailable whenever Shizuku is not READY. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val shizuku = ShizukuPrivileged(appContext)
    val stats = SystemStatsRepository(appContext, shizuku)
    val apps = AppsRepository(appContext, shizuku)
    val cacheCleaner = CacheCleaner(shizuku)
    val junkCleaner = JunkCleaner(shizuku, JunkScanner())

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
            busyPackages = busy.packages,
            busyKnown = busy.known,
        )
    }
}
