package app.mcleaner.core.policy

import app.mcleaner.core.model.AppInfo

data class BoostContext(
    val whitelist: Set<String>,
    val stopGoogle: Boolean,
    val foregroundPackage: String?,
    val launcherPackage: String?,
    val imePackage: String?,
    val selfPackage: String,
    val busyPackages: Set<String>,
)

object BoostPolicy {
    val GOOGLE_PACKAGES = setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")
    val NEVER_TOUCH_PREFIXES = listOf("com.ecarx.", "com.geely.")
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isCarSystem(pkg: String): Boolean = NEVER_TOUCH_PREFIXES.any(pkg::startsWith)

    /** Full mode: running apps that may be force-stopped. */
    fun killCandidates(apps: List<AppInfo>, ctx: BoostContext): List<String> =
        apps.filter { it.isRunning && mayStop(it, ctx, allowGoogle = ctx.stopGoogle) }.map { it.packageName }

    /** Basic mode: running state is unknown, so every eligible app; Google never. */
    fun blindKillCandidates(apps: List<AppInfo>, ctx: BoostContext): List<String> =
        apps.filter { mayStop(it, ctx, allowGoogle = false) }.map { it.packageName }

    fun cacheTargets(apps: List<AppInfo>, whitelist: Set<String>): List<String> =
        apps.map { it.packageName }.filter { it !in whitelist && !isCarSystem(it) }

    /** Manual "Stop" in the app list: user apps and Google; never system or car packages. */
    fun canStopManually(app: AppInfo, selfPackage: String): Boolean {
        val pkg = app.packageName
        if (pkg == selfPackage || pkg == SHIZUKU_PACKAGE || isCarSystem(pkg)) return false
        return !app.isSystem || pkg in GOOGLE_PACKAGES
    }

    fun canFreeze(app: AppInfo, selfPackage: String): Boolean {
        val pkg = app.packageName
        return !app.isSystem && pkg !in GOOGLE_PACKAGES && pkg != selfPackage &&
            pkg != SHIZUKU_PACKAGE && !isCarSystem(pkg)
    }

    private fun mayStop(app: AppInfo, ctx: BoostContext, allowGoogle: Boolean): Boolean {
        val pkg = app.packageName
        val protected = setOfNotNull(ctx.foregroundPackage, ctx.launcherPackage, ctx.imePackage, ctx.selfPackage, SHIZUKU_PACKAGE)
        if (pkg in ctx.whitelist || pkg in protected || pkg in ctx.busyPackages || isCarSystem(pkg)) return false
        if (pkg in GOOGLE_PACKAGES) return allowGoogle
        return !app.isSystem
    }
}
