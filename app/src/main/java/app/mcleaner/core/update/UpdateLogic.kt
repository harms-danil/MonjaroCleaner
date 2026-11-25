package app.mcleaner.core.update

import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

const val UPDATE_REPO = "harms-danil/MonjaroCleaner"
const val LATEST_RELEASE_URL = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"
private const val DOWNLOAD_PREFIX = "https://github.com/$UPDATE_REPO/releases/download/"

object Version {
    /** "v1.2.3" / "1.2" → [1, 2, 3] / [1, 2]; anything else → null. */
    fun parse(s: String): List<Int>? {
        val parts = s.trim().removePrefix("v").split('.')
        if (parts.isEmpty() || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        return parts.map { it.toInt() }
    }

    fun isNewer(remote: String, current: String): Boolean {
        if (parse(remote) == null || parse(current) == null) return false
        return compare(remote, current) > 0
    }

    /** Numeric order, "1.2" == "1.2.0"; unparseable versions sort below everything. */
    fun compare(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        if (x == null || y == null) return (if (x == null) 0 else 1) - (if (y == null) 0 else 1)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}

data class ReleaseInfo(
    val version: String,
    val changes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
)

object ReleaseParser {
    private val apkName = Regex("""^MCleaner-.*\.apk$""")
    private val sha = Regex("""SHA-256:\s*`?([0-9a-fA-F]{64})`?""")

    /** GitHub "latest release" JSON → info, or null if it is not a usable MCleaner release. */
    fun parse(json: String): ReleaseInfo? = try {
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v")
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { apkName.matches(it.optString("name")) }
        val url = apk?.optString("browser_download_url").orEmpty()
        when {
            o.optBoolean("draft") || o.optBoolean("prerelease") -> null
            Version.parse(version) == null -> null
            apk == null || !DownloadUrl.isAllowed(url) -> null
            else -> {
                val body = o.optString("body")
                // Every published release carries its SHA-256; without it the file cannot be checked.
                sha.find(body)?.groupValues?.get(1)?.lowercase()?.let { ReleaseInfo(version, changesFrom(body), url, apk.optLong("size"), it) }
            }
        }
    } catch (_: JSONException) {
        null
    }

    /** Release notes = headline, CHANGELOG section, then "**Установка:**…". Keep the section only. */
    // (The notes are Russian-only, like the CHANGELOG.)
    fun changesFrom(body: String): String {
        val lines = body.replace("\r", "").lines()
        val section = lines.drop(1).takeWhile { !it.trimStart().startsWith("**Установка:**") }
        return section.joinToString("\n").trim()
    }
}

class HttpStatusException(val code: Int) : IOException("HTTP $code")

object Retry {
    /** Retries network failures only; an HTTP error answer from the server is final. */
    suspend fun <T> retrying(attempts: Int, pauseMs: Long, block: suspend () -> T): T {
        var last: IOException? = null
        repeat(attempts) { i ->
            try {
                return block()
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                last = e
                if (i < attempts - 1) delay(pauseMs)
            }
        }
        throw last ?: IOException("no attempts")
    }
}

/** Where an APK may be downloaded from, and how big it may be. */
object DownloadUrl {
    const val MAX_APK_BYTES = 50L * 1024 * 1024

    /** https, github.com, our repo's release assets only; no dot segments, encoded or not. */
    fun isAllowed(url: String): Boolean = runCatching {
        val u = URI(url)
        val decoded = URLDecoder.decode(u.rawPath.orEmpty(), "UTF-8")
        u.normalize().toString() == url && url.startsWith(DOWNLOAD_PREFIX) && u.userInfo == null &&
            u.rawQuery == null && u.rawFragment == null && "/." !in decoded && "\\" !in decoded
    }.getOrDefault(false)

    fun tooBig(bytes: Long) = bytes > MAX_APK_BYTES
}

enum class ApkVerdict { OK, NOT_APK, WRONG_PACKAGE, WRONG_SIGNER }

/** An APK may be installed only if it is MCleaner signed with exactly the installed app's certificates. */
object ApkTrust {
    fun verdict(apkPackage: String?, apkCerts: List<ByteArray>, ownPackage: String, ownCerts: List<ByteArray>): ApkVerdict {
        val theirs = apkCerts.map(::hex).toSet()
        val ours = ownCerts.map(::hex).toSet()
        return when {
            apkPackage == null -> ApkVerdict.NOT_APK
            apkPackage != ownPackage -> ApkVerdict.WRONG_PACKAGE
            ours.isEmpty() || theirs != ours -> ApkVerdict.WRONG_SIGNER
            else -> ApkVerdict.OK
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/** Output of `sha256sum <file>` -> lower-case hash, or null if the tool failed. */
object ShellSha {
    private val line = Regex("""^([0-9a-fA-F]{64})(\s|$)""")

    fun parse(out: String): String? = line.find(out.trim())?.groupValues?.get(1)?.lowercase()
}

/** Result of the detached `pm install`. */
enum class InstallOutcome { SUCCESS_PENDING, FAILED, TIMEOUT }

object InstallLog {
    /** What `pm install` wrote so far: success, failure, or null while it is still running. */
    fun verdict(out: String): InstallOutcome? = when {
        "Failure" in out || "Error" in out || "Exception" in out -> InstallOutcome.FAILED
        "Success" in out -> InstallOutcome.SUCCESS_PENDING
        else -> null
    }
}
