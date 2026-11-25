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

    @Test
    fun invalidPackageName_excluded() {
        assertFalse(AppCacheFilter.allowed("/sdcard/Android/data/../cache", whitelist))
        assertFalse(AppCacheFilter.allowed("/sdcard/Android/data/x/cache", whitelist))
    }

    @Test
    fun ownerOfAppFolders() {
        assertEquals("com.x.y", AppCacheFilter.ownerOf("/sdcard/Android/data/com.x.y/files/a.log"))
        assertEquals("com.x.y", AppCacheFilter.ownerOf("/sdcard/Android/media/com.x.y"))
        assertEquals("com.x.y", AppCacheFilter.ownerOf("/sdcard/Android/data/com.x.y/cache"))
        assertNull(AppCacheFilter.ownerOf("/sdcard/Download/a.log"))
        assertNull(AppCacheFilter.ownerOf("/sdcard/Android/obb/com.x.y/main.obb"))
    }

    @Test
    fun protectedOwner_whitelistAndCar() {
        assertTrue(AppCacheFilter.protectedOwner("/sdcard/Android/data/ru.yandex.yandexnavi/files/a.log", whitelist))
        assertTrue(AppCacheFilter.protectedOwner("/sdcard/Android/media/com.geely.music/b.log", whitelist))
        assertFalse(AppCacheFilter.protectedOwner("/sdcard/Android/data/com.example.game/files/a.log", whitelist))
        assertFalse(AppCacheFilter.protectedOwner("/sdcard/Download/a.log", whitelist))
    }
}
