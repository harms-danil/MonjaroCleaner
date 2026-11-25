package app.mcleaner.core.policy

import app.mcleaner.core.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {
    private fun app(pkg: String, system: Boolean = false) = AppInfo(pkg, pkg, system, true, 1, 1, false)

    @Test
    fun `only user apps and Google that nothing depends on can be restricted`() {
        val protected = setOf("com.launcher", "com.steering.buttons")
        val whitelist = setOf("ru.yandex.yandexnavi")
        fun can(a: AppInfo) = BackgroundPolicy.canRestrict(a, "app.mcleaner", whitelist, protected)
        assertTrue(can(app("com.huawei.hwid")))
        assertTrue(can(app("com.google.android.gms", system = true)))
        assertFalse(can(app("com.android.settings", system = true)))
        assertFalse(can(app("ru.yandex.yandexnavi")))
        assertFalse(can(app("com.launcher")))
        assertFalse(can(app("com.steering.buttons")))
        assertFalse(can(app("com.ecarx.radio")))
        assertFalse(can(app("app.mcleaner")))
        assertFalse(can(app("moe.shizuku.privileged.api")))
    }

    @Test
    fun `appops output tells whether background is restricted`() {
        assertEquals(true, BackgroundPolicy.isRestricted("RUN_ANY_IN_BACKGROUND: ignore"))
        assertEquals(false, BackgroundPolicy.isRestricted("RUN_ANY_IN_BACKGROUND: allow"))
        assertEquals(false, BackgroundPolicy.isRestricted("RUN_ANY_IN_BACKGROUND: default"))
        assertEquals(null, BackgroundPolicy.isRestricted("Error: Unknown package"))
    }

    @Test
    fun `suggestions are the most often stopped apps that can be restricted and are not yet`() {
        val top = listOf("com.huawei.hwid" to 12, "ru.yandex.yandexnavi" to 5, "com.rustore" to 3, "com.iceraven" to 2)
        val canRestrict = { p: String -> p != "ru.yandex.yandexnavi" }
        assertEquals(listOf("com.huawei.hwid" to 12, "com.iceraven" to 2), BackgroundPolicy.suggestions(top, already = setOf("com.rustore"), canRestrict = canRestrict))
    }
}
