package app.mcleaner.core.parse

/** `du -sk <root>/Android/data/<pkg>/cache` output → cache bytes per package. */
object ExternalCacheParser {
    private val line = Regex("""^(\d+)\s+\S*/Android/data/([^/\s]+)/cache/?$""")

    /** An empty directory itself takes 4 KB in du. */
    private const val EMPTY_DIR_KB = 4L

    fun parse(out: String): Map<String, Long> = out.lineSequence().mapNotNull { raw ->
        val m = line.find(raw.trim()) ?: return@mapNotNull null
        m.groupValues[2] to (m.groupValues[1].toLong() - EMPTY_DIR_KB).coerceAtLeast(0) * 1024
    }.toMap()

    /**
     * System stats may or may not include the external cache (depends on firmware), so take the bigger
     * value: never counts the same bytes twice, and no longer shows 0 for apps that cache only externally.
     */
    fun combine(statsBytes: Long?, externalBytes: Long?): Long? = when {
        statsBytes == null -> externalBytes
        externalBytes == null -> statsBytes
        else -> maxOf(statsBytes, externalBytes)
    }
}
