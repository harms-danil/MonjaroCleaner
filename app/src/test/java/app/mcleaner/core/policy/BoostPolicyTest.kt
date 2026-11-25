package app.mcleaner.core.policy

import app.mcleaner.core.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostPolicyTest {
    private fun app(pkg: String, system: Boolean = false, running: Boolean = true) =
        AppInfo(pkg, pkg, isSystem = system, isRunning = running, pssKb = 1000, cacheBytes = 10, isFrozen = false)

    private fun ctx(
        whitelist: Set<String> = emptySet(),
        stopGoogle: Boolean = false,
        foreground: String? = null,
        busy: Set<String> = emptySet(),
    ) = BoostContext(
        whitelist = whitelist,
        stopGoogle = stopGoogle,
        foregroundPackage = foreground,
        launcherPackage = "com.ecarx.launcher2",
        imePackage = "com.example.ime",
        selfPackage = "app.mcleaner",
        busyPackages = busy,
    )

    @Test
    fun userBackgroundApp_isCandidate() {
        assertEquals(listOf("com.example.game"), BoostPolicy.killCandidates(listOf(app("com.example.game")), ctx()))
    }

    @Test
    fun notRunning_notCandidate() {
        assertTrue(BoostPolicy.killCandidates(listOf(app("com.example.game", running = false)), ctx()).isEmpty())
    }

    @Test
    fun whitelisted_notKilled() {
        val apps = listOf(app("ru.yandex.yandexnavi"))
        assertTrue(BoostPolicy.killCandidates(apps, ctx(whitelist = setOf("ru.yandex.yandexnavi"))).isEmpty())
    }

    @Test
    fun systemApp_notKilled() {
        assertTrue(BoostPolicy.killCandidates(listOf(app("com.android.settings", system = true)), ctx()).isEmpty())
    }

    @Test
    fun ecarxAndGeelyPrefixes_neverKilled() {
        val apps = listOf(app("com.ecarx.weather"), app("com.geely.hicar"))
        assertTrue(BoostPolicy.killCandidates(apps, ctx()).isEmpty())
        assertTrue(BoostPolicy.blindKillCandidates(apps, ctx()).isEmpty())
    }

    @Test
    fun foregroundAndMediaApps_notKilled() {
        val apps = listOf(app("ru.yandex.yandexnavi"), app("ru.fmplay"), app("com.example.game"))
        val result = BoostPolicy.killCandidates(apps, ctx(foreground = "ru.yandex.yandexnavi", busy = setOf("ru.fmplay")))
        assertEquals(listOf("com.example.game"), result)
    }

    @Test
    fun busyProbeFailed_nothingKilled() {
        val apps = listOf(app("com.example.navitel"), app("com.example.game"))
        assertTrue(BoostPolicy.killCandidates(apps, ctx().copy(busyKnown = false)).isEmpty())
    }

    @Test
    fun selfShizukuLauncherIme_notKilled() {
        val apps = listOf(
            app("app.mcleaner"), app(BoostPolicy.SHIZUKU_PACKAGE), app("com.ecarx.launcher2"), app("com.example.ime"),
        )
        assertTrue(BoostPolicy.killCandidates(apps, ctx()).isEmpty())
    }

    @Test
    fun google_notKilledByDefault_killedWhenStopGoogle() {
        val apps = listOf(app("com.google.android.gms", system = true), app("com.android.vending"))
        assertTrue(BoostPolicy.killCandidates(apps, ctx()).isEmpty())
        assertEquals(
            listOf("com.google.android.gms", "com.android.vending"),
            BoostPolicy.killCandidates(apps, ctx(stopGoogle = true)),
        )
    }

    @Test
    fun google_whitelisted_notKilledEvenWhenStopGoogle() {
        val apps = listOf(app("com.google.android.gms", system = true))
        assertTrue(BoostPolicy.killCandidates(apps, ctx(whitelist = setOf("com.google.android.gms"), stopGoogle = true)).isEmpty())
    }

    @Test
    fun blind_includesNotRunning_neverGoogle() {
        val apps = listOf(
            app("com.example.game", running = false),
            app("com.android.vending", running = false),
            app("com.android.settings", system = true, running = false),
        )
        assertEquals(listOf("com.example.game"), BoostPolicy.blindKillCandidates(apps, ctx(stopGoogle = true)))
    }

    @Test
    fun cacheTargets_skipWhitelistAndPrefixes_includeGoogle() {
        val apps = listOf(
            app("ru.yandex.yandexnavi"), app("com.ecarx.map", system = true),
            app("com.google.android.gms", system = true), app("com.example.game", running = false),
        )
        assertEquals(
            listOf("com.google.android.gms", "com.example.game"),
            BoostPolicy.cacheTargets(apps, setOf("ru.yandex.yandexnavi")),
        )
    }

    @Test
    fun canStopManually_userAndGoogleOnly() {
        assertTrue(BoostPolicy.canStopManually(app("com.example.game"), "app.mcleaner"))
        assertTrue(BoostPolicy.canStopManually(app("com.google.android.gms", system = true), "app.mcleaner"))
        assertFalse(BoostPolicy.canStopManually(app("com.android.settings", system = true), "app.mcleaner"))
        assertFalse(BoostPolicy.canStopManually(app("com.ecarx.radio"), "app.mcleaner"))
        assertFalse(BoostPolicy.canStopManually(app("app.mcleaner"), "app.mcleaner"))
    }

    private fun canFreeze(pkg: String, system: Boolean = false, whitelist: Set<String> = emptySet(), protected: Set<String> = emptySet()) =
        BoostPolicy.canFreeze(app(pkg, system = system), "app.mcleaner", whitelist, protected)

    @Test
    fun canFreeze_rejectsSystemGoogleSelfShizuku() {
        assertTrue(canFreeze("com.example.game"))
        assertFalse(canFreeze("com.android.settings", system = true))
        assertFalse(canFreeze("com.android.vending"))
        assertFalse(canFreeze("app.mcleaner"))
        assertFalse(canFreeze(BoostPolicy.SHIZUKU_PACKAGE))
        assertFalse(canFreeze("com.geely.hicar"))
        assertFalse(canFreeze("com.ecarx.launcher2"))
    }

    @Test
    fun canFreeze_rejectsLaunchersImeAndWhitelist() {
        val protected = setOf("com.example.launcher", "com.other.home", "com.example.keyboard")
        assertFalse(canFreeze("com.example.launcher", protected = protected))
        assertFalse(canFreeze("com.other.home", protected = protected))
        assertFalse(canFreeze("com.example.keyboard", protected = protected))
        assertFalse(canFreeze("ru.yandex.yandexnavi", whitelist = setOf("ru.yandex.yandexnavi")))
        assertTrue(canFreeze("com.example.game", whitelist = setOf("ru.yandex.yandexnavi"), protected = protected))
    }

    @Test
    fun cacheTargets_skipBusy() {
        val apps = listOf(app("ru.paused.player"), app("com.example.a11y"), app("com.example.game"))
        assertEquals(
            listOf("com.example.game"),
            BoostPolicy.cacheTargets(apps, emptySet(), busy = setOf("ru.paused.player", "com.example.a11y")),
        )
    }
}
