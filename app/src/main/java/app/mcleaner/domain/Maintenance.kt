package app.mcleaner.domain

import android.content.Context
import android.os.SystemClock
import app.mcleaner.core.log.L
import app.mcleaner.core.maintenance.AppIo
import app.mcleaner.core.maintenance.CompileStep
import app.mcleaner.core.maintenance.DiskStats
import app.mcleaner.core.maintenance.Dexopt
import app.mcleaner.core.maintenance.EmmcHealth
import app.mcleaner.core.maintenance.EmmcLife
import app.mcleaner.core.maintenance.ReserveState
import app.mcleaner.core.maintenance.SpeedHistory
import app.mcleaner.core.maintenance.SpeedSample
import app.mcleaner.core.maintenance.Storaged
import app.mcleaner.core.maintenance.TrimEvent
import app.mcleaner.core.maintenance.VoldTrimLog
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class CompileProgress(val done: Int, val total: Int, val current: String?, val failed: List<String> = emptyList())

/** One app of a compile run: how it was prepared before and after. */
data class CompileOutcome(val packageName: String, val before: String?, val after: String?, val ok: Boolean)

data class CompileReport(
    val outcomes: List<CompileOutcome>,
    val optimalBefore: Int,
    val optimalAfter: Int,
    val total: Int,
    /** Free space on /data before minus after: what the compiled code took. */
    val spaceUsedBytes: Long,
    val durationMs: Long,
    val stopped: Boolean,
)

/** `sm fstrim` progress, read from vold's lines in the system log. */
data class TrimProgress(val running: Boolean, val events: List<TrimEvent>, val timedOut: Boolean = false)

data class StorageInfo(
    val disk: DiskStats?,
    val uptimeMs: Long,
    val life: EmmcLife?,
    val reserve: ReserveState?,
    /** Trims vold logged since boot (before our run). */
    val trimsSinceBoot: List<TrimEvent>,
    /** «Last maintenance» from the storage service: when Android last did its own idle maintenance. */
    val systemMaintenance: String?,
    /** Biggest writers over the period storaged keeps (about the last day). */
    val topWriters: List<AppIo>,
    val totalWrittenBytes: Long?,
)

/**
 * «Обслуживание»: compile apps (Android does it only when idle and charging — a head unit rarely is),
 * storage trim with its result, storage info and speed tests.
 */
class Maintenance(private val context: Context, private val privileged: Privileged) {

    private val _compile = MutableStateFlow<CompileProgress?>(null)
    val compile: StateFlow<CompileProgress?> = _compile.asStateFlow()

    private val _trim = MutableStateFlow<TrimProgress?>(null)
    val trim: StateFlow<TrimProgress?> = _trim.asStateFlow()

    private val historyFile get() = File(context.filesDir, "speed-history.json")

    /** Package → dexopt status; empty without Shizuku. */
    suspend fun dexoptStatus(): Map<String, String> {
        if (!privileged.isAvailable) return emptyMap()
        val r = privileged.exec("dumpsys package dexopt | grep -E '^ +\\[|status='")
        return if (r.ok) Dexopt.parse(r.out) else emptyMap()
    }

