package app.mcleaner.domain

import android.os.Environment
import app.mcleaner.core.junk.AppCacheFilter
import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.core.junk.JunkItem
import app.mcleaner.core.junk.JunkScanner
import app.mcleaner.privileged.Privileged
import app.mcleaner.privileged.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JunkCleaner(
    private val privileged: Privileged,
    private val scanner: JunkScanner,
    private val root: () -> File = { Environment.getExternalStorageDirectory() },
) {

    /**
     * [whitelist]: nothing under `Android/data|media/<pkg>` of whitelisted and car packages is offered
     * (offline maps, tracks, settings), neither found files nor app caches.
     */
    suspend fun scan(whitelist: Set<String>): List<JunkItem> = withContext(Dispatchers.IO) {
        val root = root()
        val found = scanner.scan(root).filterTo(mutableListOf()) { !AppCacheFilter.protectedOwner(it.path, whitelist) }
        if (privileged.isAvailable) {
            val seen = found.mapTo(HashSet()) { it.path }
            // An empty cache dir is already listed as an empty folder: list each path once.
            found += appDataCaches(root).filter { AppCacheFilter.allowed(it.path, whitelist) && it.path !in seen }
        }
        found
    }

    /**
     * Returns how many items were removed. Each kind gets the narrowest command that does the job:
     * empty folders `rmdir` (fails unless empty), files `rm -f`; only an app's `Android/data/<pkg>/cache`
     * folder is removed recursively. `Android/data` is hidden from apps on Android 11, so it goes via shell.
     */
    suspend fun delete(items: List<JunkItem>): Int = withContext(Dispatchers.IO) {
        items.count { deleteOne(it) }
    }

    private suspend fun deleteOne(item: JunkItem): Boolean {
        val path = item.path
        val file = File(path)
        val viaShell = "/Android/data/" in path && privileged.isAvailable
        if (item.category == JunkCategory.EMPTY_DIRS) {
            return if (viaShell) {
                privileged.exec("rmdir ${shellQuote(path)}").ok
            } else {
                file.isDirectory && file.listFiles()?.isEmpty() == true && file.delete()
            }
        }
        if (AppCacheFilter.packageOf(path) != null) {
            // Re-checked here: a recursive delete only ever hits a real app's cache folder.
            return privileged.isAvailable && AppCacheFilter.allowed(path, emptySet()) &&
                privileged.exec("rm -rf ${shellQuote(path)}").ok
        }
        return if (viaShell) privileged.exec("rm -f ${shellQuote(path)}").ok else file.isFile && file.delete()
    }

    /** `Android/data/<pkg>/cache` dirs are hidden from apps on Android 11, but visible to shell. */
    private suspend fun appDataCaches(root: File): List<JunkItem> {
        val base = File(root, "Android/data").path
        val r = privileged.exec("du -s -k ${shellQuote(base)}/*/cache 2>/dev/null")
        return r.out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            val kb = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val path = parts.getOrNull(1) ?: return@mapNotNull null
            if (kb <= 0) null else JunkItem(path, kb * 1024, JunkCategory.TEMP)
        }
    }
}
