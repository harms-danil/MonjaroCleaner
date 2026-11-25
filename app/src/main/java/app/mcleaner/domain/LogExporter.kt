package app.mcleaner.domain

import android.os.Build
import android.os.SystemClock
import app.mcleaner.AppContainer
import app.mcleaner.BuildConfig
import app.mcleaner.core.log.L
import app.mcleaner.data.FileLogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * «Выгрузить журнал»: one zip with MCleaner's journal, a device/settings summary and — with Shizuku —
 * the system log and memory/storage snapshots, so a problem can be analysed away from the car.
 */
class LogExporter(private val c: AppContainer, private val sink: FileLogSink) {

    fun fileName(): String = "mcleaner-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".zip"

    suspend fun writeZip(out: OutputStream) = withContext(Dispatchers.IO) {
        L.i("Export", "journal export started")
        sink.flush()
        val extras = if (c.shizuku.isAvailable) {
            SNAPSHOTS.map { (name, cmd) -> name to c.shizuku.exec(cmd).let { "$ $cmd\n(exit ${it.code})\n${it.out}" } }
        } else {
            listOf("system-logs.txt" to "Shizuku is not running: system log and snapshots are unavailable.")
        }
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("info.txt", info().toByteArray())
            extras.forEach { (name, text) -> put(name, text.toByteArray()) }
            sink.flush()
            sink.files().forEach { f -> put("journal/${f.name}", f.readBytes()) }
        }
        L.i("Export", "journal export done")
    }

    /** No system file picker on the head unit: save into /sdcard/MCleaner/logs. Null without storage access. */
    suspend fun writeToFolder(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(android.os.Environment.getExternalStorageDirectory(), "MCleaner/logs").apply { mkdirs() }
            val f = File(dir, fileName())
            f.outputStream().use { writeZip(it) }
            f.path
        }.onFailure { L.w("Export", "save to folder failed", it) }.getOrNull()
    }

    private suspend fun info(): String {
        val s = runCatching { c.settings.current() }.getOrNull()
        val ui = c.ui
        return buildString {
            appendLine("MCleaner ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Exported: ${Date()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), board ${Build.BOARD}, hardware ${Build.HARDWARE}")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}, fingerprint ${Build.FINGERPRINT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Uptime: ${SystemClock.elapsedRealtime() / 60_000} min")
            appendLine("Shizuku: ${c.shizuku.state.value}")
            appendLine("Language: ${ui.language}, theme: ${ui.theme.value}")
            appendLine("Usage access: ${c.apps.hasUsageAccess()}, storage: ${c.apps.hasAllFilesAccess()}, install apps: ${c.apps.canInstallApps()}, battery unrestricted: ${c.apps.ignoresBatteryOptimizations()}")
            appendLine()
            appendLine("Settings: $s")
            appendLine()
            appendLine("/proc/meminfo:")
            appendLine(runCatching { File("/proc/meminfo").readLines().take(8).joinToString("\n") }.getOrDefault("-"))
            appendLine()
            appendLine("Journal files: ${sink.files().joinToString { "${it.name} (${it.length()} B)" }}")
        }
    }

    private companion object {
        /** Each output stays under the shell service's 200 000-character limit. */
        val SNAPSHOTS = listOf(
            "system-logs.txt" to "logcat -d -v threadtime -t 4000 2>&1 | tail -c 190000",
            "crashes.txt" to "logcat -d -b crash -v threadtime 2>&1 | tail -c 100000",
            "meminfo.txt" to "dumpsys meminfo 2>&1 | tail -n 80",
            "diskstats.txt" to "dumpsys diskstats 2>&1 | head -n 40",
            "running.txt" to "dumpsys activity recents 2>&1 | grep -E 'Recent #|realActivity' | head -n 80",
            "props.txt" to "getprop | grep -E 'ro\\.(build|product|hardware|board|vendor)' | head -n 120",
        )
    }
}
