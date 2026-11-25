package app.mcleaner.domain

import android.content.Context
import android.os.SystemClock
import app.mcleaner.core.log.L
import app.mcleaner.core.maintenance.CompileStep
import app.mcleaner.core.maintenance.DiskStats
import app.mcleaner.core.maintenance.Dexopt
import app.mcleaner.core.maintenance.EmmcHealth
import app.mcleaner.core.maintenance.EmmcLife
import app.mcleaner.core.maintenance.ReserveState
import app.mcleaner.core.maintenance.SpeedHistory
import app.mcleaner.core.maintenance.SpeedSample
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CompileProgress(val done: Int, val total: Int, val current: String?, val failed: List<String> = emptyList())

data class StorageInfo(
    val disk: DiskStats?,
    val uptimeMs: Long,
    val life: EmmcLife?,
    val reserve: ReserveState?,
    /** Since boot, from /proc/diskstats when the firmware lets shell read it. */
    val readBytes: Long?,
    val writtenBytes: Long?,
)

/**
 * «Обслуживание»: compile apps (Android does it only when idle and charging — a head unit rarely is),
 * storage trim, storage info and a write-speed test.
 */
class Maintenance(private val context: Context, private val privileged: Privileged) {

    private val _compile = MutableStateFlow<CompileProgress?>(null)
    val compile: StateFlow<CompileProgress?> = _compile.asStateFlow()

    private val historyFile get() = File(context.filesDir, "speed-history.json")

    /** Package → dexopt status; empty without Shizuku. */
    suspend fun dexoptStatus(): Map<String, String> {
        if (!privileged.isAvailable) return emptyMap()
        val r = privileged.exec("dumpsys package dexopt | grep -E '^ +\\[|status='")
        return if (r.ok) Dexopt.parse(r.out) else emptyMap()
    }

    /** Runs [steps] one by one; cancel the calling job to stop after the current app. */
    suspend fun compile(steps: List<CompileStep>): CompileProgress {
        L.i("Maintenance", "compile ${steps.size} app(s): $steps")
        val failed = mutableListOf<String>()
        var p = CompileProgress(0, steps.size, null)
        try {
            for ((i, s) in steps.withIndex()) {
                currentCoroutineContext().ensureActive()
                p = CompileProgress(i, steps.size, s.packageName, failed.toList())
                _compile.value = p
                val r = privileged.execLong("cmd package compile -m ${s.mode} -f ${shellQuote(s.packageName)}", COMPILE_TIMEOUT_MS)
                if (!r.ok || "Success" !in r.out) failed += s.packageName
            }
            p = CompileProgress(steps.size, steps.size, null, failed.toList())
            L.i("Maintenance", "compile finished, failed: $failed")
            return p
        } finally {
            _compile.value = null
        }
    }

    /** `sm fstrim` only starts the trim; the system finishes it in the background within a minute or two. */
    suspend fun trimStorage(): Boolean = privileged.execLong("sm fstrim", 60_000).ok.also { L.i("Maintenance", "fstrim requested: $it") }

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val full = privileged.isAvailable
        val disk = if (full) privileged.exec("dumpsys diskstats | head -n 20").takeIf { it.ok }?.let { DiskStats.parse(it.out) } else null
        // Readable on some firmwares only; silently absent otherwise.
        val life = if (full) EmmcHealth.lifeTime(privileged.exec("cat /sys/class/mmc_host/mmc*/mmc*/life_time 2>/dev/null").out) else null
        val eol = if (full) EmmcHealth.preEol(privileged.exec("cat /sys/class/mmc_host/mmc*/mmc*/pre_eol_info 2>/dev/null").out) else null
        val io = if (full) ioSinceBoot(privileged.exec("cat /proc/diskstats 2>/dev/null").out) else null
        StorageInfo(disk, SystemClock.elapsedRealtime(), life, eol, io?.first, io?.second)
    }

    /** Sectors read/written of the main block device (mmcblk0 or sda), 512 bytes each. */
    private fun ioSinceBoot(out: String): Pair<Long, Long>? {
        val f = out.lineSequence().map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size > 9 && (it[2] == "mmcblk0" || it[2] == "sda") } ?: return null
        val read = f[5].toLongOrNull() ?: return null
        val written = f[9].toLongOrNull() ?: return null
        return read * 512 to written * 512
    }

    fun speedHistory(): List<SpeedSample> = runCatching { SpeedHistory.decode(historyFile.readText()) }.getOrDefault(emptyList())

    /**
     * Writes [TEST_MB] MB into MCleaner's own folder with fsync, measures, deletes. Only on request:
     * the wear is negligible but there is no reason to do it unasked. Reading is not measured: right after
     * writing Android serves the file from memory, which would show a fantasy number.
     */
    suspend fun writeSpeedTest(): SpeedSample? = withContext(Dispatchers.IO) {
        val f = File(context.cacheDir, "speed-test.bin")
        runCatching {
            val buf = ByteArray(1 shl 20) { (it * 31).toByte() }
            val t0 = System.nanoTime()
            FileOutputStream(f).use { out ->
                repeat(TEST_MB) { out.write(buf) }
                out.fd.sync()
            }
            val sec = (System.nanoTime() - t0) / 1e9
            val sample = SpeedSample(System.currentTimeMillis(), Math.round(TEST_MB / sec * 10) / 10.0)
            historyFile.writeText(SpeedHistory.encode(SpeedHistory.add(speedHistory(), sample)))
            L.i("Maintenance", "write speed test: ${sample.writeMBps} MB/s")
            sample
        }.onFailure { L.w("Maintenance", "speed test failed", it) }.also { f.delete() }.getOrNull()
    }

    companion object {
        const val TEST_MB = 256
        const val COMPILE_TIMEOUT_MS = 10 * 60_000L
    }
}
