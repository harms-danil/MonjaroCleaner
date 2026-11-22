package app.mcleaner

import android.content.Context
import app.mcleaner.data.AppsRepository
import app.mcleaner.data.SettingsStore
import app.mcleaner.data.SystemStatsRepository
import app.mcleaner.privileged.ShizukuPrivileged

/** Manual DI. `shizuku` is handed out as `Privileged`: it reports unavailable whenever Shizuku is not READY. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val shizuku = ShizukuPrivileged(appContext)
    val stats = SystemStatsRepository(appContext, shizuku)
    val apps = AppsRepository(appContext, shizuku)
}
