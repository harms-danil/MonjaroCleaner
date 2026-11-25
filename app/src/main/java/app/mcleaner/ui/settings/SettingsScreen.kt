package app.mcleaner.ui.settings

import app.mcleaner.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Badge
import app.mcleaner.ui.common.BadgeKind
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.containerViewModel
import app.mcleaner.ui.common.title
import app.mcleaner.ui.common.container
import app.mcleaner.ui.update.UpdateSection

@Composable
fun SettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val whitelist by vm.whitelistEntries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val full = shizuku == ShizukuState.READY

    LaunchedEffect(Unit) { vm.refreshShizuku() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Полный режим (Shizuku)", "Full mode (Shizuku)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Badge(shizuku.title(), if (full) BadgeKind.GOOD else BadgeKind.WARN)
                }
                ShizukuInstallRow(container().shizukuInstaller, showButton = shizuku == ShizukuState.NOT_INSTALLED)
                if (!full) {
                    ShizukuGuide()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = vm::requestPermission,
                            enabled = shizuku == ShizukuState.NO_PERMISSION,
                            modifier = Modifier.heightIn(min = MinTouch),
                        ) { Text(tr("Запросить разрешение", "Request permission")) }
                        OutlinedButton(onClick = vm::refreshShizuku, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Проверить снова", "Check again")) }
                    }
                } else {
                    Text(tr("Приложения останавливаются и кэш чистится от имени системы (ADB). Root не нужен.", "Apps are stopped and cache is cleared with system privileges (ADB). No root needed."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Section { PermissionsSection(container(), full) }
            Section { AppearanceSection(container().ui) }
            Section {
                SwitchRow(
                    title = tr("Автоускорение", "Auto-boost"),
                    subtitle = tr("После загрузки (через 90 с) и пробуждения магнитолы (через 30 с), не чаще раза в 10 минут. Работает, только если Shizuku запущен.", "After boot (in 90 s) and head unit wake-up (in 30 s), at most once every 10 minutes. Works only while Shizuku is running."),
                    checked = settings?.autoBoost == true,
                    onChange = vm::setAutoBoost,
                )
                SwitchRow(
                    title = tr("Останавливать Google-сервисы", "Stop Google services"),
                    subtitle = tr("Освобождает больше памяти, но Google-сервисы сразу перезапускаются, а Play и зависящие от него приложения могут сбоить.", "Frees more memory, but Google services restart right away, and Play and apps that depend on it may misbehave."),
                    checked = settings?.stopGoogle == true,
                    onChange = vm::setStopGoogle,
                )
                AutomationRows(settings, full, vm)
            }
            Section {
                WhitelistSection(
                    entries = whitelist,
                    candidates = vm::candidates,
                    onAdd = { vm.setWhitelisted(it, true) },
                    onRemove = { vm.setWhitelisted(it, false) },
                    onReset = vm::resetWhitelist,
                )
            }
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Замороженные приложения: ${settings?.frozen?.size ?: 0}", "Frozen apps: ${settings?.frozen?.size ?: 0}"))
                    }
                    OutlinedButton(
                        onClick = vm::unfreezeAll,
                        enabled = full && !settings?.frozen.isNullOrEmpty(),
                        modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text(tr("Разморозить всё", "Unfreeze all")) }
                }
            }
            Section { BackupSection(vm) }
            Section { UpdateSection(container().updates) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
