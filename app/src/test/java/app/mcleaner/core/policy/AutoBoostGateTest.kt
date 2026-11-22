package app.mcleaner.core.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBoostGateTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    @Test
    fun disabled_false() {
        assertFalse(AutoBoostGate.shouldRun(now, null, enabled = false, privilegedReady = true))
    }

    @Test
    fun notReady_false() {
        assertFalse(AutoBoostGate.shouldRun(now, null, enabled = true, privilegedReady = false))
    }

    @Test
    fun firstRun_true() {
        assertTrue(AutoBoostGate.shouldRun(now, null, enabled = true, privilegedReady = true))
    }

    @Test
    fun bootThenWake_withinCooldown_skipsSecond() {
        assertFalse(AutoBoostGate.shouldRun(now, now - 5 * min, enabled = true, privilegedReady = true))
    }

    @Test
    fun afterCooldown_true() {
        assertTrue(AutoBoostGate.shouldRun(now, now - 10 * min, enabled = true, privilegedReady = true))
    }

    @Test
    fun clockWentBackwards_runs() {
        // head unit clock may be reset before GPS/NTP sync; a "future" last run must not block forever
        assertTrue(AutoBoostGate.shouldRun(now, now + 60 * min, enabled = true, privilegedReady = true))
    }
}
