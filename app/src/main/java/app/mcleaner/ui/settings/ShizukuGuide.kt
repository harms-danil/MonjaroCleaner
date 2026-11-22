package app.mcleaner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val SHIZUKU_STEPS = listOf(
    "Включите режим разработчика: Настройки → О системе → 7 раз нажмите «Номер сборки» (на Monjaro путь может отличаться, иногда нужен инженерный пароль).",
    "Установите Shizuku: скачайте APK с github.com/RikkaApps/Shizuku/releases на флешку и установите.",
    "Запустите Shizuku. Вариант 1 — в приложении Shizuku «Беспроводная отладка». Вариант 2 — с ноутбука по ADB:",
    "Вернитесь в MCleaner и нажмите «Запросить разрешение», затем разрешите доступ.",
    "После перезагрузки магнитолы Shizuku обычно нужно запустить заново (шаг 3). Если магнитола просто «спала» — он продолжает работать.",
)

const val SHIZUKU_ADB_COMMAND = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

@Composable
fun ShizukuGuide(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SHIZUKU_STEPS.forEachIndexed { i, step ->
            Text("${i + 1}. $step")
            if (i == 2) {
                Text(SHIZUKU_ADB_COMMAND, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
