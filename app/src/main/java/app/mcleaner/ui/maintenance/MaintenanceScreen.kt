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
    val trim by vm.trim.collectAsStateWithLifecycle()
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

            Card { CompileBlock(ui, full, running, vm) }

            Card { StorageBlock(ui, trim, full, running, vm) }

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
private fun CompileBlock(ui: MaintenanceUi, full: Boolean, running: Boolean, vm: MaintenanceViewModel) {
    Text(tr("Ускорение приложений", "App compilation"), style = MaterialTheme.typography.titleMedium)
    Text(
        if (ui.loading && ui.plan.isEmpty()) tr("Проверяю…", "Checking…")
        else tr(
            "Подготовлено хорошо: ${ui.optimal} из ${ui.total}. Скомпилировать стоит: ${count(ui.plan.size, "приложение", "приложения", "приложений", "app", "apps")}, выбрано ${ui.selected.size}.",
            "Well prepared: ${ui.optimal} of ${ui.total}. Worth compiling: ${ui.plan.size} apps, ${ui.selected.size} selected.",
        ),
    )
    Text(
        tr(
            "Белый список и лаунчер — полностью (навигатор, музыка, рабочий стол открываются и работают быстрее), остальные свои приложения — по профилю использования. Займёт десятки МБ на приложение. Снимите галочку, чтобы пропустить приложение.",
            "Whitelist and launcher fully (navigator, music, home screen start and run faster), other user apps by usage profile. Takes tens of MB per app. Untick an app to skip it.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (ui.plan.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.selectAll(true) }, enabled = !running, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Выбрать все", "Select all")) }
            TextButton(onClick = { vm.selectAll(false) }, enabled = !running, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Снять все", "Select none")) }
        }
    }
    ui.plan.forEach { step ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(enabled = !running) { vm.toggle(step.packageName) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(checked = step.packageName in ui.selected, onCheckedChange = { vm.toggle(step.packageName) }, enabled = !running)
            Text(ui.labels[step.packageName] ?: step.packageName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                statusName(ui.statuses[step.packageName]) + " → " + if (step.mode == Dexopt.FULL) tr("полностью", "fully") else tr("по профилю", "by profile"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    OutlinedButton(onClick = vm::compileOnly, enabled = full && !running && ui.selected.isNotEmpty(), modifier = Modifier.heightIn(min = MinTouch)) {
        Text(tr("Скомпилировать выбранные", "Compile selected"))
    }
    ui.lastCompile?.let { r ->
        androidx.compose.material3.HorizontalDivider()
        Text(tr("Результат", "Result"), style = MaterialTheme.typography.titleSmall)
        Line(tr("Подготовлено хорошо", "Well prepared"), "${r.optimalBefore} → ${r.optimalAfter} " + tr("из ${r.total}", "of ${r.total}"))
        Line(tr("Заняло места", "Space used"), Formatters.bytes(r.spaceUsedBytes))
        Line(tr("Время", "Time"), tr("${r.durationMs / 1000} с", "${r.durationMs / 1000} s"))
        r.outcomes.forEach { o ->
            Line(
                ui.labels[o.packageName] ?: o.packageName,
                if (o.ok) statusName(o.before) + " → " + statusName(o.after) else tr("ошибка", "failed"),
                if (o.ok) null else Palette.Warn,
            )
        }
    }
}

/** Dexopt status in plain words. */
private fun statusName(s: String?): String = when {
    s == null -> "—"
    s == "speed" || s == "everything" -> tr("полностью", "fully")
    s == "speed-profile" -> tr("по профилю", "by profile")
    Dexopt.rank(s) >= Dexopt.rank("quicken") -> tr("облегчённо", "light")
    else -> tr("не скомпилировано", "not compiled")
}

@Composable
private fun StorageBlock(ui: MaintenanceUi, trim: app.mcleaner.domain.TrimProgress?, full: Boolean, running: Boolean, vm: MaintenanceViewModel) {
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
    if (full && s != null && s.life == null && s.reserve == null) {
        Text(
            tr(
                "Износ контроллер не отдаёт: файлы eMMC и UFS на этой прошивке закрыты для Shizuku (проверенные пути записаны в журнал). Без root его не узнать.",
                "The controller does not expose wear: eMMC and UFS files are closed to Shizuku on this firmware (checked paths are in the journal). Not available without root.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Who writes the most: wear comes from writes, and this is available on Android 9 through storaged.
    if (s != null && s.topWriters.isNotEmpty()) {
        androidx.compose.material3.HorizontalDivider()
        Text(tr("Кто больше всех пишет на накопитель", "Biggest writers to storage"), style = MaterialTheme.typography.titleSmall)
        s.totalWrittenBytes?.let {
            Text(tr("Всего записано приложениями за период, который хранит система (около суток): ${Formatters.bytes(it)}", "Written by apps over the period the system keeps (about a day): ${Formatters.bytes(it)}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.topWriters.forEach { w ->
            Line(ui.labels[w.packageName] ?: w.packageName, tr("записано ${Formatters.bytes(w.writtenBytes)}, прочитано ${Formatters.bytes(w.readBytes)}", "written ${Formatters.bytes(w.writtenBytes)}, read ${Formatters.bytes(w.readBytes)}"))
        }
    }

    // Trim: what was before, progress, result.
    androidx.compose.material3.HorizontalDivider()
    Text(tr("Обслуживание накопителя (TRIM)", "Storage trim (TRIM)"), style = MaterialTheme.typography.titleSmall)
    Text(
        tr(
            "Сообщает накопителю, какие блоки свободны, — запись со временем не замедляется.",
            "Tells the storage which blocks are free, so writes do not slow down over time.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val before = s?.trimsSinceBoot.orEmpty().filter { it !is app.mcleaner.core.maintenance.TrimEvent.Started }
    Line(
        tr("До запуска", "Before"),
        before.lastOrNull()?.let { tr("с включения: ", "since start: ") + trimText(it) } ?: tr("с включения не выполнялось", "not run since start"),
    )
    s?.systemMaintenance?.let { Line(tr("Обслуживание самим Android", "Android's own maintenance"), it) }
    trim?.let { t ->
        if (t.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(tr("Идёт обслуживание… Система сообщает:", "Trim in progress… The system reports:"))
        } else {
            Text(tr("Результат", "Result"), style = MaterialTheme.typography.titleSmall)
        }
        if (t.events.isEmpty() && !t.running) {
            Text(tr("Система ничего не сообщила о результате.", "The system reported no result."), color = Palette.Warn)
        }
        t.events.forEach { e -> Text("• " + trimText(e), color = if (e is app.mcleaner.core.maintenance.TrimEvent.Failed) Palette.Warn else MaterialTheme.colorScheme.onSurface) }
        if (t.timedOut) Text(tr("Ждали 3 минуты: система ещё не закончила, итог появится в журнале.", "Waited 3 minutes: the system has not finished; the result will be in the journal."), color = Palette.Warn)
    }
    OutlinedButton(onClick = vm::trim, enabled = full && !running && trim?.running != true, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Обслужить накопитель", "Trim storage")) }

    // Speed tests.
    androidx.compose.material3.HorizontalDivider()
    Text(tr("Скорость", "Speed"), style = MaterialTheme.typography.titleSmall)
    val last = ui.speed.firstOrNull()
    if (ui.testing) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(tr("Тестирую: пишу 256 МБ, затем читаю крупный файл…", "Testing: writing 256 MB, then reading a large file…"))
    } else if (last != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column {
                Text(tr("Запись", "Write"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("${mbps(last.writeMBps)} МБ/с", "${mbps(last.writeMBps)} MB/s"), style = MaterialTheme.typography.headlineSmall)
            }
            Column {
                Text(tr("Чтение", "Read"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(last.readMBps?.let { tr("${mbps(it)} МБ/с", "${mbps(it)} MB/s") } ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
            Column {
                Text(tr("Когда", "When"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last.timeMs)), style = MaterialTheme.typography.titleMedium)
            }
        }
        if ((last.readMBps ?: 0.0) > app.mcleaner.domain.Maintenance.CACHED_READ_MBPS) {
            Text(tr("Чтение подозрительно быстрое — файл, видимо, был в памяти, цифра завышена.", "Read is suspiciously fast — the file was probably in memory; the number is too high."), color = Palette.Warn)
        }
        if (ui.speed.size > 1) {
            Text(tr("Предыдущие замеры (запись / чтение):", "Earlier tests (write / read):"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ui.speed.drop(1).take(5).forEach { p ->
                Line(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.timeMs)), "${mbps(p.writeMBps)} / ${p.readMBps?.let(::mbps) ?: "—"}" + tr(" МБ/с", " MB/s"))
            }
        }
    } else {
        Text(tr("Ещё не тестировали.", "Not tested yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        tr(
            "Запись: 256 МБ в папку MCleaner, затем удаляются. Чтение: самый крупный APK приложения, которое сейчас не запущено, — его нет в памяти, поэтому замер честный.",
            "Write: 256 MB into MCleaner's folder, then deleted. Read: the largest APK of an app that is not running — it is not in memory, so the number is honest.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = vm::speedTest, enabled = !ui.testing, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Тест скорости", "Speed test")) }
}

private fun trimText(e: app.mcleaner.core.maintenance.TrimEvent): String = when (e) {
    is app.mcleaner.core.maintenance.TrimEvent.Started -> tr("начато: ${e.path}", "started: ${e.path}")
    is app.mcleaner.core.maintenance.TrimEvent.Trimmed -> tr(
        "${e.path}: освобождено для записи ${Formatters.bytes(e.bytes)} за ${mbps(e.ms / 100 / 10.0)} с",
        "${e.path}: ${Formatters.bytes(e.bytes)} freed for writing in ${mbps(e.ms / 100 / 10.0)} s",
    )
    is app.mcleaner.core.maintenance.TrimEvent.Failed ->
        if (app.mcleaner.core.maintenance.VoldTrimLog.unsupported(e)) {
            tr("${e.path}: накопитель не поддерживает TRIM — обслуживать нечего, это не ошибка", "${e.path}: the storage does not support TRIM — nothing to do, not an error")
        } else {
            tr("${e.path}: не выполнено — ${e.reason}", "${e.path}: failed — ${e.reason}")
        }
}

internal fun mbps(v: Double): String = tr(v.toString().replace('.', ','), v.toString())

private fun hours(ms: Long): String {
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    return tr("$h ч $m мин", "$h h $m min")
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
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
