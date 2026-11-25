package app.mcleaner.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFilesTest {
    @Test
    fun `version is taken from the file name`() {
        assertEquals("1.2.0", ApkFiles.versionOf("MCleaner-1.2.0.apk"))
        assertNull(ApkFiles.versionOf("MCleaner-1.2.0.apk.part"))
        assertNull(ApkFiles.versionOf("other.apk"))
        assertEquals("MCleaner-1.2.0.apk", ApkFiles.nameOf("1.2.0"))
    }

    @Test
    fun `keeps the five newest versions, numerically`() {
        val have = listOf("1.0.0", "1.0.10", "1.0.2", "1.1.0", "1.0.9", "1.2.0", "0.9")
        // Newest five: 1.2.0, 1.1.0, 1.0.10, 1.0.9, 1.0.2 (1.0.10 > 1.0.9 numerically).
        assertEquals(setOf("1.0.0", "0.9"), ApkFiles.toDelete(have, keep = 5).toSet())
        assertTrue(ApkFiles.toDelete(listOf("1.0.0", "1.1.0"), keep = 5).isEmpty())
    }
}

class VersionCatalogTest {
    private fun rel(v: String) = ReleaseInfo(v, "", "https://github.com/$UPDATE_REPO/releases/download/v$v/MCleaner-$v.apk", 1, null)

    @Test
    fun `merges device and github versions, hides current, newest first`() {
        val list = VersionCatalog.merge(
            local = setOf("1.0.0", "1.1.0", "1.2.0"),
            remote = listOf(rel("1.3.0"), rel("1.2.0"), rel("1.0.1")),
            current = "1.2.0",
        )
        assertEquals(listOf("1.3.0", "1.1.0", "1.0.1", "1.0.0"), list.map { it.version })
        val v130 = list.first()
        assertTrue(v130.newer && !v130.onDevice && v130.release != null)
        val v110 = list[1]
        assertFalse(v110.newer)
        assertTrue(v110.onDevice)
        assertNull(v110.release)
    }

    @Test
    fun `restore support starts at 1_2_1`() {
        assertTrue(VersionCatalog.restoresSettings("1.2.1"))
        assertFalse(VersionCatalog.restoresSettings("1.2.0")) // could overwrite the backup on Android 9
        assertTrue(VersionCatalog.restoresSettings("1.10.0"))
        assertFalse(VersionCatalog.restoresSettings("1.1.0"))
    }
}

class ReleaseListTest {
    @Test
    fun `parses all usable releases from the list endpoint`() {
        val sha = "\\n- SHA-256: `${"a".repeat(64)}`"
        val json = """[
            {"tag_name":"v1.1.0","draft":false,"prerelease":false,"body":"x$sha",
             "assets":[{"name":"MCleaner-1.1.0.apk","size":5,"browser_download_url":"https://github.com/$UPDATE_REPO/releases/download/v1.1.0/MCleaner-1.1.0.apk"}]},
            {"tag_name":"v1.0.5","draft":true,"prerelease":false,"body":"x$sha",
             "assets":[{"name":"MCleaner-1.0.5.apk","size":5,"browser_download_url":"https://github.com/$UPDATE_REPO/releases/download/v1.0.5/MCleaner-1.0.5.apk"}]},
            {"tag_name":"v1.0.0","draft":false,"prerelease":false,"body":"x$sha",
             "assets":[{"name":"MCleaner-1.0.0.apk","size":7,"browser_download_url":"https://github.com/$UPDATE_REPO/releases/download/v1.0.0/MCleaner-1.0.0.apk"}]}
        ]"""
        assertEquals(listOf("1.1.0", "1.0.0"), ReleaseParser.parseList(json).map { it.version })
        assertTrue(ReleaseParser.parseList("oops").isEmpty())
    }
}

class SettingsBackupTest {
    @Test
    fun `round trip`() {
        val data = BackupData(whitelist = setOf("a.b", "c.d"), frozen = setOf("x.y"), autoBoost = true, stopGoogle = false)
        assertEquals(data, SettingsBackup.decode(SettingsBackup.encode(data)))
    }

