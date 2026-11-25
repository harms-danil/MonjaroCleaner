package app.mcleaner.domain

import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote

data class CacheResult(val cleared: Int, val failed: List<String>, val perPackageUnsupported: Boolean)

data class ExternalCacheResult(val apps: Int, val bytes: Long)

class CacheCleaner(private val privileged: Privileged) {
    suspend fun clearOne(pkg: String, userId: Int): Boolean = privileged.deleteCache(pkg, userId)

    /** Per-package clear. Three refusals in a row before any success means the platform doesn't allow it. */
    suspend fun clearMany(pkgs: List<String>, userId: Int): CacheResult {
        var cleared = 0
        val failed = mutableListOf<String>()
        for ((i, pkg) in pkgs.withIndex()) {
            if (clearOne(pkg, userId)) {
                cleared++
            } else {
                failed += pkg
                if (cleared == 0 && failed.size >= GIVE_UP_AFTER) {
                    return CacheResult(0, failed + pkgs.drop(i + 1), perPackageUnsupported = true)
                }
            }
        }
        return CacheResult(cleared, failed, perPackageUnsupported = false)
    }

    /**
     * The part of the cache Android still lets us clear per app without system privileges:
     * `<root>/Android/data/<pkg>/cache`. The internal cache (/data/data/<pkg>/cache) needs
     * INTERNAL_DELETE_CACHE_FILES, which only firmware-signed apps have.
     */
    suspend fun clearExternal(pkgs: List<String>, root: String): ExternalCacheResult {
        if (pkgs.isEmpty() || !privileged.isAvailable) return ExternalCacheResult(0, 0)
        val dirs = pkgs.map { "$root/Android/data/$it/cache" }
        val du = privileged.exec("du -sk ${dirs.joinToString(" ") { shellQuote(it) }} 2>/dev/null").out
        val sizesKb = du.lines().mapNotNull { it.trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.toLongOrNull() }
            .map { (it - EMPTY_DIR_KB).coerceAtLeast(0) }
        privileged.exec(dirs.joinToString("; ") { d -> "[ -d ${shellQuote(d)} ] && find ${shellQuote(d)} -mindepth 1 -delete" })
        return ExternalCacheResult(apps = sizesKb.count { it > 0 }, bytes = sizesKb.sum() * 1024)
    }

    /** Clears every app's cache, including navigation. Only on explicit user confirmation. */
    suspend fun trimAll(): Boolean = privileged.exec("pm trim-caches 999G").ok

    private companion object {
        const val GIVE_UP_AFTER = 3
        const val EMPTY_DIR_KB = 4L
    }
}
