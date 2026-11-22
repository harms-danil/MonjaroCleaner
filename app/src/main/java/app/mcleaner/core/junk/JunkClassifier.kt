package app.mcleaner.core.junk

enum class JunkCategory(val title: String) {
    APK("Установочные файлы APK"),
    LOGS("Логи"),
    TEMP("Временные файлы"),
    THUMBNAILS("Миниатюры"),
    EMPTY_DIRS("Пустые папки"),
}

data class JunkItem(val path: String, val sizeBytes: Long, val category: JunkCategory)

object JunkClassifier {
    private val logSuffix = Regex("""\.log(\.\d+)?$""")

    fun classify(path: String, isDirectory: Boolean, isEmptyDir: Boolean): JunkCategory? {
        val p = path.replace('\\', '/').trimEnd('/')
        if ("/Android/obb/" in "$p/") return null
        if (isDirectory) {
            // Android/, Android/data, Android/media look empty to apps on Android 11 but are system dirs.
            if (p.endsWith("/Android") || p.substringBeforeLast('/').endsWith("/Android")) return null
            return if (isEmptyDir) JunkCategory.EMPTY_DIRS else null
        }
        val name = p.substringAfterLast('/').lowercase()
        val appLogs = "/logs/" in p.lowercase() && ("/Android/data/" in p || "/Android/media/" in p)
        return when {
            name.endsWith(".apk") || name.endsWith(".apks") -> JunkCategory.APK
            "/.thumbnails/" in p -> JunkCategory.THUMBNAILS
            logSuffix.containsMatchIn(name) || appLogs -> JunkCategory.LOGS
            name.endsWith(".tmp") || name.endsWith(".temp") || name.startsWith("~$") || "/.tmp/" in p -> JunkCategory.TEMP
            else -> null
        }
    }
}
