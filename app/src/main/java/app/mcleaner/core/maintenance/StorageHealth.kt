package app.mcleaner.core.maintenance

import org.json.JSONArray
import org.json.JSONObject

data class Partition(val name: String, val freeBytes: Long, val totalBytes: Long)

/** What the system itself measures: `dumpsys diskstats` (write latency test, recent write speed, free space). */
data class DiskStats(val writeLatencyMs: Int?, val recentWriteKbps: Int?, val partitions: List<Partition>) {
    companion object {
        private val latency = Regex("""^Latency: (\d+)ms""")
        private val speed = Regex("""Recent Disk Write Speed \(kB/s\) = (\d+)""")
        private val part = Regex("""^(\w+)-Free: (\d+)K / (\d+)K total""")

        fun parse(out: String): DiskStats {
            var lat: Int? = null
            var kbps: Int? = null
            val parts = mutableListOf<Partition>()
            for (line in out.lineSequence().map { it.trim() }) {
                latency.find(line)?.let { lat = it.groupValues[1].toIntOrNull() }
                speed.find(line)?.let { kbps = it.groupValues[1].toIntOrNull() }
                part.find(line)?.let { m ->
                    // On Android 9 «Cache» is the same partition as «Data».
                    if (m.groupValues[1] != "Cache") parts += Partition(m.groupValues[1], m.groupValues[2].toLong() * 1024, m.groupValues[3].toLong() * 1024)
                }
            }
            return DiskStats(lat, kbps, parts)
        }
    }
}

/** eMMC wear: «life_time» gives the used share of rated erase cycles in 10 % steps. */
data class EmmcLife(val usedFromPercent: Int, val usedToPercent: Int)

enum class ReserveState { NORMAL, WARNING, URGENT }

object EmmcHealth {
    private val hex = Regex("""0x([0-9a-fA-F]{2})""")

    /** `life_time` holds two estimates (type A and type B memory); the worse one counts. 0x0B = rated life exceeded. */
    fun lifeTime(out: String): EmmcLife? {
        val v = hex.findAll(out).map { it.groupValues[1].toInt(16) }.filter { it in 1..0x0B }.maxOrNull() ?: return null
        return if (v == 0x0B) EmmcLife(100, 100) else EmmcLife((v - 1) * 10, v * 10)
    }

    /** `pre_eol_info`: how much of the spare block reserve is used up. */
    fun preEol(out: String): ReserveState? = when (hex.find(out)?.groupValues?.get(1)?.toInt(16)) {
        1 -> ReserveState.NORMAL
        2 -> ReserveState.WARNING
        3 -> ReserveState.URGENT
        else -> null
    }
}

data class SpeedSample(val timeMs: Long, val writeMBps: Double)

/** Our own write-speed tests, newest first, so a slowing drive shows up over months. */
object SpeedHistory {
    const val KEEP = 10

    fun add(list: List<SpeedSample>, s: SpeedSample): List<SpeedSample> = (listOf(s) + list).take(KEEP)

    fun encode(list: List<SpeedSample>): String =
        JSONArray(list.map { JSONObject().put("t", it.timeMs).put("w", it.writeMBps) }).toString()

    fun decode(text: String): List<SpeedSample> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).map { i -> a.getJSONObject(i).let { SpeedSample(it.getLong("t"), it.getDouble("w")) } }
    }.getOrDefault(emptyList())
}
