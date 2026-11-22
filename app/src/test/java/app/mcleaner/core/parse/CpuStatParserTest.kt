package app.mcleaner.core.parse

import app.mcleaner.core.model.CpuTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuStatParserTest {
    @Test
    fun parsesAggregateLine() {
        // user..steal = 10000+200+3000+50000+500+0+100+0; idle+iowait = 50500
        assertEquals(CpuTimes(idle = 50_500, total = 63_800), CpuStatParser.parse(Fixtures.read("proc_stat_1.txt")))
    }

    @Test
    fun usageBetweenTwoSnapshots() {
        val a = CpuStatParser.parse(Fixtures.read("proc_stat_1.txt"))!!
        val b = CpuStatParser.parse(Fixtures.read("proc_stat_2.txt"))!!
        // total delta 1800, idle delta 900 -> 50 %
        assertEquals(50, CpuStatParser.usagePercent(a, b))
    }

    @Test
    fun zeroDelta_isZero() {
        val a = CpuTimes(10, 100)
        assertEquals(0, CpuStatParser.usagePercent(a, a))
    }

    @Test
    fun garbage_null() {
        assertNull(CpuStatParser.parse("Permission denied"))
    }
}
