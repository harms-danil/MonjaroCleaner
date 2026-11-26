package app.mcleaner.core.maintenance

/** What vold reports about a storage trim (`sm fstrim`) in the system log. */
sealed interface TrimEvent {
    data class Started(val path: String) : TrimEvent
    data class Trimmed(val path: String, val bytes: Long, val ms: Long) : TrimEvent
    data class Failed(val path: String, val reason: String) : TrimEvent
}

object VoldTrimLog {
    private val started = Regex("""Starting trim of (\S+)""")
    private val trimmed = Regex("""Trimmed (\d+) bytes on (\S+) in (\d+)ms""")
    private val failed = Regex("""Trim failed on ([^:\s]+): (.+)$""")

    /** The storage (or its driver) does not take TRIM at all: nothing to fix, nothing wrong. */
    fun unsupported(e: TrimEvent.Failed): Boolean = e.reason.contains("not supported", ignoreCase = true)

    /** `logcat -d -s vold` output → trim events in order. */
    fun parse(out: String): List<TrimEvent> = out.lineSequence().mapNotNull { line ->
        trimmed.find(line)?.let { TrimEvent.Trimmed(it.groupValues[2], it.groupValues[1].toLong(), it.groupValues[3].toLong()) }
            ?: failed.find(line)?.let { TrimEvent.Failed(it.groupValues[1], it.groupValues[2].trim()) }
            ?: started.find(line)?.let { TrimEvent.Started(it.groupValues[1]) }
    }.toList()
}

/** Bytes an app read from and wrote to storage (storaged keeps the last days, by hour). */
data class AppIo(val packageName: String, val readBytes: Long, val writtenBytes: Long)

object Storaged {
    private val row = Regex("""^([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)((?:\s+\d+){8})\s*$""")

    /**
     * `dumpsys storaged`: per app `read write` for foreground and background, with the charger off and on
     * (8 numbers). Summed per app over all records, biggest writers first.
     */
    fun parse(out: String): List<AppIo> {
        val sums = LinkedHashMap<String, LongArray>()
        for (line in out.lineSequence()) {
            val m = row.find(line.trim()) ?: continue
            val n = m.groupValues[2].trim().split(Regex("\\s+")).map { it.toLong() }
            val acc = sums.getOrPut(m.groupValues[1]) { LongArray(2) }
            for (i in n.indices) acc[i % 2] += n[i]
        }
        return sums.map { (k, v) -> AppIo(k, v[0], v[1]) }.sortedByDescending { it.writtenBytes }
    }
}
