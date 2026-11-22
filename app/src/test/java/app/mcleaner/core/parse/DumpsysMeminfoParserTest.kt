package app.mcleaner.core.parse

import app.mcleaner.core.model.ProcessMem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysMeminfoParserTest {
    private val known = setOf(
        "com.google.android.gms", "ru.yandex.yandexnavi", "com.ecarx.launcher",
        "com.example.game", "com.android.shell",
    )

    @Test
    fun parsesPssByProcess_sortedDesc() {
        val list = DumpsysMeminfoParser.parse(Fixtures.read("dumpsys_meminfo_a11.txt"), known)
        assertEquals(ProcessMem("com.google.android.gms", 401_230L + 211_004L), list[0])
        assertEquals(ProcessMem("ru.yandex.yandexnavi", 540_120L), list[1])
        assertEquals(list.sortedByDescending { it.pssKb }, list)
    }

    @Test
    fun subProcesses_collapsedIntoPackage() {
        val list = DumpsysMeminfoParser.parse(Fixtures.read("dumpsys_meminfo_a11.txt"), known)
        assertEquals(95_300L + 40_100L, list.single { it.packageName == "com.example.game" }.pssKb)
    }

    @Test
    fun onlyFirstSection_noOomDuplicates() {
        val list = DumpsysMeminfoParser.parse(Fixtures.read("dumpsys_meminfo_a11.txt"), known)
        assertEquals(1, list.count { it.packageName == "ru.yandex.yandexnavi" })
        assertTrue(list.none { it.packageName == "surfaceflinger" || it.packageName == "Native" })
    }

    @Test
    fun commaNumbers_parsed() {
        val list = DumpsysMeminfoParser.parse("Total PSS by process:\n    1,234,567K: a.b (pid 1)\n    999K: c.d (pid 2)\n")
        assertEquals(listOf(ProcessMem("a.b", 1_234_567L), ProcessMem("c.d", 999L)), list)
    }

    @Test
    fun rssSectionOnNewerAndroid_parsed() {
        val list = DumpsysMeminfoParser.parse("Total RSS by process:\n    10,000K: a.b (pid 1)\n\nTotal PSS by process:\n    5,000K: a.b (pid 1)\n")
        assertEquals(listOf(ProcessMem("a.b", 5_000L)), list)
    }

    @Test
    fun missingSection_returnsEmpty() {
        assertTrue(DumpsysMeminfoParser.parse("Applications Memory Usage (in Kilobytes):\nUptime: 1\n").isEmpty())
    }

    @Test
    fun garbage_returnsEmpty() {
        assertTrue(DumpsysMeminfoParser.parse("foo\nbar").isEmpty())
        assertTrue(DumpsysMeminfoParser.parse("Total PSS by process:\n    xxK: (pid\n").isEmpty())
    }
}
