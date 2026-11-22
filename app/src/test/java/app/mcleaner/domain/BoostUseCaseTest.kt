package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostContext
import app.mcleaner.privileged.FakePrivileged
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostUseCaseTest {
    private fun app(pkg: String, running: Boolean = true, system: Boolean = false) =
        AppInfo(pkg, pkg, isSystem = system, isRunning = running, pssKb = 100, cacheBytes = 10, isFrozen = false)

    private val ctx = BoostContext(
        whitelist = setOf("ru.yandex.yandexnavi"),
        stopGoogle = false,
        foregroundPackage = null,
        launcherPackage = null,
        imePackage = null,
        selfPackage = "app.mcleaner",
        busyPackages = emptySet(),
    )

    private fun useCase(
        fake: FakePrivileged,
        apps: List<AppInfo>,
        freeRam: List<Long> = listOf(1000, 3000),
        cache: List<Long> = listOf(500, 100),
        killed: MutableList<String> = mutableListOf(),
    ): BoostUseCase {
        val ram = ArrayDeque(freeRam)
        val caches = ArrayDeque(cache)
        return BoostUseCase(
            privileged = fake,
            loadApps = { apps },
            buildContext = { ctx },
            readFreeRamKb = { ram.removeFirstOrNull() ?: 0 },
            totalCacheBytes = { caches.removeFirstOrNull() ?: 0 },
            killBackground = { killed += it },
            cacheCleaner = CacheCleaner(fake),
            currentUser = { 0 },
            delayMs = {},
        )
    }

    @Test
    fun fullMode_forceStopsCandidatesAndClearsCache() = runTest {
        val fake = FakePrivileged()
        val report = useCase(fake, listOf(app("ru.yandex.yandexnavi"), app("com.example.game"))).run()

        assertTrue("am force-stop com.example.game" in fake.calls)
        assertTrue(fake.calls.none { "yandexnavi" in it })
        assertEquals(listOf("com.example.game" to 0), fake.deleteCacheCalls)
        assertEquals(BoostReport(fullMode = true, stopped = 1, failed = emptyList(), freedRamKb = 2000, freedCacheBytes = 400, protectionUnavailable = false), report)
    }

    @Test
    fun shizukuDiesMidway_reportsFailures() = runTest {
        val fake = FakePrivileged(dieAfterCalls = 1)
        val apps = listOf(app("com.a"), app("com.b"), app("com.c"))
        val report = useCase(fake, apps).run()

        assertEquals(1, report.stopped)
        assertEquals(listOf("com.b", "com.c"), report.failed)
    }

    @Test
    fun basicMode_killsBlindCandidates_noShellCalls() = runTest {
        val fake = FakePrivileged(available = false)
        val killed = mutableListOf<String>()
        val apps = listOf(app("com.example.game", running = false), app("ru.yandex.yandexnavi", running = false), app("com.android.settings", system = true))
        val report = useCase(fake, apps, killed = killed).run()

        assertEquals(listOf("com.example.game"), killed)
        assertTrue(fake.calls.isEmpty())
        assertTrue(fake.deleteCacheCalls.isEmpty())
        assertFalse(report.fullMode)
        assertEquals(1, report.stopped)
    }

    @Test
    fun perPackageCacheUnsupported_noTrimAll() = runTest {
        val fake = FakePrivileged(deleteCacheResult = { false })
        val apps = (1..5).map { app("com.x$it", running = false) }
        useCase(fake, apps).run()

        assertTrue(fake.calls.none { it.startsWith("pm trim-caches") })
    }

    @Test
    fun busyProbeFailed_noForceStop_cacheStillCleared() = runTest {
        val fake = FakePrivileged()
        val uc = BoostUseCase(
            privileged = fake,
            loadApps = { listOf(app("com.example.navitel"), app("com.example.game")) },
            buildContext = { ctx.copy(busyKnown = false) },
            readFreeRamKb = { 0 },
            totalCacheBytes = { 0 },
            killBackground = {},
            cacheCleaner = CacheCleaner(fake),
            currentUser = { 0 },
            delayMs = {},
        )
        val report = uc.run()

        assertTrue(fake.calls.none { it.startsWith("am force-stop") })
        assertEquals(2, fake.deleteCacheCalls.size)
        assertEquals(0, report.stopped)
        assertTrue(report.protectionUnavailable)
    }

    @Test
    fun negativeDeltas_clampedToZero() = runTest {
        val report = useCase(FakePrivileged(), listOf(app("com.example.game")), freeRam = listOf(3000, 1000), cache = listOf(100, 500)).run()
        assertEquals(0L, report.freedRamKb)
        assertEquals(0L, report.freedCacheBytes)
    }
}
