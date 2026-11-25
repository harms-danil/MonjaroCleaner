package app.mcleaner.core.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNamesTest {
    @Test
    fun validNames() {
        assertTrue(PackageNames.isValid("com.example.game"))
        assertTrue(PackageNames.isValid("ru.yandex.yandexnavi"))
        assertTrue(PackageNames.isValid("moe.shizuku.privileged.api"))
        assertTrue(PackageNames.isValid("com.a_b.C9"))
    }

    @Test
    fun invalidNames() {
        assertFalse(PackageNames.isValid(""))
        assertFalse(PackageNames.isValid("android"))
        assertFalse(PackageNames.isValid("com..x"))
        assertFalse(PackageNames.isValid(".com.x"))
        assertFalse(PackageNames.isValid("com.x."))
        assertFalse(PackageNames.isValid("1com.x"))
        assertFalse(PackageNames.isValid("com.1x"))
        assertFalse(PackageNames.isValid("com.x; reboot"))
        assertFalse(PackageNames.isValid("com.x\nreboot"))
        assertFalse(PackageNames.isValid("com.x'y"))
        assertFalse(PackageNames.isValid("com.x/.Main"))
        assertFalse(PackageNames.isValid(".."))
    }
}
