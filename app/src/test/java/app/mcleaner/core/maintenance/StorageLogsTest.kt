package app.mcleaner.core.maintenance

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageLogsTest {
    @Test
    fun `vold log lines become trim events`() {
        val out = """
            11-26 15:18:05.093 D/vold    ( 1306): Starting trim of /data
            11-26 15:18:05.094 W/vold    ( 1306): Trim failed on /data: Operation not supported on transport endpoint
            11-26 15:20:01.000 I/vold    ( 1306): Trimmed 1073741824 bytes on /data in 3456ms
            11-26 15:20:01.001 I/vold    ( 1306): Something else
        """.trimIndent()
        assertEquals(
            listOf(
                TrimEvent.Started("/data"),
                TrimEvent.Failed("/data", "Operation not supported on transport endpoint"),
                TrimEvent.Trimmed("/data", 1073741824L, 3456L),
            ),
            VoldTrimLog.parse(out),
        )
    }

    @Test
    fun `storaged dump gives read and write bytes per app, foreground and background summed`() {
        val out = """
            17,1795591487
            1010 5373952 0 0 0 0 0 0 0
            com.android.calendar 0 0 1081344 16384 0 0 0 0
            com.android.launcher3 385024 1552384 0 122880 0 0 0 0
            junk
        """.trimIndent()
        assertEquals(
            listOf(
                AppIo("com.android.launcher3", readBytes = 385024, writtenBytes = 1552384L + 122880),
                AppIo("com.android.calendar", readBytes = 1081344, writtenBytes = 16384),
            ),
            Storaged.parse(out),
        )
    }

    @Test
    fun `storaged records repeated per hour are summed per app`() {
        val out = "1\ncom.a 1 2 3 4 0 0 0 0\n2\ncom.a 10 20 30 40 1 1 1 1"
        assertEquals(listOf(AppIo("com.a", readBytes = 1 + 3 + 10 + 30 + 1 + 1, writtenBytes = 2 + 4 + 20 + 40 + 1 + 1)), Storaged.parse(out))
    }

    @Test
    fun `unsupported trim is recognised`() {
        assertEquals(true, VoldTrimLog.unsupported(TrimEvent.Failed("/data", "Operation not supported on transport endpoint")))
        assertEquals(false, VoldTrimLog.unsupported(TrimEvent.Failed("/data", "I/O error")))
    }
}
