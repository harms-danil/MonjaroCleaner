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
}
