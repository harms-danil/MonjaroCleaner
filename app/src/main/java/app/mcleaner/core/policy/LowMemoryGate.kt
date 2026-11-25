package app.mcleaner.core.policy

/** «Предупреждать о нехватке памяти»: when to notify, and whether the background service must run. */
object LowMemoryGate {
    const val COOLDOWN_MS = 10 * 60_000L
    const val CHECK_INTERVAL_MS = 30_000L
    const val DEFAULT_PERCENT = 15
    val PERCENT_CHOICES = listOf(10, 15, 20, 25)

    fun freePercent(totalKb: Long, availableKb: Long): Int =
        if (totalKb <= 0) 0 else (availableKb * 100 / totalKb).toInt()

    fun shouldAlert(freePercent: Int, thresholdPercent: Int, lastAlertMs: Long?, nowMs: Long): Boolean {
        if (freePercent >= thresholdPercent) return false
        return lastAlertMs == null || lastAlertMs > nowMs || nowMs - lastAlertMs >= COOLDOWN_MS
    }

    /** The foreground service watches wake-ups (auto-boost) and memory (alert). */
    fun serviceNeeded(autoBoost: Boolean, lowMemoryAlert: Boolean) = autoBoost || lowMemoryAlert
}
