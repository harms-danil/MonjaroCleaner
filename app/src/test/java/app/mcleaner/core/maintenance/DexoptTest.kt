package app.mcleaner.core.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DexoptTest {
    private val dump = """
        Dexopt state:
          [com.android.cts.priv.ctsshim]
          [com.android.providers.calendar]
            path: /system/priv-app/CalendarProvider/CalendarProvider.apk
              arm64: [status=quicken] [reason=prebuilt]
          [com.shihab.diplay]
            path: /data/app/com.shihab.diplay-FMtf==/base.apk
              arm64: [status=run-from-apk] [reason=unknown]
          [ru.yandex.yandexnavi]
            path: /data/app/ru.yandex.yandexnavi-1/base.apk
              arm64: [status=speed-profile] [reason=bg-dexopt]
              arm: [status=quicken] [reason=install]
          [com.launcher]
            path: /system/app/Launcher/Launcher.apk
              arm64: [status=speed] [reason=prebuilt]
    """.trimIndent()

    @Test
    fun `status per package, the worst one when there are several`() {
        assertEquals(
            mapOf(
                "com.android.providers.calendar" to "quicken",
                "com.shihab.diplay" to "run-from-apk",
                "ru.yandex.yandexnavi" to "quicken",
                "com.launcher" to "speed",
            ),
            Dexopt.parse(dump),
        )
    }

    @Test
    fun `ranks from not compiled to fully compiled`() {
        assertTrue(Dexopt.rank("run-from-apk") < Dexopt.rank("quicken"))
        assertTrue(Dexopt.rank("quicken") < Dexopt.rank("speed-profile"))
        assertTrue(Dexopt.rank("speed-profile") < Dexopt.rank("speed"))
        assertTrue(Dexopt.rank("speed") <= Dexopt.rank("everything"))
        assertFalse(Dexopt.isOptimal("run-from-apk"))
        assertTrue(Dexopt.isOptimal("speed-profile"))
    }

    @Test
    fun `plan compiles important apps fully first, other user apps by profile, only when it helps`() {
        val statuses = Dexopt.parse(dump)
        val plan = Dexopt.plan(
            statuses,
            userApps = setOf("com.shihab.diplay", "ru.yandex.yandexnavi"),
            important = setOf("ru.yandex.yandexnavi", "com.launcher"),
        )
        assertEquals(
            listOf(CompileStep("ru.yandex.yandexnavi", "speed"), CompileStep("com.shihab.diplay", "speed-profile")),
            plan,
        )
    }
}
