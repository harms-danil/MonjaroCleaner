package app.mcleaner.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkTrustTest {
    private val a = byteArrayOf(1, 2, 3)
    private val b = byteArrayOf(9, 8, 7)

    @Test
    fun `same package and same signers is trusted`() {
        assertEquals(ApkVerdict.OK, ApkTrust.verdict("app.mcleaner", listOf(a), "app.mcleaner", listOf(byteArrayOf(1, 2, 3))))
        // Order does not matter, it is a set.
        assertEquals(ApkVerdict.OK, ApkTrust.verdict("app.mcleaner", listOf(b, a), "app.mcleaner", listOf(a, b)))
    }

    @Test
    fun `not an apk, other package or other key is refused`() {
        assertEquals(ApkVerdict.NOT_APK, ApkTrust.verdict(null, emptyList(), "app.mcleaner", listOf(a)))
        assertEquals(ApkVerdict.WRONG_PACKAGE, ApkTrust.verdict("evil.app", listOf(a), "app.mcleaner", listOf(a)))
        assertEquals(ApkVerdict.WRONG_SIGNER, ApkTrust.verdict("app.mcleaner", listOf(b), "app.mcleaner", listOf(a)))
        assertEquals(ApkVerdict.WRONG_SIGNER, ApkTrust.verdict("app.mcleaner", listOf(a, b), "app.mcleaner", listOf(a)))
    }

    @Test
    fun `no certificates on either side is never trusted`() {
        assertEquals(ApkVerdict.WRONG_SIGNER, ApkTrust.verdict("app.mcleaner", emptyList(), "app.mcleaner", emptyList()))
        assertEquals(ApkVerdict.WRONG_SIGNER, ApkTrust.verdict("app.mcleaner", listOf(a), "app.mcleaner", emptyList()))
    }

    @Test
    fun `hex of bytes`() {
        assertEquals("0102ff", ApkTrust.hex(byteArrayOf(1, 2, -1)))
    }
}

class DownloadUrlTest {
    private val ok = "https://github.com/$UPDATE_REPO/releases/download/v1.4.1/MCleaner-1.4.1.apk"

    @Test
    fun `release asset url is allowed`() {
        assertTrue(DownloadUrl.isAllowed(ok))
    }

    @Test
    fun `path tricks and foreign hosts are refused`() {
        assertFalse(DownloadUrl.isAllowed("https://github.com/$UPDATE_REPO/releases/download/../../../evil/x/releases/download/v1/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("https://github.com/$UPDATE_REPO/releases/download/v1/%2e%2e/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("https://github.com/$UPDATE_REPO/releases/download/./v1/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("http://github.com/$UPDATE_REPO/releases/download/v1/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("https://github.com.evil.example/$UPDATE_REPO/releases/download/v1/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("https://user@github.com/$UPDATE_REPO/releases/download/v1/MCleaner-1.apk"))
        assertFalse(DownloadUrl.isAllowed("not a url at all"))
        assertFalse(DownloadUrl.isAllowed(""))
    }

    @Test
    fun `size cap is 50 MB`() {
        assertEquals(50L * 1024 * 1024, DownloadUrl.MAX_APK_BYTES)
        assertFalse(DownloadUrl.tooBig(-1))
        assertFalse(DownloadUrl.tooBig(DownloadUrl.MAX_APK_BYTES))
        assertTrue(DownloadUrl.tooBig(DownloadUrl.MAX_APK_BYTES + 1))
    }
}

class ShellShaTest {
    private val h = "ff652b368418ad6ab1e3685c76b2ea8576590460b0073c6cd456e684f98fd151"

    @Test
    fun `reads the hash from sha256sum output`() {
        assertEquals(h, ShellSha.parse("$h  /data/local/tmp/mcleaner-update.apk\n"))
        assertEquals(h, ShellSha.parse(h.uppercase() + " x"))
    }

    @Test
    fun `missing tool or garbage gives null`() {
        assertNull(ShellSha.parse("sh: sha256sum: not found"))
        assertNull(ShellSha.parse(""))
        assertNull(ShellSha.parse("abc  file"))
    }
}

class InstallLogTest {
    @Test
    fun `pm output verdict`() {
        assertEquals(InstallOutcome.SUCCESS_PENDING, InstallLog.verdict("Success\n"))
        assertEquals(InstallOutcome.FAILED, InstallLog.verdict("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertEquals(InstallOutcome.FAILED, InstallLog.verdict("Error: java.lang.SecurityException"))
        assertNull(InstallLog.verdict(""))
        assertNull(InstallLog.verdict("\n"))
    }
}

class VersionCompareTest {
    @Test
    fun `numerically equal versions compare as equal`() {
        assertEquals(0, Version.compare("1.2", "1.2.0"))
        assertEquals(0, Version.compare("v1.2.0", "1.2"))
        assertTrue(Version.compare("1.10", "1.9") > 0)
        assertTrue(Version.compare("1.0.9", "1.0.10") < 0)
    }

    @Test
    fun `toDelete and merge tolerate numerically equal versions`() {
        // A comparator that never returns 0 breaks the sort contract on larger inputs.
        val many = (0 until 40).flatMap { listOf("1.$it", "1.$it.0") }
        assertEquals(10, ApkFiles.toDelete(many, keep = 70).size)
        val merged = VersionCatalog.merge(many.toSet(), emptyList(), current = "0.1")
        assertEquals(80, merged.size)
        assertEquals(0, Version.compare(merged[0].version, "1.39"))
    }
}

class ReleaseShaRequiredTest {
    @Test
    fun `release without SHA-256 in notes is unusable`() {
        val json = """{"tag_name":"v1.1.0","draft":false,"prerelease":false,"body":"no hash here",
            "assets":[{"name":"MCleaner-1.1.0.apk","size":5,"browser_download_url":"https://github.com/$UPDATE_REPO/releases/download/v1.1.0/MCleaner-1.1.0.apk"}]}"""
        assertNull(ReleaseParser.parse(json))
    }

    @Test
    fun `release with traversal in asset url is unusable`() {
        val json = """{"tag_name":"v1.1.0","draft":false,"prerelease":false,"body":"x\n- SHA-256: `${"a".repeat(64)}`",
            "assets":[{"name":"MCleaner-1.1.0.apk","size":5,"browser_download_url":"https://github.com/$UPDATE_REPO/releases/download/../../../o/r/releases/download/v1/MCleaner-1.1.0.apk"}]}"""
        assertNull(ReleaseParser.parse(json))
    }
}

class SelfUpdateFloorTest {
    @Test
    fun `versions below 1_2_2 are listed but not installable`() {
        val list = VersionCatalog.merge(setOf("1.2.1", "1.2.2", "1.1.0"), emptyList(), current = "1.4.1")
        val byV = list.associateBy { it.version }
        assertFalse(byV.getValue("1.2.1").installable)
        assertFalse(byV.getValue("1.1.0").installable)
        assertTrue(byV.getValue("1.2.2").installable)
        assertEquals("1.2.2", VersionCatalog.MIN_INSTALLABLE)
    }
}
