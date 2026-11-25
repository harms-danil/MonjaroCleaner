package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.model.ShellResult
import app.mcleaner.privileged.FakePrivileged
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeManagerTest {
    private val frozen = mutableSetOf<String>()
    private val fake = FakePrivileged()
    private val manager = FreezeManager(fake, currentUser = { 10 }, addFrozen = { frozen += it }, removeFrozen = { frozen -= it })

    private fun app(pkg: String, system: Boolean = false) = AppInfo(pkg, pkg, system, false, null, null, false)

    private suspend fun freeze(pkg: String, whitelist: Set<String> = emptySet(), protected: Set<String> = emptySet()) =
        manager.freeze(app(pkg), "app.mcleaner", whitelist, protected)

    @Test
    fun usesCurrentUser_quotesPackage() = runTest {
        assertTrue(freeze("com.example.game"))
        assertEquals(listOf("pm disable-user --user 10 'com.example.game'"), fake.calls)
        assertEquals(setOf("com.example.game"), frozen)
    }

    @Test
    fun refusesSystemApp() = runTest {
        assertFalse(manager.freeze(app("com.android.settings", system = true), "app.mcleaner", emptySet(), emptySet()))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun refusesLauncherImeAndWhitelisted() = runTest {
        assertFalse(freeze("com.example.launcher", protected = setOf("com.example.launcher", "com.example.ime")))
        assertFalse(freeze("com.example.ime", protected = setOf("com.example.launcher", "com.example.ime")))
        assertFalse(freeze("ru.yandex.yandexnavi", whitelist = setOf("ru.yandex.yandexnavi")))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun failedCommand_notRecorded() = runTest {
        fake.responses["pm disable-user --user 10 'com.example.game'"] = ShellResult(1, "denied")
        assertFalse(freeze("com.example.game"))
        assertTrue(frozen.isEmpty())
    }

    @Test
    fun unfreeze_quotesAndRejectsInvalidName() = runTest {
        assertTrue(manager.unfreeze("com.a.b"))
        assertFalse(manager.unfreeze("com.a; reboot"))
        assertEquals(listOf("pm enable --user 10 'com.a.b'"), fake.calls)
    }

    @Test
    fun unfreezeAll_onlyInstalledAndDisabled() = runTest {
        fake.responses[LIST] = ShellResult(0, "package:com.a.x\npackage:com.c.z\n")
        frozen += setOf("com.a.x", "com.b.y", "com.a.x; reboot")
        assertEquals(1, manager.unfreezeAll(frozen.toSet()))
        assertEquals(listOf(LIST, "pm enable --user 10 'com.a.x'"), fake.calls)
        // Stale entries (uninstalled, already enabled, not a package name) are dropped from the list.
        assertTrue(frozen.isEmpty())
    }

    @Test
    fun unfreezeAll_listFails_touchesNothing() = runTest {
        fake.responses[LIST] = ShellResult(1, "error")
        frozen += setOf("com.a.x")
        assertEquals(0, manager.unfreezeAll(frozen.toSet()))
        assertEquals(listOf(LIST), fake.calls)
        assertEquals(setOf("com.a.x"), frozen)
    }

    private companion object {
        const val LIST = "pm list packages -d --user 10"
    }
}
