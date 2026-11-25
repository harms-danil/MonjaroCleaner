package app.mcleaner.core.maintenance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceGateTest {
    private val day = 24 * 3600_000L
    private val now = 100 * day
    private val up = 20 * 60_000L

    @Test
    fun `runs weekly, only when on, Shizuku ready and the unit has been up for 10 minutes`() {
        assertTrue(MaintenanceGate.due(enabled = true, ready = true, lastMs = null, nowMs = now, uptimeMs = up))
        assertTrue(MaintenanceGate.due(true, true, now - 7 * day, now, up))
        assertFalse(MaintenanceGate.due(true, true, now - 6 * day, now, up))
        assertFalse(MaintenanceGate.due(false, true, null, now, up))
        assertFalse(MaintenanceGate.due(true, false, null, now, up))
        assertFalse(MaintenanceGate.due(true, true, null, now, uptimeMs = 5 * 60_000L))
    }

    @Test
    fun `a clock that jumped back does not block it forever`() {
        assertTrue(MaintenanceGate.due(true, true, lastMs = now + 30 * day, nowMs = now, uptimeMs = up))
    }
}
