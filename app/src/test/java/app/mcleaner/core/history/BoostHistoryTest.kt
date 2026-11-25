package app.mcleaner.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostHistoryTest {
    private fun rec(t: Long, freed: Long = 1000, stopped: List<String> = emptyList(), src: BoostSource = BoostSource.MANUAL) =
        BoostRecord(timeMs = t, source = src, freedRamKb = freed, freedCacheBytes = 0, stopped = stopped)

    @Test
    fun `newest first and capped`() {
        var list = emptyList<BoostRecord>()
        for (t in 1L..5L) list = BoostHistory.append(list, rec(t), max = 3)
        assertEquals(listOf(5L, 4L, 3L), list.map { it.timeMs })
    }

    @Test
    fun `json round trip, broken file gives empty history`() {
        val list = listOf(rec(2, 500, listOf("a.b"), BoostSource.SCHEDULE), rec(1, 300, emptyList(), BoostSource.LOW_MEMORY))
        assertEquals(list, BoostHistory.decode(BoostHistory.encode(list)))
        assertTrue(BoostHistory.decode("garbage").isEmpty())
        // Unknown source from a newer version falls back instead of dropping the record.
        val foreign = """[{"t":7,"src":"FUTURE","ram":1,"cache":2,"stopped":["x"]}]"""
        assertEquals(BoostSource.MANUAL, BoostHistory.decode(foreign).single().source)
    }

    @Test
    fun `most often stopped apps`() {
        val list = listOf(rec(3, stopped = listOf("a", "b")), rec(2, stopped = listOf("a")), rec(1, stopped = listOf("c", "a", "b")))
        assertEquals(listOf("a" to 3, "b" to 2, "c" to 1), BoostHistory.topStopped(list, limit = 5))
        assertEquals(listOf("a" to 3), BoostHistory.topStopped(list, limit = 1))
    }

    @Test
    fun `summary counts only the period`() {
        val day = 24 * 3600_000L
        val now = 10 * day
        val list = listOf(rec(now - day, freed = 100), rec(now - 3 * day, freed = 200), rec(now - 8 * day, freed = 999))
        val s = BoostHistory.summary(list, sinceMs = now - 7 * day)
        assertEquals(2, s.count)
        assertEquals(300L, s.freedRamKb)
    }
}
