package app.mcleaner.core.maintenance

/** One `cmd package compile -m <mode> -f <pkg>` run. */
data class CompileStep(val packageName: String, val mode: String)

/**
 * Android compiles apps fully only when the device is idle and charging — a head unit almost never is.
 * `dumpsys package dexopt` shows how each app is prepared; [plan] picks what is worth compiling.
 */
object Dexopt {
    const val FULL = "speed"
    const val PROFILE = "speed-profile"

    private val pkgLine = Regex("""^\s*\[([A-Za-z0-9_.]+)]\s*$""")
    private val statusLine = Regex("""\[status=([a-z-]+)]""")

    private val ranks = mapOf(
        "error" to 0, "run-from-apk" to 0, "run-from-vdex-fallback" to 0, "unknown" to 0,
        "extract" to 1, "assume-verified" to 1,
        "verify" to 2, "interpret-only" to 2,
        "quicken" to 3, "space-profile" to 3, "space" to 3,
        "speed-profile" to 4,
        "speed" to 5, "everything" to 6,
    )

    fun rank(status: String): Int = ranks[status] ?: 0

    /** Good enough: compiled by profile or fully. */
    fun isOptimal(status: String): Boolean = rank(status) >= rank(PROFILE)

    /** Package → its worst status over all code paths and instruction sets; packages without code are skipped. */
    fun parse(out: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var pkg: String? = null
        for (line in out.lineSequence()) {
            pkgLine.find(line)?.let { pkg = it.groupValues[1]; return@let }
            val p = pkg ?: continue
            val status = statusLine.find(line)?.groupValues?.get(1) ?: continue
            val cur = result[p]
            if (cur == null || rank(status) < rank(cur)) result[p] = status
        }
        return result
    }

    /**
     * [important] (whitelist, launcher: navigator, music, home screen) get compiled fully; other [userApps]
     * by profile. Only apps whose current state is worse than the target; important ones first.
     */
    fun plan(statuses: Map<String, String>, userApps: Set<String>, important: Set<String>): List<CompileStep> {
        val steps = mutableListOf<CompileStep>()
        for (pkg in important) {
            val s = statuses[pkg] ?: continue
            if (rank(s) < rank(FULL)) steps += CompileStep(pkg, FULL)
        }
        for (pkg in userApps - important) {
            val s = statuses[pkg] ?: continue
            if (rank(s) < rank(PROFILE)) steps += CompileStep(pkg, PROFILE)
        }
        return steps
    }
}

/** When the weekly «Обслуживание» may run: not during the first minutes after start, when the unit is busiest. */
object MaintenanceGate {
    const val WEEK_MS = 7 * 24 * 3600_000L
    const val MIN_UPTIME_MS = 10 * 60_000L

    fun due(enabled: Boolean, ready: Boolean, lastMs: Long?, nowMs: Long, uptimeMs: Long): Boolean {
        if (!enabled || !ready || uptimeMs < MIN_UPTIME_MS) return false
        // Head unit clocks jump (2015 after a battery disconnect, then GPS): a «future» last run means unknown.
        return lastMs == null || lastMs > nowMs || nowMs - lastMs >= WEEK_MS
    }
}
