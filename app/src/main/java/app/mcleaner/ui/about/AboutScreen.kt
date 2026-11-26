package app.mcleaner.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.update.UPDATE_REPO
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.container
import app.mcleaner.ui.update.UpdateSection

private const val REPO_URL = "https://github.com/$UPDATE_REPO"

/** «Что умеет» in About: one line per section of the app. */
private val FEATURES = listOf(
    "«Обзор» — память, накопитель, процессор и самые тяжёлые приложения; «Ускорить» останавливает фоновые приложения и чистит кэш, не трогая навигатор, музыку и белый список." to
        "Overview — memory, storage, CPU and the heaviest apps; Boost stops background apps and clears cache without touching the navigator, music and the whitelist.",
    "«Приложения» — память, процессор и кэш каждого приложения: остановить, очистить кэш, заморозить, добавить в белый список." to
        "Apps — memory, CPU and cache of each app: stop, clear cache, freeze, add to the whitelist.",
    "«Очистка» — кэш всех приложений и поиск мусора: старые APK, логи, временные файлы." to
        "Cleanup — cache of all apps and junk search: old APKs, logs, temporary files.",
    "«Обслуживание» — компиляция приложений для быстрого запуска, обслуживание и тест накопителя, ограничение фоновой работы." to
        "Maintenance — compiling apps for faster start, storage trim and test, background restriction.",
    "«Журнал» — что и когда делал MCleaner; выгрузка на телефон по QR-коду или в файл." to
        "Journal — what MCleaner did and when; export to a phone via QR code or to a file.",
    "Автоматика — запуск с магнитолой, автоускорение после включения и по расписанию, предупреждение о нехватке памяти, автозапуск Shizuku." to
        "Automation — start with the head unit, auto-boost after start and on a schedule, low-memory warning, Shizuku autostart.",
    "Заморозка — отключить ненужное приложение, не удаляя его: не запускается и не занимает память, данные сохраняются." to
        "Freezing — disable an unneeded app without uninstalling it: it does not start or use memory, its data is kept.",
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            tr("MCleaner — очистка памяти и кэша магнитолы Geely Monjaro", "MCleaner — memory and cache cleaner for the Geely Monjaro head unit"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Icon(Icons.Outlined.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("MCleaner", style = MaterialTheme.typography.headlineMedium)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tr("Что умеет", "What it does"), style = MaterialTheme.typography.titleMedium)
            FEATURES.forEach { (ru, en) -> Text("• " + tr(ru, en)) }
            Text(
                tr(
                    "Полный режим работает через Shizuku — права ADB, root не нужен. Без Shizuku доступны обзор, очистка мусора и мягкая выгрузка фоновых приложений.",
                    "Full mode works through Shizuku — ADB rights, no root needed. Without Shizuku: overview, junk cleanup and a gentle unload of background apps.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { UpdateSection(container().updates) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tr(
                    "Без рекламы и аналитики. В интернет ходит только за обновлениями — на GitHub, каждое обновление проверяется по подписи и SHA-256.",
                    "No ads, no analytics. Goes online only for updates — to GitHub; every update is checked by signature and SHA-256.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(REPO_URL, color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(REPO_URL))
                        android.widget.Toast.makeText(context, tr("Ссылка скопирована", "Link copied"), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.heightIn(min = MinTouch),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(" " + tr("Копировать", "Copy"))
                }
            }
        }
    }
}
