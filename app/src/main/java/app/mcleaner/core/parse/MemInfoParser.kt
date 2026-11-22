package app.mcleaner.core.parse

import app.mcleaner.core.model.MemSnapshot

object MemInfoParser {
    private val line = Regex("""^(\w+):\s+(\d+)\s*kB""")

    fun parse(text: String): MemSnapshot? {
        val values = text.lineSequence()
            .mapNotNull { line.find(it.trim())?.destructured }
            .associate { (key, value) -> key to value.toLong() }
        val total = values["MemTotal"] ?: return null
        val available = values["MemAvailable"] ?: return null
        return MemSnapshot(total, available)
    }
}
