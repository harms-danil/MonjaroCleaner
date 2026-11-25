package app.mcleaner.ui.apps

import app.mcleaner.core.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppsUiTest {
    private fun app(pkg: String, pss: Long?, cache: Long?) =
        AppInfo(pkg, pkg, isSystem = false, isRunning = pss != null, pssKb = pss, cacheBytes = cache, isFrozen = false)

    // Full mode: running apps have memory, the rest do not. Mixing them used to crash the Apps tab
    // (Long compared with the Int fallback -1 → ClassCastException).
    private val apps = listOf(app("a", null, 10), app("b", 500, null), app("c", 200, 30), app("d", null, null))

    @Test
    fun `sort by memory with unknown values does not crash, unknown last`() {
        assertEquals(listOf("b", "c", "a", "d"), AppsUi(apps = apps, sort = AppSort.MEMORY).visible.map { it.packageName })
    }

    @Test
    fun `sort by cache with unknown values does not crash, unknown last`() {
        assertEquals(listOf("c", "a", "b", "d"), AppsUi(apps = apps, sort = AppSort.CACHE).visible.map { it.packageName })
    }
}
