package app.mcleaner.domain

import app.mcleaner.privileged.Privileged

data class CacheResult(val cleared: Int, val failed: List<String>, val perPackageUnsupported: Boolean)

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

    /** Clears every app's cache, including navigation. Only on explicit user confirmation. */
    suspend fun trimAll(): Boolean = privileged.exec("pm trim-caches 999G").ok

    private companion object {
        const val GIVE_UP_AFTER = 3
    }
}
