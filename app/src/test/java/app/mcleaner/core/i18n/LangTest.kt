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
}
