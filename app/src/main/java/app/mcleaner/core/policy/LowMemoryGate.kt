package app.mcleaner.core.policy

/** «Предупреждать о нехватке памяти»: when to notify, and whether the background service must run. */
object LowMemoryGate {
    const val COOLDOWN_MS = 10 * 60_000L
    const val CHECK_INTERVAL_MS = 30_000L
    const val DEFAULT_PERCENT = 15
    val PERCENT_CHOICES = listOf(10, 15, 20, 25)

    /** A short dip (an app starting) is not worth a notification while driving. */
    const val MIN_LOW_CHECKS = 2
    const val SUSTAINED_MS = 60_000L

    /** Memory has been below the threshold since [sinceMs], for [checks] readings in a row. */
    data class LowStreak(val sinceMs: Long, val checks: Int)

    fun freePercent(totalKb: Long, availableKb: Long): Int =
        if (totalKb <= 0) 0 else (availableKb * 100 / totalKb).toInt()

    /** The streak after one more reading; null when memory is fine again. Use a monotonic clock for [nowMs]. */
    fun track(previous: LowStreak?, freePercent: Int, thresholdPercent: Int, nowMs: Long): LowStreak? = when {
        freePercent >= thresholdPercent -> null
        previous == null || previous.sinceMs > nowMs -> LowStreak(sinceMs = nowMs, checks = 1)
        else -> previous.copy(checks = previous.checks + 1)
    }

    fun shouldAlert(streak: LowStreak?, lastAlertMs: Long?, nowMs: Long): Boolean {
        if (streak == null || streak.checks < MIN_LOW_CHECKS || nowMs - streak.sinceMs < SUSTAINED_MS) return false
        return lastAlertMs == null || lastAlertMs > nowMs || nowMs - lastAlertMs >= COOLDOWN_MS
    }

    /** The foreground service keeps MCleaner running and watches wake-ups (auto-boost, Shizuku) and memory (alert). */
    fun serviceNeeded(autoBoost: Boolean, lowMemoryAlert: Boolean, keepRunning: Boolean) = keepRunning || autoBoost || lowMemoryAlert
}
