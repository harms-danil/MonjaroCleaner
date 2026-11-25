package app.mcleaner.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mcleaner.ui.common.MinTouch

const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
const val SHIZUKU_ADB_COMMAND = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"

private data class GuideStep(val text: String, val snippet: String? = null, val isLink: Boolean = false)

private val STEPS = listOf(
    GuideStep("Включите режим разработчика: Настройки → О системе → 7 раз нажмите «Номер сборки» (на Monjaro путь может отличаться, иногда нужен инженерный пароль)."),
    GuideStep("Установите Shizuku: скачайте APK (файл shizuku-…-release.apk) со страницы релизов и установите. Можно открыть ссылку здесь или скопировать её.", SHIZUKU_RELEASES_URL, isLink = true),
    GuideStep("Запустите Shizuku с ноутбука по ADB (на Android 9 нет «Беспроводной отладки», поэтому нужен ноутбук). Команда:", SHIZUKU_ADB_COMMAND),
    GuideStep("Вернитесь в MCleaner и нажмите «Запросить разрешение», затем разрешите доступ."),
    GuideStep("После полной перезагрузки магнитолы Shizuku нужно запустить заново (шаг 3). Если магнитола просто «спала» — он продолжает работать."),
)

@Composable
fun ShizukuGuide(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        STEPS.forEachIndexed { i, step ->
            Text("${i + 1}. ${step.text}")
            step.snippet?.let { snippet ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionContainer(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(snippet, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                    if (step.isLink) {
                        TextButton(onClick = { openLink(context, snippet) }, modifier = Modifier.heightIn(min = MinTouch)) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Text(" Открыть")
                        }
                    }
                    TextButton(onClick = { copy(context, snippet) }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Text(" Копировать")
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MCleaner", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MCleaner", url))
        Toast.makeText(context, "Браузер не найден — ссылка скопирована", Toast.LENGTH_LONG).show()
    }
}
