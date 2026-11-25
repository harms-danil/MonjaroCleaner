package app.mcleaner.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.update.VersionCatalog
import app.mcleaner.core.update.VersionEntry
import app.mcleaner.ui.common.Badge
import app.mcleaner.ui.common.BadgeKind
import app.mcleaner.update.UpdateManager
import app.mcleaner.update.UpdateState
import app.mcleaner.ui.common.MinTouch

/** Global "update available" window, shown over any tab. */
@Composable
fun UpdateDialog(updates: UpdateManager) {
    val visible by updates.dialog.collectAsStateWithLifecycle()
    val state by updates.state.collectAsStateWithLifecycle()
    val rollback by updates.rollback.collectAsStateWithLifecycle()
    if (!visible) return
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.NeedsPermission -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        else -> return
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) updates.later() },
        title = { Text(if (rollback) "Установка версии ${release.version}" else "Доступно обновление ${release.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Установлена версия ${updates.currentVersion}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.changes.isNotBlank()) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Text(release.changes.replace("### ", "").replace("`", ""))
                    }
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Text("Скачиваю… ${(s.progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Installing -> {
                        Text(
                            if (rollback) "Устанавливаю… MCleaner закроется. Если система не разрешит поставить старую версию поверх, " +
                                "MCleaner переустановится и откроется сам через несколько секунд."
                            else "Устанавливаю… Приложение закроется и обновится. Если появится системное окно — нажмите «Установить».",
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "Без Shizuku Android просит разрешить MCleaner устанавливать приложения. " +
                            "Нажмите «Разрешить», включите переключатель, вернитесь и нажмите «Установить».",
                        color = MaterialTheme.colorScheme.error,
                    )
                    is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state is UpdateState.NeedsPermission) {
                    OutlinedButton(onClick = updates::openInstallPermissionScreen, modifier = Modifier.heightIn(min = MinTouch)) { Text("Разрешить") }
                }
                Button(onClick = updates::downloadAndInstall, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(
                        when (state) {
                            is UpdateState.Available -> "Скачать и установить"
                            is UpdateState.Failed -> "Повторить"
                            else -> "Установить"
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = updates::later, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) { Text("Позже") }
        },
    )
}

/** «Настройки» → version, status and the manual check button. */
@Composable
fun UpdateSection(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    var showVersions by remember { mutableStateOf(false) }
    if (showVersions) VersionsDialog(updates, onDismiss = { showVersions = false })
    val status = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> "Проверяю…"
        UpdateState.UpToDate -> "Установлена последняя версия"
        is UpdateState.CheckFailed -> s.message
        is UpdateState.Available -> "Доступна версия ${s.release.version}"
        is UpdateState.Downloading -> "Скачиваю ${s.release.version}… ${(s.progress * 100).toInt()}%"
        is UpdateState.NeedsPermission -> "Нужно разрешение на установку"
        is UpdateState.Installing -> "Устанавливаю ${s.release.version}…"
        is UpdateState.Failed -> s.message
    }
    val hasUpdate = state is UpdateState.Available || state is UpdateState.NeedsPermission || state is UpdateState.Failed
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Версия ${updates.currentVersion}", style = MaterialTheme.typography.titleMedium)
            status?.let {
                Text(
                    it,
                    color = if (state is UpdateState.CheckFailed || state is UpdateState.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasUpdate) {
            Button(onClick = updates::showDialog, modifier = Modifier.heightIn(min = MinTouch)) { Text("Обновить") }
        }
        OutlinedButton(onClick = { showVersions = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text("Другие версии") }
        OutlinedButton(
            onClick = updates::manualCheck,
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text("Проверить обновления") }
    }
}

/** «Другие версии»: downloaded ones and everything on GitHub; older ones roll back (Shizuku only). */
@Composable
private fun VersionsDialog(updates: UpdateManager, onDismiss: () -> Unit) {
    val versions by updates.versions.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<VersionEntry?>(null) }
    LaunchedEffect(Unit) { updates.loadVersions() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Версии MCleaner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Установлена ${updates.currentVersion}. На устройстве хранятся ${UpdateManager.KEEP_VERSIONS} последних скачанных версий.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!updates.canRollback) {
                    Text("Старые версии ставятся только с запущенным Shizuku.", color = MaterialTheme.colorScheme.error)
                }
                versions.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when {
                    versions.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    versions.entries.isEmpty() -> Text("Других версий нет")
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(versions.entries, key = { it.version }) { e ->
                            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.version, style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (e.newer) Badge("новее", BadgeKind.GOOD) else Badge("старее", BadgeKind.WARN)
                                        if (e.onDevice) Badge("на устройстве", BadgeKind.INFO)
                                        if (e.release != null) Badge("GitHub", BadgeKind.NEUTRAL)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { if (e.newer) { updates.installVersion(e); onDismiss() } else confirm = e },
                                    enabled = e.newer || updates.canRollback,
                                    modifier = Modifier.heightIn(min = MinTouch),
                                ) { Text("Установить") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text("Закрыть") } },
    )

    confirm?.let { e ->
        val restores = VersionCatalog.restoresSettings(e.version)
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Установить старую версию ${e.version}?") },
            text = {
                Text(
                    "Сначала попробую поставить её поверх — тогда всё сохранится. Если прошивка не разрешит, " +
                        "MCleaner удалится и установится заново, после чего Shizuku ещё раз попросит разрешение.\n\n" +
                        if (restores) "Настройки (белый список, замороженные, автоускорение) вернутся из резервной копии /sdcard/MCleaner/settings.json."
                        else "Версия ${e.version} не умеет восстанавливать настройки: при переустановке белый список, замороженные и автоускорение " +
                            "станут стандартными. Резервная копия останется и вернётся при обновлении на ${VersionCatalog.RESTORE_SINCE} или новее.",
                )
            },
            confirmButton = {
                Button(onClick = { updates.installVersion(e); confirm = null; onDismiss() }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text("Установить ${e.version}")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text("Отмена") } },
        )
    }
}
