package app.mcleaner.core.parse

object DumpsysStateParsers {
    /** PLAYING, FAST_FORWARDING, REWINDING, BUFFERING, CONNECTING, SKIPPING_TO_PREVIOUS/NEXT/QUEUE_ITEM. */
    private val AUDIBLE_STATES = setOf(3, 4, 5, 6, 8, 9, 10, 11)

    /** Packages of `ServiceRecord`s whose block contains `isForeground=true`. */
    fun foregroundServicePackages(dumpsysActivityServices: String): Set<String> {
        val result = mutableSetOf<String>()
        var pkg: String? = null
        var fg = false
        fun commit() {
            if (fg) pkg?.let(result::add)
        }
        for (raw in dumpsysActivityServices.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("* ") -> {
                    commit(); pkg = null; fg = false
                }
                line.startsWith("packageName=") -> pkg = line.removePrefix("packageName=").trim()
                line.startsWith("isForeground=true") -> fg = true
            }
        }
        commit()
        return result
    }

    /** Packages of media sessions that are `active=true` and in state PLAYING (3). */
    fun activeMediaSessionPackages(dumpsysMediaSession: String): Set<String> {
        val result = mutableSetOf<String>()
        var pkg: String? = null
        var active = false
        var playing = false
        fun commit() {
            if (active && playing) pkg?.let(result::add)
        }
        for (raw in dumpsysMediaSession.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("package=") -> {
                    commit()
                    pkg = line.removePrefix("package=").trim(); active = false; playing = false
                }
                line == "active=true" -> active = true
                line.startsWith("state=PlaybackState {state=") ->
                    playing = line.removePrefix("state=PlaybackState {state=").substringBefore(',').trim().toIntOrNull() in AUDIBLE_STATES
            }
        }
        commit()
        return result
    }

    /** Packages of resumed activities on every display (`dumpsys activity activities`). */
    fun resumedPackages(dumpsysActivities: String): Set<String> =
        resumed.findAll(dumpsysActivities).map { it.groupValues[1] }.toSet()

    private val resumed = Regex("""(?m)^\s*m?ResumedActivity: ActivityRecord\{\S+ u\d+ ([^/\s]+)/""")

    /** A real `dumpsys activity services` always lists ServiceRecords; anything else means "unknown". */
    fun looksLikeServices(text: String): Boolean = "ServiceRecord{" in text

    /** `dumpsys media_session` always prints the "Sessions Stack" header, even with zero sessions. */
    fun looksLikeMediaSessions(text: String): Boolean = "Sessions Stack" in text

    /** `dumpsys activity activities` always starts with its header. */
    fun looksLikeActivities(text: String): Boolean = "ACTIVITY MANAGER ACTIVITIES" in text

    fun currentUser(amGetCurrentUserOut: String): Int = amGetCurrentUserOut.trim().toIntOrNull() ?: 0
}
