package app.mcleaner.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemInfoParserTest {
    @Test
    fun parsesTotalAndAvailable() {
        val snap = MemInfoParser.parse(Fixtures.read("proc_meminfo.txt"))!!
        assertEquals(7812344L, snap.totalKb)
        assertEquals(2903116L, snap.availableKb)
        assertEquals(7812344L - 2903116L, snap.usedKb)
    }

    @Test
    fun missingFields_returnsNull() {
        assertNull(MemInfoParser.parse("MemTotal: 100 kB\nMemFree: 5 kB\n"))
        assertNull(MemInfoParser.parse(""))
    }
}
