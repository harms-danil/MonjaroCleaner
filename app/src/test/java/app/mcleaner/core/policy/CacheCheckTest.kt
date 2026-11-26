package app.mcleaner.core.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCheckTest {
    private val mb = 1024L * 1024

    @Test
    fun `cache that barely changed counts as not cleared`() {
        assertTrue(CacheCheck.notCleared(before = 154 * mb, after = 154 * mb))
        assertTrue(CacheCheck.notCleared(before = 154 * mb, after = 150 * mb))
        assertFalse(CacheCheck.notCleared(before = 154 * mb, after = 2 * mb))
    }

    @Test
    fun `tiny or unknown caches are not worth a warning`() {
        assertFalse(CacheCheck.notCleared(before = 512 * 1024, after = 512 * 1024))
        assertFalse(CacheCheck.notCleared(before = null, after = 154 * mb))
        assertFalse(CacheCheck.notCleared(before = 154 * mb, after = null))
    }
}
