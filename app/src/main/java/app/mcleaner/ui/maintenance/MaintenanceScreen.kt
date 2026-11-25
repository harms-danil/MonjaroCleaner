package app.mcleaner.ui.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.i18n.count
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.maintenance.Dexopt
import app.mcleaner.core.maintenance.ReserveState
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.containerViewModel
import app.mcleaner.ui.theme.Palette
import java.text.DateFormat
import java.util.Date

@Composable
fun MaintenanceScreen() {
    val vm = containerViewModel { MaintenanceViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val progress by vm.compile.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(null)
    val full = shizuku == ShizukuState.READY
    val running = progress != null
    val snackbar = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(full) { vm.refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Обслуживание", "Maintenance"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr(
                                "Android делает это сам только когда устройство простаивает на зарядке — магнитола так почти не бывает. Лучше запускать на стоянке: несколько минут процессор будет загружен.",
                                "Android does this on its own only when the device is idle and charging — a head unit almost never is. Best run while parked: the CPU is busy for a few minutes.",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        settings?.lastMaintenanceMs?.let {
                            Text(tr("Последний раз: ", "Last run: ") + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (running) {
                        OutlinedButton(onClick = vm::stop, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Остановить", "Stop")) }
                    } else {
                        Button(onClick = vm::runAll, enabled = full && !ui.loading, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Обслужить сейчас", "Run now")) }
                    }
                }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                    Text(tr("Компилирую ${p.done + 1} из ${p.total}: ", "Compiling ${p.done + 1} of ${p.total}: ") + (p.current?.let { ui.labels[it] ?: it } ?: ""))
                }
                SwitchLine(
                    tr("Раз в неделю автоматически", "Weekly, automatically"),
                    tr("Не раньше чем через 10 минут после включения магнитолы, только с Shizuku.", "Not earlier than 10 minutes after the head unit starts, Shizuku only."),
                    settings?.weeklyMaintenance == true,
                    vm::setWeekly,
                )
                if (!full) Text(tr("Компиляция, накопитель и «Без фона» работают в полном режиме (Shizuku).", "Compiling, storage and «No background» need full mode (Shizuku)."), color = Palette.Warn)
            }

            Card {
                Text(tr("Ускорение приложений", "App compilation"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ui.loading) tr("Проверяю…", "Checking…")
                    else tr(
                        "Подготовлено хорошо: ${ui.optimal} из ${ui.total}. Скомпилировать стоит: ${count(ui.plan.size, "приложение", "приложения", "приложений", "app", "apps")}.",
                        "Well prepared: ${ui.optimal} of ${ui.total}. Worth compiling: ${ui.plan.size} apps.",
                    ),
                )
                Text(
                    tr(
                        "Белый список и лаунчер — полностью (навигатор, музыка, рабочий стол открываются и работают быстрее), остальные свои приложения — по профилю использования. Займёт десятки МБ на приложение.",
                        "Whitelist and launcher fully (navigator, music, home screen start and run faster), other user apps by usage profile. Takes tens of MB per app.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.plan.take(12).forEach { s ->
                    Line(ui.labels[s.packageName] ?: s.packageName, if (s.mode == Dexopt.FULL) tr("полностью", "fully") else tr("по профилю", "by profile"))
                }
                if (ui.plan.size > 12) Text(tr("…и ещё ${ui.plan.size - 12}", "…and ${ui.plan.size - 12} more"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = vm::compileOnly, enabled = full && !running && ui.plan.isNotEmpty(), modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(tr("Скомпилировать", "Compile"))
                }
            }

            Card { StorageBlock(ui, full, running, vm) }

            Card {
                Text(tr("Без фона", "No background"), style = MaterialTheme.typography.titleMedium)
                Text(
                    tr(
                        "Приложение работает, пока открыто; в фоне его службы, задачи и будильники не запускаются — то же «Ограничение фоновой работы», что в настройках Android. Перестают приходить уведомления и фоновые обновления. Отменяется кнопкой «Вернуть».",
                        "The app works while open; in the background its services, jobs and alarms do not start — the same «background restriction» as in Android settings. Notifications and background updates stop. Undo with «Restore».",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.suggestions.isNotEmpty()) {
                    Text(tr("Чаще всего просыпаются и останавливаются «Ускорить»:", "Most often woken up and stopped by Boost:"))
                    ui.suggestions.forEach { (pkg, times) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.labels[pkg] ?: pkg, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tr("$times раз", "$times times"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                            OutlinedButton(onClick = { vm.setNoBackground(pkg, true) }, enabled = full, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Ограничить", "Restrict")) }
                        }
                    }
                }
                if (ui.noBackground.isEmpty()) Text(tr("Список пуст", "The list is empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ui.noBackground.forEach { e ->
                    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (e.restricted) {
                                true -> tr("ограничено", "restricted")
                                false -> tr("не применено", "not applied")
                                null -> "—"
                            },
                            color = if (e.restricted == true) Palette.Good else Palette.Warn,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        TextButton(onClick = { vm.setNoBackground(e.packageName, false) }, enabled = full, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Вернуть", "Restore")) }
                    }
                }
                OutlinedButton(onClick = { picking = true }, enabled = full, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("+ Добавить", "+ Add")) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Закрыть", "Close")) } },
            title = { Text(tr("Ограничить фоновую работу", "Restrict background work")) },
            text = {
                if (ui.candidates.isEmpty()) {
                    Text(
                        tr(
                            "Подходящих приложений нет: системные, приложения машины, белый список, лаунчер, клавиатура и службы кнопок руля ограничивать нельзя.",
                            "No suitable apps: system and car apps, the whitelist, launcher, keyboard and steering-button services cannot be restricted.",
                        ),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(ui.candidates, key = { it.packageName }) { a ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable {
                                vm.setNoBackground(a.packageName, true)
                                picking = false
                            }.padding(vertical = 6.dp),
                        ) {
                            Text(a.label)
                            Text(a.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun StorageBlock(ui: MaintenanceUi, full: Boolean, running: Boolean, vm: MaintenanceViewModel) {
    val s = ui.storage
    Text(tr("Накопитель", "Storage"), style = MaterialTheme.typography.titleMedium)
    s?.disk?.partitions?.forEach { p ->
        val name = when (p.name) {
            "Data" -> tr("Данные и приложения", "Data and apps")
            "System" -> tr("Система", "System")
            else -> p.name
        }
        Line(name, tr("свободно ${Formatters.bytes(p.freeBytes)} из ${Formatters.bytes(p.totalBytes)}", "${Formatters.bytes(p.freeBytes)} free of ${Formatters.bytes(p.totalBytes)}"))
    }
    s?.disk?.writeLatencyMs?.let { Line(tr("Задержка записи (замер системы)", "Write latency (system test)"), tr("$it мс", "$it ms")) }
    s?.disk?.recentWriteKbps?.let { Line(tr("Недавняя скорость записи (замер системы)", "Recent write speed (system)"), tr("${it / 1024} МБ/с", "${it / 1024} MB/s")) }
    s?.let { Line(tr("Работает с включения", "Up since start"), hours(it.uptimeMs)) }
    s?.life?.let { Line(tr("Износ (оценка контроллера)", "Wear (controller estimate)"), "${it.usedFromPercent}–${it.usedToPercent}%") }
    s?.reserve?.let {
        Line(
            tr("Резерв блоков", "Spare blocks"),
            when (it) {
                ReserveState.NORMAL -> tr("в норме", "normal")
                ReserveState.WARNING -> tr("израсходован на 80% — пора думать о замене", "80% used — consider replacement")
                ReserveState.URGENT -> tr("почти исчерпан", "almost exhausted")
            },
        )
    }
    if (s?.readBytes != null && s.writtenBytes != null) {
        Line(tr("С включения прочитано / записано", "Since start read / written"), "${Formatters.bytes(s.readBytes)} / ${Formatters.bytes(s.writtenBytes)}")
    }
    if (full && s != null && s.life == null) {
        Text(tr("Износ и объём записи прошивка не показывает.", "The firmware does not expose wear and write totals."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ui.speed.firstOrNull()?.let { last ->
        Line(
            tr("Тест записи", "Write test"),
            tr("${mbps(last.writeMBps)} МБ/с", "${mbps(last.writeMBps)} MB/s") + (if (ui.speed.size > 1) tr(" (до этого: ", " (before: ") + ui.speed.drop(1).take(4).joinToString { mbps(it.writeMBps) } + ")" else ""),
        )
    }
    Text(
        tr(
            "Тест пишет 256 МБ в папку MCleaner и удаляет их. Скорость чтения без root честно не измерить: только что записанный файл Android отдаёт из памяти.",
            "The test writes 256 MB into MCleaner's folder and deletes it. Read speed cannot be measured honestly without root: Android serves a just-written file from memory.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::trim, enabled = full && !running, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Обслужить накопитель", "Trim storage")) }
        OutlinedButton(onClick = vm::speedTest, enabled = !ui.testing, modifier = Modifier.heightIn(min = MinTouch)) {
            Text(if (ui.testing) tr("Тестирую…", "Testing…") else tr("Тест скорости записи", "Write speed test"))
        }
    }
}

internal fun mbps(v: Double): String = tr(v.toString().replace('.', ','), v.toString())

private fun hours(ms: Long): String {
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    return tr("$h ч $m мин", "$h h $m min")
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
