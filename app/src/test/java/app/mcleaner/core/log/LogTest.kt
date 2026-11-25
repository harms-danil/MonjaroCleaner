package app.mcleaner.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LogFormatTest {
    @Test
    fun `line has wall clock, time since boot, level, thread, tag and message`() {
        val e = LogEntry(wallMs = 0, uptimeMs = 3_723_456, level = LogLevel.I, tag = "Boost", thread = "main", message = "stopped 3", error = null)
        assertEquals("1970-01-01 00:00:00.000 [+01:02:03.456] I main Boost: stopped 3", LogFormat.line(e, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `error adds its stack trace and newlines in messages stay on one entry`() {
        val e = LogEntry(0, 0, LogLevel.E, "T", "w", "a\nb", IllegalStateException("boom"))
        val text = LogFormat.line(e, TimeZone.getTimeZone("UTC"))
        assertTrue(text.startsWith("1970-01-01 00:00:00.000 [+00:00:00.000] E w T: a\n    b\n"))
        assertTrue("IllegalStateException: boom" in text)
    }
}

class LogFilesTest {
    @Test
    fun `keeps the newest files by name`() {
        val names = listOf("mcleaner-20261124-100000.log", "mcleaner-20261126-090000.log", "mcleaner-20261125-120000.log", "other.txt")
        assertEquals(listOf("mcleaner-20261124-100000.log"), LogFiles.toDelete(names, keep = 2))
    }

    @Test
    fun `new file is needed past the size limit`() {
        assertFalse(LogFiles.needsRotation(sizeBytes = 100, limitBytes = 1000))
        assertTrue(LogFiles.needsRotation(sizeBytes = 1000, limitBytes = 1000))
    }
}

class ExecThrottleTest {
    @Test
    fun `a repeated command is logged once a minute with the number of skipped runs`() {
        val t = ExecThrottle(windowMs = 60_000)
        assertEquals(ExecThrottle.Decision(log = true, skipped = 0), t.decide("cat /proc/stat", ok = true, nowMs = 0))
        assertEquals(ExecThrottle.Decision(log = false, skipped = 0), t.decide("cat /proc/stat", ok = true, nowMs = 2_000))
        assertEquals(ExecThrottle.Decision(log = false, skipped = 0), t.decide("cat /proc/stat", ok = true, nowMs = 4_000))
        assertEquals(ExecThrottle.Decision(log = true, skipped = 2), t.decide("cat /proc/stat", ok = true, nowMs = 60_000))
    }

    @Test
    fun `a change between success and failure is logged at once, other commands too`() {
        val t = ExecThrottle(windowMs = 60_000)
        t.decide("a", ok = true, nowMs = 0)
        assertTrue(t.decide("a", ok = false, nowMs = 1).log)
        assertTrue(t.decide("a", ok = true, nowMs = 2).log)
        assertTrue(t.decide("b", ok = true, nowMs = 3).log)
    }

    @Test
    fun `the same failure every 2 seconds is also thinned out`() {
        val t = ExecThrottle(windowMs = 60_000)
        assertTrue(t.decide("cat /sys/thermal", ok = false, nowMs = 0).log)
        assertFalse(t.decide("cat /sys/thermal", ok = false, nowMs = 2_000).log)
        assertEquals(ExecThrottle.Decision(log = true, skipped = 1), t.decide("cat /sys/thermal", ok = false, nowMs = 60_000))
    }
}
