package app.mcleaner.core.policy

/** After «Кэш» → system app page → back: did the cache actually go down? */
object CacheCheck {
    private const val MIN_BYTES = 1024L * 1024

    fun notCleared(before: Long?, after: Long?): Boolean {
        if (before == null || after == null || before < MIN_BYTES) return false
        return after > before * 0.8
    }
}
