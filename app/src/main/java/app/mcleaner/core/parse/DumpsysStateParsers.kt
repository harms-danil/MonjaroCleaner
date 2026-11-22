package app.mcleaner.core.parse

object DumpsysStateParsers {
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
                line.startsWith("state=PlaybackState {state=3,") -> playing = true
            }
        }
        commit()
        return result
    }

    fun currentUser(amGetCurrentUserOut: String): Int = amGetCurrentUserOut.trim().toIntOrNull() ?: 0
}
