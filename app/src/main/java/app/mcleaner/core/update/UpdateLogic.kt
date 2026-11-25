package app.mcleaner.core.update

import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

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
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val d = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
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
            apk == null || !url.startsWith(DOWNLOAD_PREFIX) -> null
            else -> {
                val body = o.optString("body")
                ReleaseInfo(version, changesFrom(body), url, apk.optLong("size"), sha.find(body)?.groupValues?.get(1)?.lowercase())
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
