package app.mcleaner.core.policy

import app.mcleaner.core.parse.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedPackagesTest {
    @Test
    fun componentList_packagesOfEveryComponent() {
        assertEquals(
            setOf("com.example.a11y", "ru.voice.helper"),
            ProtectedPackages.fromComponentList("com.example.a11y/.MyService:ru.voice.helper/ru.voice.helper.Svc"),
        )
    }

    @Test
    fun componentList_emptyOrNull() {
        assertEquals(emptySet<String>(), ProtectedPackages.fromComponentList(null))
        assertEquals(emptySet<String>(), ProtectedPackages.fromComponentList(""))
        assertEquals(emptySet<String>(), ProtectedPackages.fromComponentList("null"))
    }

    @Test
    fun componentList_toleratesWhitespaceAndGarbage() {
        assertEquals(
            setOf("com.a.b", "com.c.d", "com.e.f"),
            ProtectedPackages.fromComponentList(" com.a.b/.S :: com.c.d/com.c.d.S\n:not a pkg/x:com.e.f"),
        )
    }

    @Test
    fun mediaSessions_everyStateCounts() {
        assertEquals(
            setOf("ru.fmplay", "ru.yandex.music", "com.example.podcast"),
            ProtectedPackages.mediaSessionPackages(Fixtures.read("dumpsys_media_session_a11.txt")),
        )
    }

    @Test
    fun mediaSessions_pausedAndInactiveIncluded() {
        val text = """
            Sessions Stack - have 2 sessions:
              MediaSessionRecord
                package=ru.paused
                active=false
                state=PlaybackState {state=2, position=0}
              MediaSessionRecord
                package=ru.stopped
                active=true
                state=null
        """.trimIndent()
        assertEquals(setOf("ru.paused", "ru.stopped"), ProtectedPackages.mediaSessionPackages(text))
        assertEquals(emptySet<String>(), ProtectedPackages.mediaSessionPackages(""))
    }
}
