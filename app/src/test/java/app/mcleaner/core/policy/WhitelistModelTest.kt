package app.mcleaner.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistModelTest {
    private val installed = listOf(
        InstalledApp("ru.yandex.yandexnavi", "Навигатор", isSystem = false),
        InstalledApp("com.spotify.music", "Spotify", isSystem = false),
        InstalledApp("com.ecarx.radio", "Радио", isSystem = true),
        InstalledApp("org.telegram.messenger", "Telegram", isSystem = false),
        InstalledApp("app.mcleaner", "MCleaner", isSystem = false),
    )

    @Test
    fun `entries are sorted by label and keep packages that are not installed`() {
        val entries = WhitelistModel.entries(setOf("com.spotify.music", "ru.yandex.yandexnavi", "ru.dublgis.dgismobile"), installed)
        assertEquals(listOf("Spotify", "Навигатор", "ru.dublgis.dgismobile"), entries.map { it.label })
        assertEquals(listOf("com.spotify.music", "ru.yandex.yandexnavi", "ru.dublgis.dgismobile"), entries.map { it.packageName })
        assertFalse(entries.last().installed)
        assertTrue(entries.first().installed)
    }

    @Test
    fun `candidates exclude whitelisted, car system and self, user apps first`() {
        val c = WhitelistModel.candidates(setOf("com.spotify.music"), installed, query = "", selfPackage = "app.mcleaner")
        assertEquals(listOf("org.telegram.messenger", "ru.yandex.yandexnavi"), c.map { it.packageName })
    }

    @Test
    fun `candidates filter by label or package, case-insensitive`() {
        assertEquals(
            listOf("org.telegram.messenger"),
            WhitelistModel.candidates(emptySet(), installed, query = "TELE", selfPackage = "app.mcleaner").map { it.packageName },
        )
        assertEquals(
            listOf("ru.yandex.yandexnavi"),
            WhitelistModel.candidates(emptySet(), installed, query = "yandex", selfPackage = "app.mcleaner").map { it.packageName },
        )
    }

    @Test
    fun `non-car system apps are offered after user apps`() {
        val withSys = installed + InstalledApp("com.android.chrome", "Chrome", isSystem = true)
        val c = WhitelistModel.candidates(emptySet(), withSys, query = "", selfPackage = "app.mcleaner")
        assertEquals("com.android.chrome", c.last().packageName)
        assertFalse(c.any { it.packageName == "com.ecarx.radio" })
    }
}
