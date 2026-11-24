package app.mcleaner.core.update

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class VersionTest {
    @Test
    fun `newer only when strictly greater, numerically`() {
        assertTrue(Version.isNewer("v1.0.2", "1.0.1"))
        assertTrue(Version.isNewer("1.1.0", "1.0.9"))
        assertTrue(Version.isNewer("v1.0.10", "1.0.9"))
        assertTrue(Version.isNewer("2.0", "1.9.9"))
        assertFalse(Version.isNewer("v1.0.1", "1.0.1"))
        assertFalse(Version.isNewer("1.0", "1.0.0"))
        assertFalse(Version.isNewer("v1.0.0", "1.0.1"))
    }

    @Test
    fun `garbage is never newer`() {
        assertFalse(Version.isNewer("latest", "1.0.0"))
        assertFalse(Version.isNewer("", "1.0.0"))
        assertNull(Version.parse("v1.x"))
        assertEquals(listOf(1, 2, 3), Version.parse("v1.2.3"))
    }
}

class ReleaseParserTest {
    private val body = """
        MCleaner 1.1.0 — очистка памяти и кэша для магнитолы Geely Monjaro (ECARX, Android 11).

        ### Добавлено
        - Обновление из приложения.

        **Установка:** скачайте `MCleaner-1.1.0.apk` и установите.

        - SHA-256: `ff652b368418ad6ab1e3685c76b2ea8576590460b0073c6cd456e684f98fd151`
    """.trimIndent()

    private fun json(assets: String, tag: String = "v1.1.0", draft: Boolean = false) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": false,
         "body": ${org.json.JSONObject.quote(body)},
         "assets": [$assets]}
    """.trimIndent()

    private val apkAsset = """{"name": "MCleaner-1.1.0.apk", "size": 3014120,
        "browser_download_url": "https://github.com/harms-danil/MonjaroCleaner/releases/download/v1.1.0/MCleaner-1.1.0.apk"}"""

    @Test
    fun `parses version, apk asset, changes and checksum`() {
        val r = ReleaseParser.parse(json("""{"name": "notes.txt", "size": 1, "browser_download_url": "https://x/notes.txt"}, $apkAsset"""))!!
        assertEquals("1.1.0", r.version)
        assertEquals("https://github.com/harms-danil/MonjaroCleaner/releases/download/v1.1.0/MCleaner-1.1.0.apk", r.apkUrl)
        assertEquals(3014120L, r.apkSize)
        assertEquals("### Добавлено\n- Обновление из приложения.", r.changes)
        assertEquals("ff652b368418ad6ab1e3685c76b2ea8576590460b0073c6cd456e684f98fd151", r.sha256)
    }

    @Test
    fun `release without apk, drafts and broken json give null`() {
        assertNull(ReleaseParser.parse(json("")))
        assertNull(ReleaseParser.parse(json(apkAsset, draft = true)))
        assertNull(ReleaseParser.parse("{not json"))
        assertNull(ReleaseParser.parse(json(apkAsset, tag = "nightly")))
    }

    @Test
    fun `apk must come from github`() {
        val evil = """{"name": "MCleaner-1.1.0.apk", "size": 10, "browser_download_url": "http://evil.example/MCleaner-1.1.0.apk"}"""
        assertNull(ReleaseParser.parse(json(evil)))
    }
}

class RetryTest {
    @Test
    fun `retries network errors up to the limit with a pause between attempts`() = runTest {
        var calls = 0
        try {
            Retry.retrying(attempts = 5, pauseMs = 15_000) {
                calls++
                throw IOException("no network")
            }
            fail("must throw")
        } catch (_: IOException) {
        }
        assertEquals(5, calls)
        assertEquals(4 * 15_000L, currentTime)
    }

    @Test
    fun `stops at the first success`() = runTest {
        var calls = 0
        val r = Retry.retrying(attempts = 5, pauseMs = 15_000) {
            calls++
            if (calls < 3) throw IOException("flaky") else "ok"
        }
        assertEquals("ok", r)
        assertEquals(3, calls)
    }

    @Test
    fun `server errors are not retried`() = runTest {
        var calls = 0
        try {
            Retry.retrying(attempts = 5, pauseMs = 15_000) {
                calls++
                throw HttpStatusException(403)
            }
            fail("must throw")
        } catch (_: HttpStatusException) {
        }
        assertEquals(1, calls)
    }
}
