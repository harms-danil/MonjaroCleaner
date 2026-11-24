package app.mcleaner.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.update.UpdateManager
import app.mcleaner.update.UpdateState
import app.mcleaner.ui.common.MinTouch

/** Global "update available" window, shown over any tab. */
@Composable
fun UpdateDialog(updates: UpdateManager) {
    val visible by updates.dialog.collectAsStateWithLifecycle()
    val state by updates.state.collectAsStateWithLifecycle()
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
        title = { Text("Доступно обновление ${release.version}") },
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
                        Text("Устанавливаю… Приложение закроется и обновится. Если появится системное окно — нажмите «Установить».")
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
        OutlinedButton(
            onClick = updates::manualCheck,
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing,
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text("Проверить обновления") }
    }
}
