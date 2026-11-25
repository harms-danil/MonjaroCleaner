package app.mcleaner.core.parse

/**
 * `dumpsys cpuinfo`: each process's average CPU over the last minute or two (the system keeps this window).
 * Steadier for sorting than one `top` frame. Processes `pkg:name` count towards `pkg`; system_server is "android".
 */
object CpuInfoParser {
    private val line = Regex("""^\s*([\d.]+)% \d+/([^:\s]+(?::[^:\s]+)?): """)

    fun perPackage(out: String, installed: Set<String>): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        for (raw in out.lineSequence()) {
            val m = line.find(raw) ?: continue
            val process = m.groupValues[2]
            val pkg = if (process == "system_server") "android" else process.substringBefore(':')
            if (pkg !in installed) continue
            val pct = m.groupValues[1].toDoubleOrNull() ?: continue
            result[pkg] = Math.round(((result[pkg] ?: 0.0) + pct) * 10) / 10.0
        }
        return result
    }
}
