package app.mcleaner.privileged

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {
    @Test
    fun plainPath() {
        assertEquals("'/sdcard/a b.log'", shellQuote("/sdcard/a b.log"))
    }

    @Test
    fun singleQuoteEscaped() {
        assertEquals("'it'\\''s'", shellQuote("it's"))
    }
}
