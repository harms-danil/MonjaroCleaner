package app.mcleaner.core.junk

import org.junit.Assert.assertEquals
import org.junit.Test

class JunkSelectionTest {
    private val log1 = JunkItem("/s/a.log", 100, JunkCategory.LOGS)
    private val log2 = JunkItem("/s/b.log", 200, JunkCategory.LOGS)
    private val apk = JunkItem("/s/x.apk", 5000, JunkCategory.APK)
    private val tmp = JunkItem("/s/c.tmp", 10, JunkCategory.TEMP)
    private val junk = listOf(log1, log2, apk, tmp).groupBy { it.category }

    @Test
    fun `initial selection takes everything except APK`() {
        val s = JunkSelection.initial(junk)
        assertEquals(setOf(log1.path, log2.path, tmp.path), s)
        assertEquals(CategoryState.NONE, JunkSelection.state(JunkCategory.APK, junk, s))
        assertEquals(CategoryState.ALL, JunkSelection.state(JunkCategory.LOGS, junk, s))
    }

    @Test
    fun `toggling one file makes the category partial`() {
        val s = JunkSelection.toggleItem(JunkSelection.initial(junk), log1.path)
        assertEquals(CategoryState.SOME, JunkSelection.state(JunkCategory.LOGS, junk, s))
        assertEquals(listOf(log2, tmp), JunkSelection.selectedItems(junk, s))
        assertEquals(210L, JunkSelection.selectedBytes(junk, s))
    }

    @Test
    fun `toggling a partial or empty category selects all its files, a full one clears it`() {
        var s = JunkSelection.toggleItem(JunkSelection.initial(junk), log1.path)
        s = JunkSelection.toggleCategory(s, JunkCategory.LOGS, junk)
        assertEquals(CategoryState.ALL, JunkSelection.state(JunkCategory.LOGS, junk, s))
        s = JunkSelection.toggleCategory(s, JunkCategory.LOGS, junk)
        assertEquals(CategoryState.NONE, JunkSelection.state(JunkCategory.LOGS, junk, s))
        s = JunkSelection.toggleCategory(s, JunkCategory.APK, junk)
        assertEquals(setOf(tmp.path, apk.path), s)
    }

    @Test
    fun `after rescan only still-existing paths stay selected`() {
        val s = JunkSelection.initial(junk)
        val rescanned = listOf(log2, apk).groupBy { it.category }
        assertEquals(setOf(log2.path), JunkSelection.retain(s, rescanned))
    }
}
