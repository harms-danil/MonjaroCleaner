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
    fun resumedActivities_allDisplays_notPaused() {
        assertEquals(
            setOf("ru.yandex.yandexnavi", "com.ecarx.cluster"),
            DumpsysStateParsers.resumedPackages(Fixtures.read("dumpsys_activities_a11.txt")),
        )
    }

    @Test
    fun probeValidity() {
        assertEquals(true, DumpsysStateParsers.looksLikeServices(Fixtures.read("dumpsys_services_a11.txt")))
        assertEquals(false, DumpsysStateParsers.looksLikeServices("Can't find service: activity"))
        assertEquals(false, DumpsysStateParsers.looksLikeServices(""))
        assertEquals(true, DumpsysStateParsers.looksLikeMediaSessions(Fixtures.read("dumpsys_media_session_a11.txt")))
        assertEquals(true, DumpsysStateParsers.looksLikeMediaSessions("Sessions Stack - have 0 sessions:\n"))
        assertEquals(false, DumpsysStateParsers.looksLikeMediaSessions("Permission Denial"))
        assertEquals(true, DumpsysStateParsers.looksLikeActivities(Fixtures.read("dumpsys_activities_a11.txt")))
        assertEquals(false, DumpsysStateParsers.looksLikeActivities(""))
    }

    @Test
    fun bufferingAndConnecting_countAsPlaying() {
        val text = """
            package=ru.radio
              active=true
              state=PlaybackState {state=6, position=0}
            package=ru.stream
              active=true
              state=PlaybackState {state=8, position=0}
            package=ru.stopped
              active=true
              state=PlaybackState {state=1, position=0}
        """.trimIndent()
        assertEquals(setOf("ru.radio", "ru.stream"), DumpsysStateParsers.activeMediaSessionPackages(text))
    }

    @Test
    fun emptyInputs_empty() {
        assertEquals(emptySet<String>(), DumpsysStateParsers.resumedPackages(""))
        assertEquals(emptySet<String>(), DumpsysStateParsers.foregroundServicePackages(""))
        assertEquals(emptySet<String>(), DumpsysStateParsers.activeMediaSessionPackages(""))
    }
}
