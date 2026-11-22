package app.mcleaner.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    private val mb = 1024L * 1024
    private val gb = 1024L * mb

    @Test
    fun megabytes_integer() {
        assertEquals("612 МБ", Formatters.bytes(612 * mb))
        assertEquals("0 МБ", Formatters.kb(0))
        assertEquals("1 МБ", Formatters.bytes(700 * 1024L))
    }

    @Test
    fun gigabytes_oneDecimalComma() {
        assertEquals("1,2 ГБ", Formatters.bytes(1288490188L))
        assertEquals("8 ГБ", Formatters.bytes(8 * gb))
    }

    @Test
    fun usedOfTotal() {
        assertEquals("5,1 / 8 ГБ", Formatters.usedOfTotal(5_476_083_302L, 8 * gb))
    }

    @Test
    fun negative_clampedToZero() {
        assertEquals("0 МБ", Formatters.bytes(-5))
    }
}
