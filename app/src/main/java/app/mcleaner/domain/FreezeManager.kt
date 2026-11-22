package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.privileged.Privileged

class FreezeManager(
    private val privileged: Privileged,
    private val currentUser: suspend () -> Int,
    private val addFrozen: suspend (String) -> Unit,
    private val removeFrozen: suspend (String) -> Unit,
) {
    suspend fun freeze(app: AppInfo, selfPackage: String): Boolean {
        if (!BoostPolicy.canFreeze(app, selfPackage)) return false
        val ok = privileged.exec("pm disable-user --user ${currentUser()} ${app.packageName}").ok
        if (ok) addFrozen(app.packageName)
        return ok
    }

    suspend fun unfreeze(pkg: String): Boolean {
        val ok = privileged.exec("pm enable --user ${currentUser()} $pkg").ok
        if (ok) removeFrozen(pkg)
        return ok
    }

    suspend fun unfreezeAll(pkgs: Set<String>): Int = pkgs.count { unfreeze(it) }
}
