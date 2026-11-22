package app.mcleaner.core.parse

import app.mcleaner.core.model.CpuTimes

object CpuStatParser {
    /** Aggregate `cpu ` line: total = user..steal (8 fields), idle = idle + iowait. */
    fun parse(text: String): CpuTimes? {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val f = line.removePrefix("cpu").trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (f.size < 4) return null
        val fields = f.take(8)
        val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
        return CpuTimes(idle = idle, total = fields.sum())
    }

    fun usagePercent(prev: CpuTimes, cur: CpuTimes): Int {
        val total = cur.total - prev.total
        if (total <= 0) return 0
        val busy = total - (cur.idle - prev.idle)
        return (busy * 100 / total).toInt().coerceIn(0, 100)
    }
}
