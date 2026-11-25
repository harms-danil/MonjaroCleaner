package app.mcleaner.data

import android.os.Process
import app.mcleaner.core.log.LogEntry
import app.mcleaner.core.log.LogFiles
import app.mcleaner.core.log.LogFormat
import app.mcleaner.core.log.LogLevel
import app.mcleaner.core.log.LogSink
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Journal in `filesDir/logs`: one low-priority writer thread, a bounded queue (callers never wait — when the
 * queue is full the entry is dropped and counted), batched writes, files of [FILE_LIMIT] bytes, [KEEP] files kept.
 * Warnings and errors are mirrored to logcat.
 */
class FileLogSink(val dir: File) : LogSink {
    private val queue = ArrayBlockingQueue<LogEntry>(QUEUE)
    private val dropped = AtomicInteger()
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var file: File? = null

    init {
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val batch = ArrayList<LogEntry>(64)
            while (true) {
                val first = runCatching { queue.poll(5, TimeUnit.SECONDS) }.getOrNull() ?: continue
                batch.add(first)
                queue.drainTo(batch, 255)
                synchronized(lock) { runCatching { writeBatch(batch) } }
                batch.clear()
            }
        }, "mcleaner-log").apply { isDaemon = true }.start()
    }

    override fun offer(entry: LogEntry) {
        if (entry.level >= LogLevel.W) mirror(entry)
        if (!queue.offer(entry)) dropped.incrementAndGet()
    }

    override fun writeNow(entry: LogEntry) {
        mirror(entry)
        synchronized(lock) {
            runCatching {
                val pending = ArrayList<LogEntry>()
                queue.drainTo(pending)
                writeBatch(pending + entry)
            }
        }
    }

    /** Pushes everything queued so far to disk (before an export). */
    fun flush() {
        synchronized(lock) {
            runCatching {
                val pending = ArrayList<LogEntry>()
                queue.drainTo(pending)
                writeBatch(pending)
            }
        }
    }

    fun files(): List<File> = dir.listFiles { f -> f.name.startsWith(LogFiles.PREFIX) && f.name.endsWith(LogFiles.SUFFIX) }
        ?.sortedBy { it.name }.orEmpty()

    fun totalBytes(): Long = files().sumOf { it.length() }

    /** «Очистить журнал»: the current file is reopened empty. */
    fun clear() {
        synchronized(lock) {
            runCatching { writer?.close() }
            writer = null
            file = null
            files().forEach { it.delete() }
        }
    }

    private fun writeBatch(batch: List<LogEntry>) {
        if (batch.isEmpty()) return
        val w = currentWriter()
        val lost = dropped.getAndSet(0)
        if (lost > 0) w.append("--- $lost entries dropped (queue full) ---\n")
        for (e in batch) w.append(LogFormat.line(e)).append('\n')
        w.flush()
        if (LogFiles.needsRotation(file?.length() ?: 0, FILE_LIMIT)) {
            runCatching { w.close() }
            writer = null
        }
    }

    private fun currentWriter(): BufferedWriter {
        writer?.let { return it }
        dir.mkdirs()
        // Continue the newest file after an app restart, so restarts don't push history out.
        files().lastOrNull()?.takeIf { !LogFiles.needsRotation(it.length(), FILE_LIMIT) }?.let { last ->
            file = last
            return BufferedWriter(OutputStreamWriter(FileOutputStream(last, true), Charsets.UTF_8), 16 * 1024).also { writer = it }
        }
        val name = LogFiles.PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + LogFiles.SUFFIX
        val f = File(dir, name)
        LogFiles.toDelete((files().map { it.name } + name).distinct(), KEEP).forEach { File(dir, it).delete() }
        file = f
        return BufferedWriter(OutputStreamWriter(FileOutputStream(f, true), Charsets.UTF_8), 16 * 1024).also { writer = it }
    }

    private fun mirror(e: LogEntry) {
        val tag = "MCleaner/${e.tag}"
        if (e.level == LogLevel.E) android.util.Log.e(tag, e.message, e.error) else android.util.Log.w(tag, e.message, e.error)
    }

    companion object {
        const val FILE_LIMIT = 1_000_000L
        const val KEEP = 8
        private const val QUEUE = 4096
    }
}
