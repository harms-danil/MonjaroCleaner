package app.mcleaner.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import app.mcleaner.core.model.CpuTimes
import app.mcleaner.core.model.MemSnapshot
import app.mcleaner.core.parse.CpuStatParser
import app.mcleaner.core.parse.MemInfoParser
import app.mcleaner.privileged.Privileged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class SystemStats(
    val ram: MemSnapshot?,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val cpuPercent: Int?,
    val tempC: Int?,
)

class SystemStatsRepository(private val context: Context, private val privileged: Privileged) {

    fun stats(): Flow<SystemStats> = flow {
        var prevCpu: CpuTimes? = null
        while (true) {
            val cpu = readCpu()
            val percent = if (prevCpu != null && cpu != null) CpuStatParser.usagePercent(prevCpu, cpu) else null
            prevCpu = cpu
            val storage = StatFs(Environment.getDataDirectory().path)
            emit(
                SystemStats(
                    ram = readRam(),
                    storageTotalBytes = storage.totalBytes,
                    storageFreeBytes = storage.availableBytes,
                    cpuPercent = percent,
                    tempC = readTemp(),
                ),
            )
            delay(REFRESH_MS)
        }
    }.flowOn(Dispatchers.IO)

    /** Free RAM in KB, as the system reports it (MemAvailable). */
    fun freeRamKb(): Long = readRam()?.availableKb ?: 0L

    private fun readRam(): MemSnapshot? =
        runCatching { MemInfoParser.parse(File("/proc/meminfo").readText()) }.getOrNull()
            ?: run {
                val am = context.getSystemService(ActivityManager::class.java)
                val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
                MemSnapshot(mi.totalMem / 1024, mi.availMem / 1024)
            }

    private suspend fun readCpu(): CpuTimes? {
        if (!privileged.isAvailable) return null
        val r = privileged.exec("cat /proc/stat")
        return if (r.ok) CpuStatParser.parse(r.out) else null
    }

    private suspend fun readTemp(): Int? {
        val direct = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.mapNotNull { runCatching { File(it, "temp").readText().trim().toLong() }.getOrNull() }
            .orEmpty()
        val values = direct.ifEmpty {
            if (!privileged.isAvailable) return null
            val r = privileged.exec("cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null")
            r.out.lines().mapNotNull { it.trim().toLongOrNull() }
        }
        return values.map { if (it > 1000) it / 1000 else it }.filter { it in 1..150 }.maxOrNull()?.toInt()
    }

    private companion object {
        const val REFRESH_MS = 2_000L
    }
}
