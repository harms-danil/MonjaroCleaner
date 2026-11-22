package app.mcleaner.core.junk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCacheFilterTest {
    private val whitelist = setOf("ru.yandex.yandexnavi")

    @Test
    fun packageFromPath() {
        assertEquals("com.x.y", AppCacheFilter.packageOf("/storage/emulated/0/Android/data/com.x.y/cache"))
        assertNull(AppCacheFilter.packageOf("/storage/emulated/0/Download/cache"))
    }

    @Test
    fun ordinaryApp_allowed() {
        assertTrue(AppCacheFilter.allowed("/sdcard/Android/data/com.example.game/cache", whitelist))
    }

    @Test
    fun whitelistedAndCarApps_excluded() {
        assertFalse(AppCacheFilter.allowed("/sdcard/Android/data/ru.yandex.yandexnavi/cache", whitelist))
        assertFalse(AppCacheFilter.allowed("/sdcard/Android/data/com.ecarx.map/cache", whitelist))
        assertFalse(AppCacheFilter.allowed("/sdcard/Android/data/com.geely.radio/cache", whitelist))
    }

    @Test
    fun unparsablePath_excluded() {
        assertFalse(AppCacheFilter.allowed("/sdcard/weird", whitelist))
    }
}
