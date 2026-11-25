package app.mcleaner.domain

import app.mcleaner.core.log.L
import app.mcleaner.core.policy.BackgroundPolicy
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote

/** Applies «Без фона» through appops and reads it back, so the screen shows the real state. */
class BackgroundLimiter(private val privileged: Privileged) {

    /** True when the system now reports the wanted state. */
    suspend fun set(pkg: String, restricted: Boolean): Boolean {
        if (!privileged.isAvailable) return false
        val mode = if (restricted) "ignore" else "default"
        privileged.exec("cmd appops set ${shellQuote(pkg)} ${BackgroundPolicy.OP} $mode")
        val now = state(pkg)
        L.i("NoBackground", "$pkg -> $mode, system reports restricted=$now")
        return now == restricted
    }

    suspend fun state(pkg: String): Boolean? =
        BackgroundPolicy.isRestricted(privileged.exec("cmd appops get ${shellQuote(pkg)} ${BackgroundPolicy.OP}").out)

    /** After a reinstall of the app or a restored backup: put back restrictions the system no longer has. */
    suspend fun reapply(pkgs: Set<String>) {
        if (!privileged.isAvailable) return
        for (pkg in pkgs) if (state(pkg) == false) set(pkg, true)
    }
}
