package app.mcleaner.core.log

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class LogLevel { D, I, W, E }

/** One record. Formatting happens later, on the writer thread, so logging costs the caller almost nothing. */
data class LogEntry(
    val wallMs: Long,
    /** Time since boot: the head unit's wall clock is often wrong or jumps after GPS/network sync. */
    val uptimeMs: Long,
    val level: LogLevel,
    val tag: String,
    val thread: String,
    val message: String,
    val error: Throwable?,
)

/** Where entries go. Implementations must not block the caller. */
interface LogSink {
    fun offer(entry: LogEntry)

    /** For crashes: write now, synchronously. */
    fun writeNow(entry: LogEntry) = offer(entry)
}

/**
 * MCleaner's journal. Without a sink (unit tests, before the app starts) every call is a no-op.
 * Never log secrets; package names, commands and results are fine.
 */
object L {
    @Volatile
    var sink: LogSink? = null

    @Volatile
    var uptime: () -> Long = { System.nanoTime() / 1_000_000 }

    fun d(tag: String, message: String) = log(LogLevel.D, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.I, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = log(LogLevel.W, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log(LogLevel.E, tag, message, error)

    fun crash(thread: Thread, error: Throwable) {
        sink?.writeNow(entry(LogLevel.E, "Crash", "uncaught in ${thread.name}", error, thread.name))
    }

    private fun log(level: LogLevel, tag: String, message: String, error: Throwable?) {
        val s = sink ?: return
        s.offer(entry(level, tag, message, error, Thread.currentThread().name))
    }

    private fun entry(level: LogLevel, tag: String, message: String, error: Throwable?, thread: String) =
        LogEntry(System.currentTimeMillis(), uptime(), level, tag, thread, message, error)
}

object LogFormat {
    /** `2026-11-26 12:00:00.123 [+01:02:03.456] I main Boost: message`; extra lines indented. */
    fun line(e: LogEntry, zone: TimeZone = TimeZone.getDefault()): String {
        val wall = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).apply { timeZone = zone }.format(Date(e.wallMs))
        val up = e.uptimeMs
        val since = String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", up / 3_600_000, up / 60_000 % 60, up / 1000 % 60, up % 1000)
        val sb = StringBuilder()
        sb.append(wall).append(" [+").append(since).append("] ").append(e.level.name).append(' ')
            .append(e.thread).append(' ').append(e.tag).append(": ").append(e.message.replace("\n", "\n    "))
        e.error?.let { t ->
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            sb.append('\n').append(sw.toString().trimEnd().prependIndent("    "))
        }
        return sb.toString()
    }
}

object LogFiles {
    const val PREFIX = "mcleaner-"
    const val SUFFIX = ".log"

    /** Names sort by time (`mcleaner-yyyyMMdd-HHmmss.log`): everything but the newest [keep] goes. */
    fun toDelete(names: List<String>, keep: Int): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }.sortedDescending().drop(keep)

    fun needsRotation(sizeBytes: Long, limitBytes: Long) = sizeBytes >= limitBytes
}

/**
 * The stats screen runs the same shell command every 2 s. Logging each run would bury everything else,
 * so a repeated command with the same outcome is logged at most once per [windowMs], with how many runs
 * were skipped. A switch between success and failure is logged at once.
 */
class ExecThrottle(private val windowMs: Long = 60_000) {
    data class Decision(val log: Boolean, val skipped: Int)

    private class Seen(var lastLoggedMs: Long, var ok: Boolean, var skipped: Int)

    private val seen = HashMap<String, Seen>()

    @Synchronized
    fun decide(command: String, ok: Boolean, nowMs: Long): Decision {
        val s = seen[command]
        if (s == null || s.ok != ok) {
            if (s == null && seen.size > 200) seen.clear()
            seen[command] = Seen(nowMs, ok, 0)
            return Decision(log = true, skipped = 0)
        }
        if (nowMs - s.lastLoggedMs >= windowMs || nowMs < s.lastLoggedMs) {
            val skipped = s.skipped
            s.lastLoggedMs = nowMs
            s.skipped = 0
            return Decision(log = true, skipped = skipped)
        }
        s.skipped++
        return Decision(log = false, skipped = 0)
    }
}
