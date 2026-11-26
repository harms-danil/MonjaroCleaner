package app.mcleaner.core.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageHealthTest {
    @Test
    fun `diskstats gives write latency, recent write speed and partitions`() {
        val out = """
            Latency: 1ms [512B Data Write]
            Recent Disk Write Speed (kB/s) = 8931
            Data-Free: 5784976K / 6094400K total = 94% free
            Cache-Free: 5784976K / 6094400K total = 94% free
            System-Free: 1655164K / 2580272K total = 64% free
            App Size Agg: 1
        """.trimIndent()
        val d = DiskStats.parse(out)
        assertEquals(1, d.writeLatencyMs)
        assertEquals(8931, d.recentWriteKbps)
        assertEquals(listOf(Partition("Data", 5784976L * 1024, 6094400L * 1024), Partition("System", 1655164L * 1024, 2580272L * 1024)), d.partitions)
    }

    @Test
    fun `failed latency test and junk are tolerated`() {
        val d = DiskStats.parse("Test-Error: write failed\nnothing")
        assertNull(d.writeLatencyMs)
        assertNull(d.recentWriteKbps)
        assertEquals(emptyList<Partition>(), d.partitions)
    }

    @Test
    fun `eMMC life time estimate becomes a used range`() {
        assertEquals(EmmcLife(usedFromPercent = 0, usedToPercent = 10), EmmcHealth.lifeTime("0x01 0x01"))
        assertEquals(EmmcLife(usedFromPercent = 20, usedToPercent = 30), EmmcHealth.lifeTime("0x02 0x03"))
        assertEquals(EmmcLife(usedFromPercent = 100, usedToPercent = 100), EmmcHealth.lifeTime("0x0b"))
        assertNull(EmmcHealth.lifeTime("0x00 0x00"))
        assertNull(EmmcHealth.lifeTime("Permission denied"))
    }

    @Test
    fun `pre-EOL info is a reserve state`() {
        assertEquals(ReserveState.NORMAL, EmmcHealth.preEol("0x01"))
        assertEquals(ReserveState.WARNING, EmmcHealth.preEol("0x02"))
        assertEquals(ReserveState.URGENT, EmmcHealth.preEol("0x03"))
        assertNull(EmmcHealth.preEol(""))
    }

    @Test
    fun `speed history keeps the last ten newest first`() {
        var h = emptyList<SpeedSample>()
        for (i in 1..12) h = SpeedHistory.add(h, SpeedSample(timeMs = i.toLong(), writeMBps = i.toDouble()))
        assertEquals(10, h.size)
        assertEquals(12L, h.first().timeMs)
        assertEquals(h, SpeedHistory.decode(SpeedHistory.encode(h)))
        assertEquals(emptyList<SpeedSample>(), SpeedHistory.decode("junk"))
    }

    @Test
    fun `read speed is kept and old records without it still load`() {
        val h = listOf(SpeedSample(timeMs = 5, writeMBps = 11.9, readMBps = 180.5))
        assertEquals(h, SpeedHistory.decode(SpeedHistory.encode(h)))
        assertEquals(listOf(SpeedSample(1, 2.0, null)), SpeedHistory.decode("""[{"t":1,"w":2.0}]"""))
    }

    @Test
    fun `UFS health descriptor values parse like eMMC ones`() {
        val probe = "/sys/devices/platform/soc/1d84000.ufshc/health_descriptor/life_time_estimation_a: 0x02\n/sys/devices/platform/soc/1d84000.ufshc/health_descriptor/eol_info: 0x01"
        assertEquals(EmmcLife(10, 20), EmmcHealth.lifeTimeFromProbe(probe))
        assertEquals(ReserveState.NORMAL, EmmcHealth.preEolFromProbe(probe))
        assertEquals(null, EmmcHealth.lifeTimeFromProbe("nothing readable"))
    }
}
