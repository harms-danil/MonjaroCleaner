package app.mcleaner.core.policy

object AutoBoostGate {
    const val COOLDOWN_MS = 600_000L
    const val BOOT_DELAY_MS = 90_000L
    const val WAKE_DELAY_MS = 30_000L

    fun shouldRun(nowMs: Long, lastRunMs: Long?, enabled: Boolean, privilegedReady: Boolean): Boolean {
        if (!enabled || !privilegedReady) return false
        if (lastRunMs == null || lastRunMs > nowMs) return true
        return nowMs - lastRunMs >= COOLDOWN_MS
    }
}
