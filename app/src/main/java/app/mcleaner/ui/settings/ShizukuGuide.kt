package app.mcleaner.ui.settings

import app.mcleaner.core.i18n.tr
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
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mcleaner.ui.common.MinTouch

const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases"
/**
 * Starts Shizuku straight from its installed starter: works on a fresh install
 * (the old `…/start.sh` only appears after Shizuku's own setup screen was opened).
 */
const val SHIZUKU_SHELL_COMMAND =
    "p=\$(dirname \$(pm path moe.shizuku.privileged.api | cut -d: -f2)); \$p/lib/arm64/libshizuku.so || \$p/lib/arm/libshizuku.so"
const val SHIZUKU_ADB_COMMAND = "adb shell '$SHIZUKU_SHELL_COMMAND'"

private data class GuideStep(val text: String, val snippet: String? = null, val isLink: Boolean = false)

private fun steps() = listOf(
    GuideStep(tr("Включите режим разработчика: Настройки → О системе → 7 раз нажмите «Номер сборки» (на Monjaro путь может отличаться, иногда нужен инженерный пароль).", "Enable developer mode: Settings → About → tap \"Build number\" 7 times (on Monjaro the path may differ; sometimes an engineering password is required).")),
    GuideStep(tr("Установите Shizuku: кнопкой «Установить Shizuku» выше (встроен в MCleaner) или APK со страницы релизов — ссылку можно открыть или скопировать.", "Install Shizuku: with the Install Shizuku button above (bundled with MCleaner) or the APK from the releases page — open or copy the link."), SHIZUKU_RELEASES_URL, isLink = true),
    GuideStep(tr("Запустите Shizuku с ноутбука по ADB (на Android 9 нет «Беспроводной отладки»). Одна команда — или скрипт setup-monjaro.sh из README:", "Start Shizuku from a laptop over ADB (Android 9 has no Wireless debugging). One command — or the setup-monjaro.sh script from the README:"), SHIZUKU_ADB_COMMAND),
    GuideStep(tr("Без ноутбука — с Android-телефона: приложение Bugjaeger (Google Play), телефон к магнитоле USB-кабелем через переходник OTG. В терминале Bugjaeger выполните:", "No laptop — from an Android phone: the Bugjaeger app (Google Play), phone connected to the head unit by USB through an OTG adapter. In Bugjaeger's terminal run:"), SHIZUKU_SHELL_COMMAND),
    GuideStep(tr("Вернитесь в MCleaner и нажмите «Запросить разрешение», затем разрешите доступ.", "Return to MCleaner, tap \"Request permission\", then grant access.")),
    GuideStep(tr("После полной перезагрузки магнитолы Shizuku нужно запустить заново (шаг 3 или 4). Если магнитола просто «спала» — он продолжает работать.", "After a full head unit reboot, Shizuku must be started again (step 3 or 4). If the head unit was just asleep, it keeps running.")),
)

@Composable
fun ShizukuGuide(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps().forEachIndexed { i, step ->
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
                            Text(tr(" Открыть", " Open"))
                        }
                    }
                    TextButton(onClick = { copy(context, snippet) }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Text(tr(" Копировать", " Copy"))
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MCleaner", text))
    Toast.makeText(context, tr("Скопировано", "Copied"), Toast.LENGTH_SHORT).show()
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MCleaner", url))
        Toast.makeText(context, tr("Браузер не найден — ссылка скопирована", "No browser found — link copied"), Toast.LENGTH_LONG).show()
    }
}

/** «Установить Shizuku» from the APK bundled in MCleaner — no internet needed. */
@Composable
fun ShizukuInstallRow(installer: app.mcleaner.privileged.ShizukuInstaller, showButton: Boolean) {
    val busy by installer.busy.collectAsStateWithLifecycle()
    val message by installer.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (showButton) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Button(
            onClick = { scope.launch { installer.install() } },
            enabled = !busy,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(tr("Установить Shizuku", "Install Shizuku")) }
        Text(
            tr(
                "Shizuku ${app.mcleaner.privileged.ShizukuInstaller.BUNDLED_VERSION} встроен в MCleaner — интернет не нужен. Android покажет окно установки.",
                "Shizuku ${app.mcleaner.privileged.ShizukuInstaller.BUNDLED_VERSION} is bundled with MCleaner — no internet needed. Android will show the install dialog.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
