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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { UpdateSection(container().updates) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { JournalSection() }
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

/** «Журнал»: size, export as a zip (file picker or /sdcard/MCleaner/logs), clear. */
@Composable
private fun JournalSection() {
    val c = container()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sink = (context.applicationContext as app.mcleaner.MCleanerApp).logSink
    var size by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(sink.totalBytes()) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
    fun saveToFolder() = scope.launch {
        busy = true
        val path = c.logExporter.writeToFolder()
        busy = false
        toast(path?.let { tr("Журнал сохранён: $it", "Journal saved: $it") } ?: tr("Нет доступа к памяти: включите полный режим", "No storage access: enable full mode"))
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { c.logExporter.writeZip(it) }
            }.onFailure { app.mcleaner.core.log.L.w("Export", "write to picked file failed", it) }.isSuccess
            busy = false
            toast(if (ok) tr("Журнал сохранён", "Journal saved") else tr("Не удалось записать файл", "Could not write the file"))
        }
    }
    Text(tr("Журнал", "Journal"), style = MaterialTheme.typography.titleMedium)
    Text(
        tr(
            "MCleaner записывает, что делает: команды, ускорения, обновления, ошибки. Хранится до 8 МБ, старое удаляется. " +
                "Выгрузка — zip для анализа; с Shizuku в него добавляются системный журнал, память и накопитель. Сейчас: ${app.mcleaner.ui.common.Formatters.bytes(size)}.",
            "MCleaner records what it does: commands, boosts, updates, errors. Up to 8 MB is kept, old entries are removed. " +
                "Export is a zip for analysis; with Shizuku it also includes the system log, memory and storage. Now: ${app.mcleaner.ui.common.Formatters.bytes(size)}.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Button(
            onClick = {
                try {
                    picker.launch(c.logExporter.fileName())
                } catch (_: android.content.ActivityNotFoundException) {
                    saveToFolder()
                }
            },
            enabled = !busy,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(if (busy) tr("Сохраняю…", "Saving…") else tr("Выгрузить журнал", "Export journal")) }
        androidx.compose.material3.OutlinedButton(onClick = { saveToFolder() }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(tr("В папку MCleaner", "To MCleaner folder"))
        }
        TextButton(
            onClick = {
                sink.clear()
                app.mcleaner.core.log.L.i("Export", "journal cleared by user")
                size = 0
            },
            enabled = !busy,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(tr("Очистить", "Clear")) }
    }
}
