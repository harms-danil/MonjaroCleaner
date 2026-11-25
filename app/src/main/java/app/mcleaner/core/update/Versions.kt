package app.mcleaner.core.update

import app.mcleaner.privileged.shellQuote
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Downloaded APKs are stored as `MCleaner-<version>.apk`. */
object ApkFiles {
    private val name = Regex("""^MCleaner-(\d+(?:\.\d+)*)\.apk$""")

    fun versionOf(fileName: String): String? = name.find(fileName)?.groupValues?.get(1)

    fun nameOf(version: String) = "MCleaner-$version.apk"

    /** Versions to remove so that only the [keep] newest stay. */
    fun toDelete(versions: List<String>, keep: Int): List<String> =
        versions.sortedWith { a, b -> Version.compare(b, a) }.drop(keep)
}

data class VersionEntry(
    val version: String,
    /** Newer than the installed one: a normal update; otherwise a rollback. */
    val newer: Boolean,
    val onDevice: Boolean,
    /** Present when the version is published on GitHub. */
    val release: ReleaseInfo?,
    /** False below [VersionCatalog.MIN_INSTALLABLE]: such a version could never update itself again. */
    val installable: Boolean = true,
)

object VersionCatalog {
    /** First version that restores settings from the backup file after a reinstall. */
    const val RESTORE_SINCE = "1.2.1" // 1.2.0 on Android 9 could overwrite the backup with defaults

    fun restoresSettings(version: String) = !Version.isNewer(RESTORE_SINCE, version)

    /** Oldest version that can update itself on Android 9; older ones are listed but not offered. */
    const val MIN_INSTALLABLE = "1.2.2"

    fun merge(local: Set<String>, remote: List<ReleaseInfo>, current: String): List<VersionEntry> {
        val byVersion = remote.associateBy { it.version }
        return (local + byVersion.keys)
            .filter { Version.parse(it) != null && (Version.isNewer(it, current) || Version.isNewer(current, it)) }
            .map { VersionEntry(it, Version.isNewer(it, current), it in local, byVersion[it], !Version.isNewer(MIN_INSTALLABLE, it)) }
            .sortedWith { a, b -> Version.compare(b.version, a.version) }
    }
}

/** What survives a reinstall (`/sdcard/MCleaner/settings.json`) and what export/import carries. */
data class BackupData(
    val whitelist: Set<String>,
    val frozen: Set<String>,
    val autoBoost: Boolean,
    val stopGoogle: Boolean,
    /** 0 = off. Added in 1.4.0, older files read as off. */
    val scheduleMinutes: Int = 0,
    val lowMemoryAlert: Boolean = false,
    val lowMemoryPercent: Int = 15,
)

object SettingsBackup {
    private const val FORMAT = 1

    fun encode(d: BackupData): String = JSONObject()
        .put("format", FORMAT)
        .put("whitelist", JSONArray(d.whitelist.sorted()))
        .put("frozen", JSONArray(d.frozen.sorted()))
        .put("autoBoost", d.autoBoost)
        .put("stopGoogle", d.stopGoogle)
        .put("scheduleMinutes", d.scheduleMinutes)
        .put("lowMemoryAlert", d.lowMemoryAlert)
        .put("lowMemoryPercent", d.lowMemoryPercent)
        .toString(2)

    fun decode(text: String): BackupData? = try {
        val o = JSONObject(text)
        if (o.optInt("format") != FORMAT || !o.has("whitelist")) {
            null
        } else {
            BackupData(
                whitelist = o.strings("whitelist"),
                frozen = o.strings("frozen"),
                autoBoost = o.optBoolean("autoBoost", false),
                stopGoogle = o.optBoolean("stopGoogle", false),
                scheduleMinutes = o.optInt("scheduleMinutes", 0).coerceIn(0, 24 * 60),
                lowMemoryAlert = o.optBoolean("lowMemoryAlert", false),
                lowMemoryPercent = o.optInt("lowMemoryPercent", 15).coerceIn(5, 50),
            )
        }
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.strings(key: String): Set<String> {
        val a = optJSONArray(key) ?: return emptySet()
        return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }.toSet()
    }
}

/** Shell for a rollback on a non-debuggable build: uninstall, install the old APK, fall back to the current one. */
object RollbackScript {
    fun reinstallSteps(target: String, current: String, pkg: String, component: String): String =
        "sleep 2; pm uninstall ${shellQuote(pkg)}; " +
            "pm install -r ${shellQuote(target)} || pm install -r ${shellQuote(current)}; " +
            "am start -n ${shellQuote(component)}; rm -f ${shellQuote(target)} ${shellQuote(current)}"

    /**
     * Install [staged] over the current version and, on success, start MCleaner again: the install kills
     * MCleaner, so the restart must come from this separate shell. The pm output lands in [result],
     * which MCleaner polls to notice a failure while it is still alive.
     */
    fun installAndRestart(staged: String, result: String, component: String, downgrade: Boolean): String =
        "pm install -r ${if (downgrade) "-d " else ""}${shellQuote(staged)} > ${shellQuote(result)} 2>&1; " +
            "if grep -q Success ${shellQuote(result)}; then am start -n ${shellQuote(component)}; rm -f ${shellQuote(staged)}; fi"

    /** Runs [script] in its own session so it outlives MCleaner (which the script uninstalls). */
    fun detached(script: String): String = "setsid sh -c ${shellQuote(script)} </dev/null >/dev/null 2>&1 &"
}

fun ReleaseParser.parseList(json: String): List<ReleaseInfo> = try {
    val a = JSONArray(json)
    (0 until a.length()).mapNotNull { parse(a.getJSONObject(it).toString()) }
} catch (_: JSONException) {
    emptyList()
}