    @Test
    fun `broken or foreign file is ignored`() {
        assertNull(SettingsBackup.decode("not json"))
        assertNull(SettingsBackup.decode("""{"hello": 1}"""))
    }

    @Test
    fun `missing optional fields fall back to off`() {
        val d = SettingsBackup.decode("""{"format":1,"whitelist":["a.b"]}""")!!
        assertEquals(setOf("a.b"), d.whitelist)
        assertEquals(emptySet<String>(), d.frozen)
        assertFalse(d.autoBoost)
        // Fields added in 1.4.0 default to "off" / standard threshold for older files.
        assertEquals(0, d.scheduleMinutes)
        assertFalse(d.lowMemoryAlert)
        assertEquals(15, d.lowMemoryPercent)
        // Added in 1.6.0: background work is on unless a file says otherwise.
        assertTrue(d.keepRunning)
        // Added in 1.7.0.
        assertTrue(d.gentleTrim)
        assertEquals(emptySet<String>(), d.noBackground)
    }

    @Test
    fun `1_7_0 fields survive a round trip`() {
        val data = BackupData(setOf("a.b"), emptySet(), autoBoost = false, stopGoogle = false, gentleTrim = false, noBackground = setOf("com.huawei.hwid"), weeklyMaintenance = true)
        assertEquals(data, SettingsBackup.decode(SettingsBackup.encode(data)))
    }

    @Test
    fun `background work setting survives a round trip`() {
        val data = BackupData(setOf("a.b"), emptySet(), autoBoost = false, stopGoogle = false, keepRunning = false)
        assertEquals(data, SettingsBackup.decode(SettingsBackup.encode(data)))
    }

    @Test
    fun `schedule and memory alert survive a round trip`() {
        val data = BackupData(setOf("a.b"), emptySet(), autoBoost = false, stopGoogle = true, scheduleMinutes = 30, lowMemoryAlert = true, lowMemoryPercent = 20)
        assertEquals(data, SettingsBackup.decode(SettingsBackup.encode(data)))
    }
}

class RollbackScriptTest {
    @Test
    fun `reinstall script puts the current version back if the old one fails`() {
        val s = RollbackScript.reinstallSteps(
            target = "/data/local/tmp/mcleaner-target.apk",
            current = "/data/local/tmp/mcleaner-current.apk",
            pkg = "app.mcleaner",
            component = "app.mcleaner/.MainActivity",
        )
        val order = listOf("pm uninstall 'app.mcleaner'", "pm install -r '/data/local/tmp/mcleaner-target.apk'", "pm install -r '/data/local/tmp/mcleaner-current.apk'", "am start -n 'app.mcleaner/.MainActivity'")
        order.forEach { assertTrue("missing: $it\n$s", it in s) }
        assertTrue(order.map { s.indexOf(it) }.zipWithNext().all { (a, b) -> a < b })
        // Fallback install runs only when the target install failed.
        assertTrue("|| pm install -r '/data/local/tmp/mcleaner-current.apk'" in s)
    }

    @Test
    fun `detached wrapper survives the caller and keeps the inner script intact`() {
        val w = RollbackScript.detached("echo 'hi'; sleep 1")
        assertTrue(w.startsWith("setsid sh -c "))
        assertTrue(w.trimEnd().endsWith("&"))
        assertTrue("'echo '\\''hi'\\''; sleep 1'" in w)
    }

    @Test
    fun `install script restarts MCleaner only after a successful install`() {
        val s = RollbackScript.installAndRestart("/data/local/tmp/u.apk", "/data/local/tmp/r.txt", "app.mcleaner/.MainActivity", downgrade = false)
        assertTrue(s.startsWith("pm install -r '/data/local/tmp/u.apk' > '/data/local/tmp/r.txt' 2>&1;"))
        assertTrue("if grep -q Success '/data/local/tmp/r.txt'; then am start -n 'app.mcleaner/.MainActivity'" in s)
        assertFalse(" -d " in s)
    }

    @Test
    fun `downgrade install adds -d`() {
        assertTrue(RollbackScript.installAndRestart("/t/u.apk", "/t/r", "a/.B", downgrade = true).startsWith("pm install -r -d '/t/u.apk'"))
    }
}
