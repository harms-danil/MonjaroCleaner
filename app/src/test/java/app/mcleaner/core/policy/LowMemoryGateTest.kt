package app.mcleaner.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowMemoryGateTest {
    private val now = 1_000_000_000L

    @Test
    fun `alerts below the threshold, at most once per cooldown`() {
        assertTrue(LowMemoryGate.shouldAlert(freePercent = 10, thresholdPercent = 15, lastAlertMs = null, nowMs = now))
        assertFalse(LowMemoryGate.shouldAlert(freePercent = 15, thresholdPercent = 15, lastAlertMs = null, nowMs = now))
        assertFalse(LowMemoryGate.shouldAlert(10, 15, lastAlertMs = now - 5 * 60_000, nowMs = now))
        assertTrue(LowMemoryGate.shouldAlert(10, 15, lastAlertMs = now - LowMemoryGate.COOLDOWN_MS, nowMs = now))
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
