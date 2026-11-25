package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.core.policy.PackageNames
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote

class FreezeManager(
    private val privileged: Privileged,
    private val currentUser: suspend () -> Int,
    private val addFrozen: suspend (String) -> Unit,
    private val removeFrozen: suspend (String) -> Unit,
) {
    /** [protectedPackages]: launchers and the current keyboard, see [BoostPolicy.canFreeze]. */
    suspend fun freeze(app: AppInfo, selfPackage: String, whitelist: Set<String>, protectedPackages: Set<String>): Boolean {
        if (!BoostPolicy.canFreeze(app, selfPackage, whitelist, protectedPackages)) return false
        if (!PackageNames.isValid(app.packageName)) return false
        val ok = privileged.exec("pm disable-user --user ${currentUser()} ${shellQuote(app.packageName)}").ok
        if (ok) addFrozen(app.packageName)
        return ok
    }

    suspend fun unfreeze(pkg: String): Boolean = unfreeze(pkg, currentUser())

    private suspend fun unfreeze(pkg: String, user: Int): Boolean {
        if (!PackageNames.isValid(pkg)) return false
        val ok = privileged.exec("pm enable --user $user ${shellQuote(pkg)}").ok
        if (ok) removeFrozen(pkg)
        return ok
    }

    /**
     * Enables only those of [pkgs] that are installed and disabled right now; the rest are stale
     * entries (uninstalled, enabled elsewhere, not a package name) and are dropped from the list.
     * If the disabled list can't be read, nothing is touched.
     */
    suspend fun unfreezeAll(pkgs: Set<String>): Int {
        val user = currentUser()
        val r = privileged.exec("pm list packages -d --user $user")
        if (!r.ok) return 0
        val disabled = r.out.lines().map { it.trim() }.filter { it.startsWith("package:") }
            .mapTo(HashSet()) { it.removePrefix("package:").trim() }
        var n = 0
        for (pkg in pkgs) {
            if (PackageNames.isValid(pkg) && pkg in disabled) {
                if (unfreeze(pkg, user)) n++
            } else {
                removeFrozen(pkg)
            }
        }
        return n
    }
}
