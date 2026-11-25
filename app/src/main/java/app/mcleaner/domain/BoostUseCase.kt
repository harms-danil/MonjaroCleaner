package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostContext
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.privileged.Privileged

data class BoostReport(
    val fullMode: Boolean,
    val stopped: Int,
    val failed: List<String>,
    val freedRamKb: Long,
    val freedCacheBytes: Long,
    val protectionUnavailable: Boolean = false,
    /** Force-stopped packages (full mode), for the history. */
    val stoppedPackages: List<String> = emptyList(),
)

class BoostUseCase(
    private val privileged: Privileged,
    private val loadApps: suspend () -> List<AppInfo>,
    private val buildContext: suspend () -> BoostContext,
    private val readFreeRamKb: () -> Long,
    private val totalCacheBytes: suspend () -> Long,
    private val killBackground: (String) -> Unit,
    private val cacheCleaner: CacheCleaner,
    private val currentUser: suspend () -> Int,
    private val delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    /** Shared storage root for the per-app external cache fallback. */
    private val externalRoot: String = "/storage/emulated/0",
) {
    suspend fun run(): BoostReport {
        val ramBefore = readFreeRamKb()
        val cacheBefore = totalCacheBytes()
        val apps = loadApps()
        val ctx = buildContext()
        val full = privileged.isAvailable

        var stopped = 0
        val failed = mutableListOf<String>()
        val stoppedPackages = mutableListOf<String>()
        if (full) {
            for (pkg in BoostPolicy.killCandidates(apps, ctx)) {
                if (privileged.exec("am force-stop $pkg").ok) {
                    stopped++
                    stoppedPackages += pkg
                } else {
                    failed += pkg
                }
            }
            if (privileged.isAvailable) {
                val targets = BoostPolicy.cacheTargets(apps, ctx.whitelist)
                val r = cacheCleaner.clearMany(targets, currentUser())
                // Android 8+ refuses per-app internal cache to non-system callers: clear what is allowed.
                if (r.perPackageUnsupported) cacheCleaner.clearExternal(targets, externalRoot)
            }
        } else {
            for (pkg in BoostPolicy.blindKillCandidates(apps, ctx)) {
                runCatching { killBackground(pkg) }.onSuccess { stopped++ }.onFailure { failed += pkg }
            }
        }

        delayMs(SETTLE_MS)
        return BoostReport(
            fullMode = full,
            stopped = stopped,
            failed = failed,
            freedRamKb = (readFreeRamKb() - ramBefore).coerceAtLeast(0),
            freedCacheBytes = (cacheBefore - totalCacheBytes()).coerceAtLeast(0),
            protectionUnavailable = full && !ctx.busyKnown,
            stoppedPackages = stoppedPackages,
        )
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
