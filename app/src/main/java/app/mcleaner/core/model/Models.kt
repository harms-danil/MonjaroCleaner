package app.mcleaner.core.model

data class MemSnapshot(val totalKb: Long, val availableKb: Long) {
    val usedKb: Long get() = totalKb - availableKb
}

data class ProcessMem(val packageName: String, val pssKb: Long)

data class CpuTimes(val idle: Long, val total: Long)

data class ShellResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isRunning: Boolean,
    val pssKb: Long?,
    val cacheBytes: Long?,
    val isFrozen: Boolean,
    /** Average CPU % over the last minute or two (full mode only). */
    val cpuPercent: Double? = null,
)
