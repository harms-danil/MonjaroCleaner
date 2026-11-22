package app.mcleaner.domain

import app.mcleaner.core.model.AppInfo
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

    @Test
    fun usesCurrentUser() = runTest {
        assertTrue(manager.freeze(app("com.example.game"), "app.mcleaner"))
        assertEquals(listOf("pm disable-user --user 10 com.example.game"), fake.calls)
        assertEquals(setOf("com.example.game"), frozen)
    }

    @Test
    fun refusesSystemApp() = runTest {
        assertFalse(manager.freeze(app("com.android.settings", system = true), "app.mcleaner"))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun failedCommand_notRecorded() = runTest {
        fake.responses["pm disable-user --user 10 com.example.game"] = app.mcleaner.core.model.ShellResult(1, "denied")
        assertFalse(manager.freeze(app("com.example.game"), "app.mcleaner"))
        assertTrue(frozen.isEmpty())
    }

    @Test
    fun unfreezeAll() = runTest {
        frozen += setOf("com.a", "com.b")
        assertEquals(2, manager.unfreezeAll(setOf("com.a", "com.b")))
        assertEquals(listOf("pm enable --user 10 com.a", "pm enable --user 10 com.b"), fake.calls)
        assertTrue(frozen.isEmpty())
    }
}
