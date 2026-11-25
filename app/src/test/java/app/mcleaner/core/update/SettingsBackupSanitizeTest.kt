package app.mcleaner.core.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** settings.json lives on shared storage: any app with storage permission can write it. */
class SettingsBackupSanitizeTest {
    private fun file(whitelist: List<String>, frozen: List<String> = emptyList(), extra: JSONObject.() -> Unit = {}) =
        JSONObject().put("format", 1).put("whitelist", JSONArray(whitelist)).put("frozen", JSONArray(frozen)).apply(extra).toString()

    @Test
    fun `drops entries that are not package names`() {
        val bad = listOf("a", "1a.b", "a..b", ".a.b", "a.b.", "a b.c", "a.b;reboot", "a.b\n", "../x.y", "a.b-c", "a.б")
        val good = listOf("com.spotify.music", "ru.yandex.yandexnavi", "a.b", "A1_b.c_2.D")
        val d = SettingsBackup.decode(file(bad + good, bad + listOf("com.android.vending")))!!
        assertEquals(good.toSet(), d.whitelist)
        assertEquals(setOf("com.android.vending"), d.frozen)
    }

    @Test
    fun `caps list sizes`() {
        val many = (1..2000).map { "p$it.app" }
        val d = SettingsBackup.decode(file(many, many))!!
        assertEquals(SettingsBackup.MAX_PACKAGES, d.whitelist.size)
        assertEquals(SettingsBackup.MAX_PACKAGES, d.frozen.size)
    }

    @Test
    fun `clamps numbers`() {
        val d = SettingsBackup.decode(file(listOf("a.b")) { put("scheduleMinutes", -5); put("lowMemoryPercent", 999) })!!
        assertEquals(0, d.scheduleMinutes)
        assertEquals(50, d.lowMemoryPercent)
        val e = SettingsBackup.decode(file(listOf("a.b")) { put("scheduleMinutes", Int.MAX_VALUE); put("lowMemoryPercent", -1) })!!
        assertEquals(24 * 60, e.scheduleMinutes)
        assertEquals(5, e.lowMemoryPercent)
    }
}
