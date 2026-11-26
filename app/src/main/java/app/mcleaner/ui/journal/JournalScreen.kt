package app.mcleaner.ui.journal

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mcleaner.MCleanerApp
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.log.L
import app.mcleaner.data.LogShareServer
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.container
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** «Журнал»: what MCleaner did, and ways to get it off the head unit (phone by QR, file, folder). */
@Composable
fun JournalScreen() {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sink = (context.applicationContext as MCleanerApp).logSink
    var size by remember { mutableLongStateOf(sink.totalBytes()) }
    var busy by remember { mutableStateOf(false) }
    var tail by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    fun reloadTail() = scope.launch {
        sink.flush()
        size = sink.totalBytes()
        tail = withContext(Dispatchers.IO) {
            runCatching { sink.files().lastOrNull()?.readLines()?.takeLast(TAIL_LINES)?.reversed()?.joinToString("\n") }.getOrNull().orEmpty()
        }
    }
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
            }.onFailure { L.w("Export", "write to picked file failed", it) }.isSuccess
            busy = false
            toast(if (ok) tr("Журнал сохранён", "Journal saved") else tr("Не удалось записать файл", "Could not write the file"))
        }
    }
    LaunchedEffect(Unit) { reloadTail() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Text(tr("Журнал", "Journal"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr(
                    "MCleaner записывает, что делает и с каким результатом: команды, ускорения, обслуживание, обновления, Shizuku, ошибки и падения. Хранится до 8 МБ, старое удаляется. Сейчас: ${journalSize(size)}.\n" +
                        "Для разбора проблемы выгрузите архив: в нём журнал, сводка об устройстве и настройках, а с Shizuku — ещё системный журнал, память и накопитель.",
                    "MCleaner records what it does and with what result: commands, boosts, maintenance, updates, Shizuku, errors and crashes. Up to 8 MB is kept, old entries are removed. Now: ${journalSize(size)}.\n" +
                        "To analyse a problem, export the archive: the journal, a device and settings summary and, with Shizuku, the system log, memory and storage.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { sharing = true }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("На телефон по QR", "To phone via QR")) }
                OutlinedButton(
                    onClick = {
                        try {
                            picker.launch(c.logExporter.fileName())
                        } catch (_: android.content.ActivityNotFoundException) {
                            saveToFolder()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(if (busy) tr("Сохраняю…", "Saving…") else tr("Сохранить в файл…", "Save to file…")) }
                OutlinedButton(onClick = { saveToFolder() }, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("В папку MCleaner", "To MCleaner folder")) }
                TextButton(
                    onClick = {
                        sink.clear()
                        L.i("Export", "journal cleared by user")
                        reloadTail()
                    },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = MinTouch),
                ) { Text(tr("Очистить", "Clear")) }
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Последние записи (новые сверху)", "Latest entries (newest first)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { reloadTail() }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Обновить", "Refresh")) }
            }
            Text(
                tail.ifEmpty { tr("Пусто", "Empty") },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
        }
    }

    if (sharing) ShareDialog(LogShareServer(c.logExporter, context.cacheDir)) { sharing = false }
}

@Composable
private fun ShareDialog(server: LogShareServer, onClose: () -> Unit) {
    var session by remember { mutableStateOf<LogShareServer.Session?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var left by remember { mutableLongStateOf(LogShareServer.LIFETIME_MS) }
    DisposableEffect(Unit) {
        runCatching { session = server.start() }.onFailure {
            L.w("Share", "server start failed", it)
            error = it.message ?: it.javaClass.simpleName
        }
        onDispose { server.stop() }
    }
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        while (true) {
            left = s.stopsAtMs - System.currentTimeMillis()
            if (left <= 0) {
                onClose()
                break
            }
            delay(1_000)
        }
    }
    val url = session?.urls?.firstOrNull()
    val qr = remember(url) { url?.let { qrBitmap(it) } }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Остановить", "Stop")) } },
        title = { Text(tr("Журнал на телефон", "Journal to phone")) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (qr != null) Image(qr.asImageBitmap(), contentDescription = url, modifier = Modifier.size(260.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        error != null -> Text(tr("Не удалось запустить: $error", "Could not start: $error"))
                        session != null && url == null -> Text(
                            tr(
                                "Магнитола не подключена к Wi-Fi и не раздаёт его. Подключите её к точке доступа телефона или к той же сети и откройте окно снова.",
                                "The head unit is not on Wi-Fi and is not sharing it. Connect it to the phone's hotspot or the same network and open this again.",
                            ),
                        )
                        else -> {
                            Text(
                                tr(
                                    "1. Телефон — в той же сети, что магнитола (или магнитола подключена к точке доступа телефона).\n2. Наведите камеру на QR-код и откройте ссылку.\n3. Нажмите «Скачать журнал».",
                                    "1. The phone is on the same network as the head unit (or the head unit is on the phone's hotspot).\n2. Scan the QR code and open the link.\n3. Tap «Download journal».",
                                ),
                            )
                            session?.urls?.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
                            Text(
                                tr("Ссылка работает ещё ${left / 60_000}:${"%02d".format(left / 1000 % 60)} и пока открыто это окно.", "The link works for ${left / 60_000}:${"%02d".format(left / 1000 % 60)} more and while this window is open."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private const val TAIL_LINES = 150

private fun journalSize(b: Long): String = if (b < 1024 * 1024) tr("${b / 1024} КБ", "${b / 1024} KB") else Formatters.bytes(b)
