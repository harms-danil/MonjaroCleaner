package app.mcleaner.domain

import app.mcleaner.privileged.FakePrivileged
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCleanerTest {
    private val pkgs = (1..10).map { "com.p$it" }

    @Test
    fun allSucceed() = runTest {
        val fake = FakePrivileged()
        val r = CacheCleaner(fake).clearMany(pkgs, 10)
        assertEquals(CacheResult(cleared = 10, failed = emptyList(), perPackageUnsupported = false), r)
        assertEquals(10, fake.deleteCacheCalls.single { it.first == "com.p1" }.second)
    }

    @Test
    fun threeFailuresInARow_stopsEarly() = runTest {
        val fake = FakePrivileged(deleteCacheResult = { false })
        val r = CacheCleaner(fake).clearMany(pkgs, 0)
        assertEquals(3, fake.deleteCacheCalls.size)
        assertEquals(10, r.failed.size)
        assertTrue(r.perPackageUnsupported)
    }

    @Test
    fun sporadicFailure_continues() = runTest {
        val fake = FakePrivileged(deleteCacheResult = { it != "com.p2" })
        val r = CacheCleaner(fake).clearMany(pkgs, 0)
        assertEquals(9, r.cleared)
        assertEquals(listOf("com.p2"), r.failed)
        assertFalse(r.perPackageUnsupported)
    }

    @Test
    fun trimAll_runsCommand() = runTest {
        val fake = FakePrivileged()
        assertTrue(CacheCleaner(fake).trimAll())
        assertEquals(listOf("pm trim-caches 999G"), fake.calls)
    }

    @Test
    fun externalCache_measuresThenEmptiesEachAppFolder() = runTest {
        val du = "du -sk '/s/Android/data/a.b/cache' '/s/Android/data/c.d/cache' 2>/dev/null"
        val fake = FakePrivileged(responses = mutableMapOf(du to app.mcleaner.core.model.ShellResult(0, "1024\t/s/Android/data/a.b/cache\n4\t/s/Android/data/c.d/cache\n")))
        val r = CacheCleaner(fake).clearExternal(listOf("a.b", "c.d"), root = "/s")
        assertEquals(du, fake.calls[0])
        val clear = fake.calls[1]
        assertTrue("find '/s/Android/data/a.b/cache' -mindepth 1 -delete" in clear)
        assertTrue("find '/s/Android/data/c.d/cache' -mindepth 1 -delete" in clear)
        // An empty folder still takes one 4 KB block: not counted as freed.
        assertEquals(1020L * 1024, r.bytes)
        assertEquals(1, r.apps)
    }

    @Test
    fun externalCache_nothingToDo() = runTest {
        val fake = FakePrivileged()
        assertEquals(ExternalCacheResult(0, 0), CacheCleaner(fake).clearExternal(emptyList(), root = "/s"))
        assertTrue(fake.calls.isEmpty())
    }
}
