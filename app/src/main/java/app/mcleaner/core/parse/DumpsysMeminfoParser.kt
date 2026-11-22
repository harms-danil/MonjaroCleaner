package app.mcleaner.core.parse

import app.mcleaner.core.model.ProcessMem

/**
 * Parses the "Total PSS by process:" section of `dumpsys meminfo`.
 * Process names are reduced to packages: `pkg:sub` -> `pkg`; when [knownPackages] is given,
 * names like `com.google.android.gms.persistent` are mapped to the longest known prefix package.
 */
object DumpsysMeminfoParser {
    private const val HEADER = "Total PSS by process:"
    private val entry = Regex("""^([\d,]+)K:\s+(\S+)\s+\(pid""")

    fun parse(text: String, knownPackages: Set<String> = emptySet()): List<ProcessMem> {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim() == HEADER }
        if (start < 0) return emptyList()
        val totals = LinkedHashMap<String, Long>()
        for (raw in lines.drop(start + 1)) {
            val line = raw.trim()
            if (line.isEmpty()) break
            val m = entry.find(line) ?: continue
            val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            val pkg = toPackage(m.groupValues[2], knownPackages)
            totals[pkg] = (totals[pkg] ?: 0L) + kb
        }
        return totals.map { (pkg, kb) -> ProcessMem(pkg, kb) }.sortedByDescending { it.pssKb }
    }

    private fun toPackage(processName: String, known: Set<String>): String {
        val base = processName.substringBefore(':')
        if (known.isEmpty() || base in known) return base
        var candidate = base
        while ('.' in candidate) {
            candidate = candidate.substringBeforeLast('.')
            if (candidate in known) return candidate
        }
        return base
    }
}