    /** Runs [steps] one by one; cancel the calling job to stop after the current app. */
    suspend fun compile(steps: List<CompileStep>): CompileReport {
        L.i("Maintenance", "compile ${steps.size} app(s): $steps")
        val t0 = SystemClock.elapsedRealtime()
        val before = dexoptStatus()
        val freeBefore = freeData()
        val done = mutableListOf<Pair<String, Boolean>>()
        var stopped = false
        try {
            for ((i, s) in steps.withIndex()) {
                currentCoroutineContext().ensureActive()
                _compile.value = CompileProgress(i, steps.size, s.packageName, done.filter { !it.second }.map { it.first })
                val r = privileged.execLong("cmd package compile -m ${s.mode} -f ${shellQuote(s.packageName)}", COMPILE_TIMEOUT_MS)
                done += s.packageName to (r.ok && "Success" in r.out)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            stopped = true
            throw e
        } finally {
            _compile.value = null
            if (stopped) L.i("Maintenance", "compile stopped after ${done.size} app(s)")
        }
        val after = dexoptStatus()
        val report = CompileReport(
            outcomes = done.map { (pkg, ok) -> CompileOutcome(pkg, before[pkg], after[pkg], ok) },
            optimalBefore = before.values.count(Dexopt::isOptimal),
            optimalAfter = after.values.count(Dexopt::isOptimal),
            total = after.size,
            spaceUsedBytes = (freeBefore - freeData()).coerceAtLeast(0),
            durationMs = SystemClock.elapsedRealtime() - t0,
            stopped = false,
        )
        L.i("Maintenance", "compile done: $report")
        return report
    }

    private fun freeData(): Long = runCatching { android.os.StatFs("/data").availableBytes }.getOrDefault(0L)

    /**
     * `sm fstrim` only starts the trim; vold logs «Starting trim of …», then «Trimmed N bytes on … in M ms»
     * or «Trim failed on …». Follows those lines for up to [TRIM_WAIT_MS].
     */
    suspend fun trimStorage(): TrimProgress {
        val baseline = voldEvents().size
        _trim.value = TrimProgress(running = true, events = emptyList())
        try {
            val started = privileged.execLong("sm fstrim", 60_000).ok
            L.i("Maintenance", "fstrim requested: $started")
            if (!started) return TrimProgress(false, emptyList()).also { _trim.value = it }
            val t0 = SystemClock.elapsedRealtime()
            var stableSince = t0
            var lastCount = 0
            while (true) {
                delay(2_000)
                val events = voldEvents().drop(baseline)
                if (events.size != lastCount) {
                    lastCount = events.size
                    stableSince = SystemClock.elapsedRealtime()
                }
                val starts = events.count { it is TrimEvent.Started }
                val ends = events.count { it !is TrimEvent.Started }
                val now = SystemClock.elapsedRealtime()
                val finished = starts > 0 && ends >= starts && now - stableSince > 4_000
                val timedOut = now - t0 > TRIM_WAIT_MS
                _trim.value = TrimProgress(running = !(finished || timedOut), events = events, timedOut = timedOut && !finished)
                if (finished || timedOut) {
                    L.i("Maintenance", "fstrim result: $events, timed out $timedOut")
                    return _trim.value!!
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            L.w("Maintenance", "fstrim follow failed", e)
            return TrimProgress(false, emptyList()).also { _trim.value = it }
        }
    }

    private suspend fun voldEvents(): List<TrimEvent> =
        VoldTrimLog.parse(privileged.exec("logcat -d -s vold 2>/dev/null | tail -n 400").out)

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val full = privileged.isAvailable
        if (!full) return@withContext StorageInfo(null, SystemClock.elapsedRealtime(), null, null, emptyList(), null, emptyList(), null)
        val disk = privileged.exec("dumpsys diskstats | head -n 20").takeIf { it.ok }?.let { DiskStats.parse(it.out) }
        val probe = privileged.exec(HEALTH_PROBE).out
        L.i("Maintenance", "health probe: ${probe.trim().ifEmpty { "nothing readable" }}")
        val io = Storaged.parse(privileged.exec("dumpsys storaged 2>/dev/null").out)
        val maint = Regex("""(?i)last maintenance:?\s*(.+)""").find(privileged.exec("dumpsys mount 2>/dev/null | grep -i 'maint'").out)?.groupValues?.get(1)?.trim()
        StorageInfo(
            disk = disk,
            uptimeMs = SystemClock.elapsedRealtime(),
            life = EmmcHealth.lifeTimeFromProbe(probe),
            reserve = EmmcHealth.preEolFromProbe(probe),
            trimsSinceBoot = voldEvents(),
            systemMaintenance = maint,
            topWriters = io.take(8),
            totalWrittenBytes = io.sumOf { it.writtenBytes }.takeIf { io.isNotEmpty() },
        )
    }

    fun speedHistory(): List<SpeedSample> = runCatching { SpeedHistory.decode(historyFile.readText()) }.getOrDefault(emptyList())

    /**
     * Write: [TEST_MB] MB into MCleaner's own folder with fsync, then deleted. Read: the biggest APK among
     * [readCandidates] (apps that are not running, so their file is unlikely to be in memory). Only on request.
     */
    suspend fun speedTest(readCandidates: List<File>): SpeedSample? = withContext(Dispatchers.IO) {
        val f = File(context.cacheDir, "speed-test.bin")
        runCatching {
            val buf = ByteArray(1 shl 20) { (it * 31).toByte() }
            val t0 = System.nanoTime()
            FileOutputStream(f).use { out ->
                repeat(TEST_MB) { out.write(buf) }
                out.fd.sync()
            }
            val write = round1(TEST_MB / ((System.nanoTime() - t0) / 1e9))
            f.delete()
            val apk = readCandidates.filter { it.canRead() }.maxByOrNull { it.length() }
            val read = apk?.let { file ->
                val t1 = System.nanoTime()
                var total = 0L
                FileInputStream(file).use { input -> while (true) { val n = input.read(buf); if (n < 0) break; total += n } }
                round1(total / 1048576.0 / ((System.nanoTime() - t1) / 1e9))
            }
            val sample = SpeedSample(System.currentTimeMillis(), write, read)
            historyFile.writeText(SpeedHistory.encode(SpeedHistory.add(speedHistory(), sample)))
            L.i("Maintenance", "speed test: write $write MB/s, read $read MB/s (${apk?.path}, ${apk?.length()} B)")
            sample
        }.onFailure { L.w("Maintenance", "speed test failed", it) }.also { f.delete() }.getOrNull()
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    companion object {
        const val TEST_MB = 256
        const val COMPILE_TIMEOUT_MS = 10 * 60_000L
        const val TRIM_WAIT_MS = 180_000L

        /** Read speed above this is the page cache, not the storage. */
        const val CACHED_READ_MBPS = 1000.0

        /** eMMC (mmc_host, mmcblk0) and UFS (health_descriptor) wear files; prints only the readable ones. */
        private const val HEALTH_PROBE =
            "for f in /sys/class/mmc_host/*/*/life_time /sys/class/mmc_host/*/*/pre_eol_info " +
                "/sys/block/mmcblk0/device/life_time /sys/block/mmcblk0/device/pre_eol_info " +
                "/sys/devices/platform/*/health_descriptor/life_time_estimation_a /sys/devices/platform/*/health_descriptor/life_time_estimation_b " +
                "/sys/devices/platform/*/health_descriptor/eol_info /sys/devices/platform/soc/*/health_descriptor/life_time_estimation_a " +
                "/sys/devices/platform/soc/*/health_descriptor/life_time_estimation_b /sys/devices/platform/soc/*/health_descriptor/eol_info; " +
                "do [ -r \"\$f\" ] && echo \"\$f: \$(cat \"\$f\" 2>/dev/null)\"; done; true"
    }
}
