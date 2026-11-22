package app.mcleaner.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class DumpsysStateParsersTest {
    @Test
    fun foregroundServices() {
        assertEquals(
            setOf("ru.fmplay", "ru.yandex.yandexnavi"),
            DumpsysStateParsers.foregroundServicePackages(Fixtures.read("dumpsys_services_a11.txt")),
        )
    }

    @Test
    fun playingMediaSessions_onlyPlaying() {
        // yandex.music is paused (state=2), podcast is inactive
        assertEquals(
            setOf("ru.fmplay"),
            DumpsysStateParsers.activeMediaSessionPackages(Fixtures.read("dumpsys_media_session_a11.txt")),
        )
    }

    @Test
    fun currentUser_parsesAndDefaultsToZero() {
        assertEquals(10, DumpsysStateParsers.currentUser("10\n"))
        assertEquals(0, DumpsysStateParsers.currentUser("err"))
    }

    @Test
    fun emptyInputs_empty() {
        assertEquals(emptySet<String>(), DumpsysStateParsers.foregroundServicePackages(""))
        assertEquals(emptySet<String>(), DumpsysStateParsers.activeMediaSessionPackages(""))
    }
}
