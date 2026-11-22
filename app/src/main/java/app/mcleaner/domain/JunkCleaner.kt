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

class JunkCleaner(private val privileged: Privileged, private val scanner: JunkScanner) {

    /** [whitelist]: app caches of whitelisted and car packages are never offered (offline maps, tracks). */
    suspend fun scan(whitelist: Set<String>): List<JunkItem> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val found = scanner.scan(root).toMutableList()
        if (privileged.isAvailable) found += appDataCaches(root).filter { AppCacheFilter.allowed(it.path, whitelist) }
        found
    }

    /** Returns how many items were removed. */
    suspend fun delete(items: List<JunkItem>): Int = withContext(Dispatchers.IO) {
        items.count { item ->
            val file = File(item.path)
            val viaShell = "/Android/data/" in item.path && privileged.isAvailable
            when {
                viaShell -> privileged.exec("rm -rf ${shellQuote(item.path)}").ok
                file.isDirectory -> file.listFiles().isNullOrEmpty() && file.delete()
                else -> file.delete()
            }
        }
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
