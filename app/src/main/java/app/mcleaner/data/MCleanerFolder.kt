package app.mcleaner.data

import android.content.Context
import android.os.Environment
import app.mcleaner.core.update.BackupData
import app.mcleaner.core.update.SettingsBackup
import java.io.File

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

    /** Downloaded versions, in app-private storage: no other app can plant or swap an APK there. */
    fun updatesDir(): File = File(context.filesDir, "updates")

    private val backupFile: File get() = File(shared, "settings.json")

    /** null = no access (unknown); Result(null) = access, but no usable backup. */
    fun readBackup(): Result<BackupData?>? {
        if (!available) return null
        val f = backupFile
        return Result.success(if (f.isFile) runCatching { SettingsBackup.decode(f.readText()) }.getOrNull() else null)
    }

    /** Fallback export when there is no system file picker. Returns the path, or null without access. */
    fun writeExport(text: String): String? {
        if (!available) return null
        return runCatching {
            val dir = File(shared, "export").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())
            File(dir, "mcleaner-settings-$stamp.json").apply { writeText(text) }.path
        }.getOrNull()
    }

    fun writeBackup(data: BackupData): Boolean {
        if (!available) return false
        return runCatching {
            shared.mkdirs()
            val tmp = File(shared, "settings.json.tmp")
            tmp.writeText(SettingsBackup.encode(data))
            tmp.renameTo(backupFile)
        }.getOrDefault(false)
    }
}
