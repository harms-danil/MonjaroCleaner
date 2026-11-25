package app.mcleaner.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalCacheParserTest {
    @Test
    fun `du output becomes bytes per package, empty folders count as zero`() {
        val out = """
            2052	/storage/emulated/0/Android/data/ru.yandex.music/cache
            4	/storage/emulated/0/Android/data/com.empty.app/cache
            junk line
            12	/storage/emulated/0/Android/data/a.b/cache/
        """.trimIndent()
        assertEquals(
            mapOf("ru.yandex.music" to 2048L * 1024, "com.empty.app" to 0L, "a.b" to 8L * 1024),
            ExternalCacheParser.parse(out),
        )
    }

    @Test
    fun `bigger of the two sources wins, so nothing is counted twice`() {
        assertEquals(500L, ExternalCacheParser.combine(statsBytes = 500, externalBytes = 100))
        assertEquals(900L, ExternalCacheParser.combine(statsBytes = 0, externalBytes = 900))
        assertEquals(900L, ExternalCacheParser.combine(statsBytes = null, externalBytes = 900))
        assertEquals(null, ExternalCacheParser.combine(statsBytes = null, externalBytes = null))
    }
}
