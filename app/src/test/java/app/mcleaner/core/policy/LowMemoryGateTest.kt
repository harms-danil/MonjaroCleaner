package app.mcleaner.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LowMemoryGateTest {
    private val now = 1_000_000_000L
    private val step = LowMemoryGate.CHECK_INTERVAL_MS

    /** Feeds readings every [step] ms and returns the streak after each one. */
    private fun feed(vararg free: Int, threshold: Int = 15): List<LowMemoryGate.LowStreak?> {
        var s: LowMemoryGate.LowStreak? = null
        return free.mapIndexed { i, f -> LowMemoryGate.track(s, f, threshold, now + i * step).also { s = it } }
    }

    @Test
    fun `a single low reading is not an alert`() {
        val s = feed(10).last()
        assertFalse(LowMemoryGate.shouldAlert(s, lastAlertMs = null, nowMs = now))
    }

    @Test
    fun `alerts only after memory stays low for a minute over several checks`() {
        val streaks = feed(10, 10, 10)
        assertFalse(LowMemoryGate.shouldAlert(streaks[1], null, now + step)) // 2 checks, 30 s
        assertTrue(LowMemoryGate.shouldAlert(streaks[2], null, now + 2 * step)) // 3 checks, 60 s
    }

    @Test
    fun `a good reading resets the streak`() {
        val streaks = feed(10, 10, 20, 10)
        assertNull(streaks[2])
        assertFalse(LowMemoryGate.shouldAlert(streaks[3], null, now + 3 * step))
        assertEquals(1, streaks[3]!!.checks)
    }

    @Test
    fun `two checks a minute apart are enough`() {
        val first = LowMemoryGate.track(null, 10, 15, now)
        val second = LowMemoryGate.track(first, 10, 15, now + 60_000)
        assertTrue(LowMemoryGate.shouldAlert(second, null, now + 60_000))
    }

    @Test
    fun `one reading long ago is still only one check`() {
        val first = LowMemoryGate.track(null, 10, 15, now)
        assertFalse(LowMemoryGate.shouldAlert(first, null, now + 10 * 60_000))
    }

    @Test
    fun `at most once per cooldown`() {
        val s = LowMemoryGate.LowStreak(sinceMs = now - 120_000, checks = 5)
        assertTrue(LowMemoryGate.shouldAlert(s, lastAlertMs = null, nowMs = now))
        assertFalse(LowMemoryGate.shouldAlert(s, lastAlertMs = now - 5 * 60_000, nowMs = now))
        assertTrue(LowMemoryGate.shouldAlert(s, lastAlertMs = now - LowMemoryGate.COOLDOWN_MS, nowMs = now))
        assertFalse(LowMemoryGate.shouldAlert(null, lastAlertMs = null, nowMs = now))
    }

    @Test
    fun `free percent from meminfo`() {
        assertEquals(25, LowMemoryGate.freePercent(totalKb = 2_000_000, availableKb = 500_000))
        assertEquals(0, LowMemoryGate.freePercent(totalKb = 0, availableKb = 10))
    }

    @Test
    fun `service is needed for wake watching or the memory alert`() {
        assertTrue(LowMemoryGate.serviceNeeded(autoBoost = true, lowMemoryAlert = false))
        assertTrue(LowMemoryGate.serviceNeeded(autoBoost = false, lowMemoryAlert = true))
        assertFalse(LowMemoryGate.serviceNeeded(autoBoost = false, lowMemoryAlert = false))
    }
}
