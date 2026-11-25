package app.mcleaner.ui.update

import app.mcleaner.core.i18n.tr
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
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
        // «Позже» only hides the window: a download or install keeps going (and has its own timeout).
        onDismissRequest = updates::later,
        title = { Text(if (rollback) tr("Установка версии ${release.version}", "Installing version ${release.version}") else tr("Доступно обновление ${release.version}", "Update available: ${release.version}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Установлена версия ${updates.currentVersion}.", "Installed version: ${updates.currentVersion}."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.changes.isNotBlank()) {
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Text(release.changes.replace("### ", "").replace("`", ""))
                    }
                }
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Text(tr("Скачиваю… ${(s.progress * 100).toInt()}%", "Downloading… ${(s.progress * 100).toInt()}%"))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Installing -> {
                        Text(
                            if (rollback) tr("Устанавливаю… MCleaner закроется. Если система не разрешит поставить старую версию поверх, " +
                                "MCleaner переустановится и откроется сам через несколько секунд.",
                                "Installing… MCleaner will close. If the system does not allow installing an older version over the current one, " +
                                "MCleaner will reinstall and reopen by itself in a few seconds.")
                            else tr("Устанавливаю… Приложение закроется и обновится. Если появится системное окно — нажмите «Установить».", "Installing… The app will close and update. If a system window appears, tap \"Install\"."),
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.NeedsPermission -> Text(
                        tr("Без Shizuku Android просит разрешить MCleaner устанавливать приложения. " +
                            "Нажмите «Разрешить», включите переключатель, вернитесь и нажмите «Установить».",
                            "Without Shizuku, Android asks you to allow MCleaner to install apps. " +
                            "Tap \"Allow\", turn on the switch, come back and tap \"Install\"."),
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
                    OutlinedButton(onClick = updates::openInstallPermissionScreen, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Разрешить", "Allow")) }
                }
                Button(onClick = updates::downloadAndInstall, enabled = !busy, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(
                        when (state) {
                            is UpdateState.Available -> tr("Скачать и установить", "Download and install")
                            is UpdateState.Failed -> tr("Повторить", "Retry")
                            else -> tr("Установить", "Install")
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = updates::later, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Позже", "Later")) }
        },
    )
}

/** «Настройки» → version, status and the manual check button. */
@Composable
fun UpdateSection(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    var showVersions by remember { mutableStateOf(false) }
    if (showVersions) VersionsDialog(updates, onDismiss = { showVersions = false })
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
    val status = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> tr("Проверяю…", "Checking…")
        UpdateState.UpToDate -> tr("Установлена последняя версия", "You have the latest version")
        is UpdateState.CheckFailed -> s.message
        is UpdateState.Available -> tr("Доступна версия ${s.release.version}", "Version ${s.release.version} available")
        is UpdateState.Downloading -> tr("Скачиваю ${s.release.version}… ${(s.progress * 100).toInt()}%", "Downloading ${s.release.version}… ${(s.progress * 100).toInt()}%")
        is UpdateState.NeedsPermission -> tr("Нужно разрешение на установку", "Install permission required")
        is UpdateState.Installing -> tr("Устанавливаю ${s.release.version}…", "Installing ${s.release.version}…")
        is UpdateState.Failed -> s.message
    }
    val hasUpdate = state is UpdateState.Available || state is UpdateState.NeedsPermission || state is UpdateState.Failed
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(tr("Версия ${updates.currentVersion}", "Version ${updates.currentVersion}"), style = MaterialTheme.typography.titleMedium)
            status?.let {
                Text(
                    it,
                    color = if (state is UpdateState.CheckFailed || state is UpdateState.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasUpdate) {
            Button(onClick = updates::showDialog, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Обновить", "Update")) }
        }
        TextButton(onClick = { showLicenses = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Лицензии", "Licenses")) }
        OutlinedButton(onClick = { showVersions = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Другие версии", "Other versions")) }
        OutlinedButton(
            onClick = updates::manualCheck,
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(tr("Проверить обновления", "Check for updates")) }
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
        title = { Text(tr("Версии MCleaner", "MCleaner versions")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("Установлена ${updates.currentVersion}. На устройстве хранятся ${UpdateManager.KEEP_VERSIONS} последних скачанных версий.",
                        "Installed: ${updates.currentVersion}. The device keeps the last ${UpdateManager.KEEP_VERSIONS} downloaded versions."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!updates.canRollback) {
                    Text(tr("Старые версии ставятся только с запущенным Shizuku.", "Older versions can only be installed while Shizuku is running."), color = MaterialTheme.colorScheme.error)
                }
                versions.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when {
                    versions.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    versions.entries.isEmpty() -> Text(tr("Других версий нет", "No other versions"))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(versions.entries, key = { it.version }) { e ->
                            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.version, style = MaterialTheme.typography.titleMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (e.newer) Badge(tr("новее", "newer"), BadgeKind.GOOD) else Badge(tr("старее", "older"), BadgeKind.WARN)
                                        if (e.onDevice) Badge(tr("на устройстве", "on device"), BadgeKind.INFO)
                                        if (e.release != null) Badge("GitHub", BadgeKind.NEUTRAL)
                                    }
                                    if (!e.installable) {
                                        Text(
                                            tr("Не ставится: до ${VersionCatalog.MIN_INSTALLABLE} не умеет обновляться на Android 9",
                                                "Not offered: before ${VersionCatalog.MIN_INSTALLABLE} it cannot update itself on Android 9"),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { if (e.newer) { updates.installVersion(e); onDismiss() } else confirm = e },
                                    enabled = e.installable && (e.newer || updates.canRollback),
                                    modifier = Modifier.heightIn(min = MinTouch),
                                ) { Text(tr("Установить", "Install")) }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Закрыть", "Close")) } },
    )

    confirm?.let { e ->
        val restores = VersionCatalog.restoresSettings(e.version)
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("Установить старую версию ${e.version}?", "Install older version ${e.version}?")) },
            text = {
                Text(
                    tr("Сначала попробую поставить её поверх — тогда всё сохранится. Если прошивка не разрешит, " +
                        "MCleaner удалится и установится заново, после чего Shizuku ещё раз попросит разрешение.\n\n",
                        "I will first try to install it over the current version, which keeps everything. If the firmware does not allow it, " +
                        "MCleaner will be uninstalled and installed again, after which Shizuku will ask for permission once more.\n\n") +
                        if (restores) tr("Настройки (белый список, замороженные, автоускорение) вернутся из резервной копии /sdcard/MCleaner/settings.json.",
                            "Settings (whitelist, frozen apps, auto-boost) will be restored from the backup /sdcard/MCleaner/settings.json.")
                        else tr("Версия ${e.version} не умеет восстанавливать настройки: при переустановке белый список, замороженные и автоускорение " +
                            "станут стандартными. Резервная копия останется и вернётся при обновлении на ${VersionCatalog.RESTORE_SINCE} или новее.",
                            "Version ${e.version} cannot restore settings: on reinstall the whitelist, frozen apps and auto-boost " +
                            "will be reset to defaults. The backup stays and will be restored when you update to ${VersionCatalog.RESTORE_SINCE} or newer."),
                )
            },
            confirmButton = {
                Button(onClick = { updates.installVersion(e); confirm = null; onDismiss() }, modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(tr("Установить ${e.version}", "Install ${e.version}"))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

/** Third-party notices bundled in assets/licenses (Shizuku is redistributed under Apache-2.0). */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = remember {
        val dir = "licenses"
        runCatching {
            context.assets.list(dir).orEmpty().sorted().joinToString("\n\n────────\n\n") { name ->
                context.assets.open("$dir/$name").bufferedReader().use { it.readText() }
            }
        }.getOrDefault("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Лицензии", "Licenses")) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    tr(
                        "MCleaner использует AndroidX и Jetpack Compose (Apache 2.0), библиотеку Shizuku API и встроенное приложение Shizuku (Apache 2.0), Kotlin и kotlinx.coroutines (Apache 2.0).",
                        "MCleaner uses AndroidX and Jetpack Compose (Apache 2.0), the Shizuku API library and the bundled Shizuku app (Apache 2.0), Kotlin and kotlinx.coroutines (Apache 2.0).",
                    ),
                )
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Закрыть", "Close")) } },
    )
}
