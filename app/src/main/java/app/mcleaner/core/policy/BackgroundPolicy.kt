package app.mcleaner.core.policy

import app.mcleaner.core.model.AppInfo

/**
 * «Без фона»: `appops RUN_ANY_IN_BACKGROUND ignore` — the same «background restriction» as in Android's battery
 * settings. The app runs normally while open; in the background its services, jobs and alarms don't start.
 */
object BackgroundPolicy {
    const val OP = "RUN_ANY_IN_BACKGROUND"

    /** [protectedPackages]: launchers, keyboard, accessibility services, notification listeners, live wallpaper. */
    fun canRestrict(app: AppInfo, selfPackage: String, whitelist: Set<String>, protectedPackages: Set<String>): Boolean {
        val pkg = app.packageName
        if (pkg == selfPackage || pkg == BoostPolicy.SHIZUKU_PACKAGE || BoostPolicy.isCarSystem(pkg)) return false
        if (pkg in whitelist || pkg in protectedPackages) return false
        return !app.isSystem || pkg in BoostPolicy.GOOGLE_PACKAGES
    }

    /** `cmd appops get <pkg> RUN_ANY_IN_BACKGROUND`; null when the output is not understood. */
    fun isRestricted(out: String): Boolean? {
        val m = Regex("""$OP: (\w+)""").find(out) ?: return null
        return m.groupValues[1] == "ignore" || m.groupValues[1] == "deny"
    }

    /** From the boost history: apps stopped most often keep waking up — candidates for «Без фона». */
    fun suggestions(topStopped: List<Pair<String, Int>>, already: Set<String>, canRestrict: (String) -> Boolean): List<Pair<String, Int>> =
        topStopped.filter { (pkg, _) -> pkg !in already && canRestrict(pkg) }
}
