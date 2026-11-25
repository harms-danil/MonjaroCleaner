package app.mcleaner.data

import android.content.Context
import android.os.Environment
import app.mcleaner.core.update.BackupData
import app.mcleaner.core.update.SettingsBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * `/sdcard/MCleaner` — lives outside `Android/data`, so it survives uninstalling MCleaner.
 * Needs all-files access; without it, APKs fall back to the app's own folder and no settings backup is kept.
 */
class MCleanerFolder(private val context: Context, private val hasAccess: () -> Boolean) {
    private val shared: File get() = File(Environment.getExternalStorageDirectory(), "MCleaner")

    /**
     * Permission alone is not proof (Android 9 with only WRITE granted reports access but denies it):
     * check that the folder can really be created and written.
     */
    val available: Boolean get() = hasAccess() && runCatching {
        shared.mkdirs()
        shared.isDirectory && shared.canWrite()
    }.getOrDefault(false)

    /** Downloaded versions. Shell (Shizuku) can read both locations. */
    fun updatesDir(): File =
        if (available) File(shared, "updates") else File(context.getExternalFilesDir(null) ?: context.cacheDir, "update")

    private val backupFile: File get() = File(shared, "settings.json")

    /** The app collector, the first-run restore and the Shizuku-granted resync can all write at once. */
    private val backupLock = Mutex()

    /**
     * null = no access, or a broken backup that could not be moved aside (unknown: do not initialize, do not write).
     * Result(null) = access, but no usable backup. A file that exists but cannot be decoded (truncated, or a newer
     * format left behind by a rollback) is renamed to `settings.json.bad-<time>` first, so it is never overwritten.
     */
    suspend fun readBackup(): Result<BackupData?>? = backupLock.withLock {
        withContext(Dispatchers.IO) {
            if (!available) return@withContext null
            if (!backupFile.exists()) return@withContext Result.success(null)
            val data = decodeBackupFile()
            if (data != null) Result.success(data) else if (moveAsideBroken()) Result.success(null) else null
        }
    }

    /** Fallback export when there is no system file picker. Returns the path, or null without access. */
    fun writeExport(text: String): String? {
        if (!available) return null
        return runCatching {
            val dir = File(shared, "export").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
            val f = File(dir, "mcleaner-settings-$stamp.json")
            if (writeDurably(f, text)) f.path else null
        }.getOrNull()
    }

    /**
     * Rewrites the backup with [snapshot], taken under the lock so that concurrent writers cannot
     * put an older state over a newer one. Never replaces a file it cannot read (see [readBackup]).
     */
    suspend fun writeBackup(snapshot: suspend () -> BackupData): Boolean = backupLock.withLock {
        val data = snapshot()
        withContext(Dispatchers.IO) {
            if (!available) return@withContext false
            runCatching {
                if (backupFile.exists() && decodeBackupFile() == null && !moveAsideBroken()) return@runCatching false
                writeDurably(backupFile, SettingsBackup.encode(data))
            }.getOrDefault(false)
        }
    }

    private fun decodeBackupFile(): BackupData? = runCatching {
        val f = backupFile
        if (!f.isFile || f.length() > MAX_BACKUP_BYTES) null else SettingsBackup.decode(f.readText())
    }.getOrNull()

    private fun moveAsideBroken(): Boolean = runCatching {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        backupFile.renameTo(File(shared, "settings.json.bad-$stamp"))
    }.getOrDefault(false)

    /** tmp + fsync + rename: a power cut (engine start) leaves either the old file or the new one, never half. */
    private fun writeDurably(target: File, text: String): Boolean {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.flush()
            out.fd.sync()
        }
        if (tmp.renameTo(target)) return true
        tmp.delete()
        return false
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 1L shl 20
    }
}
