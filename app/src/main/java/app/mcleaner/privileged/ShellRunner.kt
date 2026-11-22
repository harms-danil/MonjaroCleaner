package app.mcleaner.privileged

import java.util.concurrent.TimeUnit

/**
 * Runs `sh -c command` with a hard timeout. Output larger than [maxChars] is rejected (not truncated):
 * a truncated `dumpsys` could silently hide a busy app, and a huge String would overflow the binder parcel.
 */
object ShellRunner {
    const val TIMEOUT = 124
    const val TOO_LARGE = 125

    fun run(command: String, timeoutMs: Long, maxChars: Int): Pair<Int, String> {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = StringBuilder()
        var tooLarge = false
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(out) {
                            if (out.length + n > maxChars) tooLarge = true else out.append(buf, 0, n)
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.join(200)
            return TIMEOUT to synchronized(out) { out.toString() }
        }
        reader.join(2_000)
        val text = synchronized(out) { out.toString() }
        if (tooLarge) return TOO_LARGE to "output exceeds $maxChars chars"
        return process.exitValue() to text
    }
}
