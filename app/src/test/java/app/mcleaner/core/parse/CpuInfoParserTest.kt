package app.mcleaner.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuInfoParserTest {
    private val out = """
        Load: 0.01 / 0.04 / 0.02
        CPU usage from 101708ms to 11649ms ago (2026-11-25 22:39:17.401 to 2026-11-25 22:40:47.460):
          2.6% 1459/surfaceflinger: 0.3% user + 2.2% kernel / faults: 51 minor
          1.8% 1600/system_server: 1% user + 0.8% kernel / faults: 32089 minor
          12% 2200/ru.yandex.yandexnavi: 10% user + 2% kernel
          3.5% 2201/ru.yandex.yandexnavi:service: 3% user + 0.5% kernel
          0.4% 1749/com.android.systemui: 0.2% user + 0.1% kernel / faults: 2913 minor
          0% 2136/com.android.launcher3: 0% user + 0% kernel / faults: 727 minor
        29% TOTAL: 10% user + 19% kernel
    """.trimIndent()

    @Test
    fun `cpu per installed package, sub-processes summed`() {
        val installed = setOf("ru.yandex.yandexnavi", "com.android.systemui", "com.android.launcher3", "android")
        assertEquals(
            mapOf("ru.yandex.yandexnavi" to 15.5, "android" to 1.8, "com.android.systemui" to 0.4, "com.android.launcher3" to 0.0),
            CpuInfoParser.perPackage(out, installed),
        )
    }

    @Test
    fun `garbage gives nothing`() {
        assertEquals(emptyMap<String, Double>(), CpuInfoParser.perPackage("Permission Denial", setOf("a.b")))
    }
}
