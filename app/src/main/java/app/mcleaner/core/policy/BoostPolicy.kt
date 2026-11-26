package app.mcleaner.core.policy

import app.mcleaner.core.model.AppInfo

data class BoostContext(
    val whitelist: Set<String>,
    val stopGoogle: Boolean,
    val foregroundPackage: String?,
    val launcherPackage: String?,
    val imePackage: String?,
    val selfPackage: String,
    /** Never stopped, cache kept: foreground services, media sessions, resumed apps, accessibility, notification listeners, live wallpaper, launchers. */
    val busyPackages: Set<String>,
    /** False when the busy probe failed: then nothing may be force-stopped (fail closed). */
    val busyKnown: Boolean = true,
    /** «Мягкое ускорение»: ask apps that keep running to drop their in-memory caches. */
    val gentleTrim: Boolean = false,
)

/** Why «Ускорить» leaves a running app alone (shown in the boost preview). */
enum class SkipReason { WHITELIST, IN_USE, SYSTEM, GOOGLE, SELF }

object BoostPolicy {
    val GOOGLE_PACKAGES = setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending")
    val NEVER_TOUCH_PREFIXES = listOf("com.ecarx.", "com.geely.")
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun isCarSystem(pkg: String): Boolean = NEVER_TOUCH_PREFIXES.any(pkg::startsWith)

    /** Full mode: running apps that may be force-stopped. */
    fun killCandidates(apps: List<AppInfo>, ctx: BoostContext): List<String> =
        if (!ctx.busyKnown) emptyList() else apps.filter { it.isRunning && mayStop(it, ctx, allowGoogle = ctx.stopGoogle) }.map { it.packageName }

    /** Basic mode: running state is unknown, so every eligible app; Google never. */
    fun blindKillCandidates(apps: List<AppInfo>, ctx: BoostContext): List<String> =
        apps.filter { mayStop(it, ctx, allowGoogle = false) }.map { it.packageName }

    /** [busy]: apps in use right now (players, accessibility, launchers…): their cache stays. */
    fun cacheTargets(apps: List<AppInfo>, whitelist: Set<String>, busy: Set<String> = emptySet()): List<String> =
        apps.map { it.packageName }.filter { it !in whitelist && it !in busy && !isCarSystem(it) }

    /** «Закрыть» in the app list and the app sheet: running now, and allowed to be stopped by hand. */
    fun canClose(app: AppInfo, selfPackage: String): Boolean = app.isRunning && canStopManually(app, selfPackage)

    /** Manual "Stop" in the app list: user apps and Google; never system or car packages. */
    fun canStopManually(app: AppInfo, selfPackage: String): Boolean {
        val pkg = app.packageName
        if (pkg == selfPackage || pkg == SHIZUKU_PACKAGE || isCarSystem(pkg)) return false
        return !app.isSystem || pkg in GOOGLE_PACKAGES
    }

    /** [protectedPackages]: every launcher (HOME handler) and the current keyboard: freezing them strands the user. */
    fun canFreeze(app: AppInfo, selfPackage: String, whitelist: Set<String>, protectedPackages: Set<String>): Boolean {
        val pkg = app.packageName
        return !app.isSystem && pkg !in GOOGLE_PACKAGES && pkg != selfPackage &&
            pkg != SHIZUKU_PACKAGE && !isCarSystem(pkg) && pkg !in whitelist && pkg !in protectedPackages
    }

    /**
     * Running apps that stay (whitelist, busy, launcher, keyboard) and the `am send-trim-memory` level for each:
     * RUNNING_LOW for what is in use right now (keeps working, drops caches), MODERATE for background ones.
     * System apps, Google and MCleaner/Shizuku are not signalled.
     */
    fun trimTargets(apps: List<AppInfo>, ctx: BoostContext): List<Pair<String, String>> {
        if (!ctx.gentleTrim) return emptyList()
        // System processes (system_server «android», Settings) reject or ignore the signal; the launcher and keyboard take it.
        return apps.filter { it.isRunning && (!it.isSystem || it.packageName == ctx.launcherPackage || it.packageName == ctx.imePackage) }.mapNotNull { a ->
            when (skipReason(a, ctx)) {
                SkipReason.IN_USE -> a.packageName to "RUNNING_LOW"
                SkipReason.WHITELIST -> a.packageName to if (a.packageName in ctx.busyPackages) "RUNNING_LOW" else "MODERATE"
                else -> null
            }
        }
    }

    /** Null when full-mode «Ускорить» would stop [app]; otherwise why it stays. */
    fun skipReason(app: AppInfo, ctx: BoostContext): SkipReason? = skipReason(app, ctx, allowGoogle = ctx.stopGoogle)

    private fun skipReason(app: AppInfo, ctx: BoostContext, allowGoogle: Boolean): SkipReason? {
        val pkg = app.packageName
        return when {
            pkg == ctx.selfPackage || pkg == SHIZUKU_PACKAGE -> SkipReason.SELF
            pkg in ctx.whitelist -> SkipReason.WHITELIST
            pkg == ctx.foregroundPackage || pkg == ctx.launcherPackage || pkg == ctx.imePackage || pkg in ctx.busyPackages -> SkipReason.IN_USE
            isCarSystem(pkg) -> SkipReason.SYSTEM
            pkg in GOOGLE_PACKAGES -> if (allowGoogle) null else SkipReason.GOOGLE
            app.isSystem -> SkipReason.SYSTEM
            else -> null
        }
    }

    private fun mayStop(app: AppInfo, ctx: BoostContext, allowGoogle: Boolean): Boolean = skipReason(app, ctx, allowGoogle) == null
}
