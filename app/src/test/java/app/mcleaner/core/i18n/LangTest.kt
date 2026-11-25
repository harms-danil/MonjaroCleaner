package app.mcleaner.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LangTest {
    @After
    fun reset() {
        Lang.current = Language.RU
    }

    @Test
    fun `russian by default, english when chosen`() {
        assertEquals("Обзор", tr("Обзор", "Overview"))
        Lang.current = Language.EN
        assertEquals("Overview", tr("Обзор", "Overview"))
    }

    @Test
    fun `russian plural forms`() {
        fun f(n: Long) = ruPlural(n, "ускорение", "ускорения", "ускорений")
        assertEquals("ускорение", f(1))
        assertEquals("ускорения", f(3))
        assertEquals("ускорений", f(5))
        assertEquals("ускорений", f(11))
        assertEquals("ускорений", f(14))
        assertEquals("ускорение", f(21))
        assertEquals("ускорения", f(22))
        assertEquals("ускорений", f(0))
        assertEquals("1 ускорение", count(1, "ускорение", "ускорения", "ускорений", "boost", "boosts"))
        Lang.current = Language.EN
        assertEquals("2 boosts", count(2, "ускорение", "ускорения", "ускорений", "boost", "boosts"))
    }
}
