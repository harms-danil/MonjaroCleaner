package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostContext
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BoostReport(
    val fullMode: Boolean,
    val stopped: Int,
    val failed: List<String>,
    val freedRamKb: Long,
    val freedCacheBytes: Long,
    val protectionUnavailable: Boolean = false,
    /** Force-stopped packages (full mode), for the history. */
    val stoppedPackages: List<String> = emptyList(),
    /** Apps that kept running and were asked to free memory («мягкое ускорение»). */
    val trimmed: Int = 0,
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
    /** [clearCache]: false for unattended (scheduled) boosts: caches are cleared only when the user asks. */
    suspend fun run(clearCache: Boolean = true): BoostReport {
        val ramBefore = readFreeRamKb()
        val cacheBefore = totalCacheBytes()
        val apps = loadApps()
        val ctx = buildContext()
        val full = privileged.isAvailable

        var stopped = 0
        var trimmed = 0
        val failed = mutableListOf<String>()
        val stoppedPackages = mutableListOf<String>()
        if (full) {
            for (pkg in BoostPolicy.killCandidates(apps, ctx)) {
                if (privileged.exec("am force-stop ${shellQuote(pkg)}").ok) {
                    stopped++
                    stoppedPackages += pkg
                } else {
                    failed += pkg
                }
            }
            if (ctx.busyKnown) {
                for ((pkg, level) in BoostPolicy.trimTargets(apps, ctx)) {
                    if (privileged.exec("am send-trim-memory ${shellQuote(pkg)} $level").ok) trimmed++
                }
            }
            // Force-stop leaves the cards in the system's running-apps screen: drop them too.
            if (stoppedPackages.isNotEmpty()) privileged.removeTasks(stoppedPackages, currentUser())
            if (clearCache && privileged.isAvailable) {
                val inUse = ctx.busyPackages + setOfNotNull(ctx.foregroundPackage, ctx.launcherPackage, ctx.imePackage)
                val targets = BoostPolicy.cacheTargets(apps, ctx.whitelist, inUse)
                val r = cacheCleaner.clearMany(targets, currentUser())
                // Android 8+ refuses per-app internal cache to non-system callers: clear what is allowed.
                if (r.perPackageUnsupported) cacheCleaner.clearExternal(targets, externalRoot)
            }
        } else {
            // killBackgroundProcesses is a blocking binder call per app: keep it off the caller's (Main) thread.
            withContext(Dispatchers.IO) {
                for (pkg in BoostPolicy.blindKillCandidates(apps, ctx)) {
                    runCatching { killBackground(pkg) }.onSuccess { stopped++ }.onFailure { failed += pkg }
                }
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
            trimmed = trimmed,
        )
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
