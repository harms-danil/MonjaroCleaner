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
    val unfrozen by vm.unfrozen.collectAsStateWithLifecycle()
    LaunchedEffect(unfrozen) {
        val (pkg, label) = unfrozen ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(tr("$label разморожено", "$label unfrozen"), actionLabel = tr("Открыть", "Open"), duration = androidx.compose.material3.SnackbarDuration.Long)
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.openApp(pkg, label)
        vm.consumeUnfrozen()
    }
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
                if (shizuku != ShizukuState.NOT_INSTALLED) ShizukuAutostartRows(vm, settings?.shizukuAutostart == true, full)
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
                    title = tr("Работать в фоне и запускаться с магнитолой", "Run in the background and start with the head unit"),
                    subtitle = tr(
                        "MCleaner стартует после включения магнитолы и остаётся в памяти (значок в шторке): так работают автоускорение, предупреждение о памяти и автозапуск Shizuku после сна. В шторке есть кнопка «Ускорить».",
                        "MCleaner starts when the head unit turns on and stays in memory (icon in the notification shade): this keeps auto-boost, the memory warning and Shizuku autostart after sleep working. The notification has a Boost button.",
                    ),
                    checked = settings?.keepRunning != false,
                    onChange = vm::setKeepRunning,
                )
                SwitchRow(
                    title = tr("Автоускорение", "Auto-boost"),
                    subtitle = tr("После загрузки (через 90 с) и пробуждения магнитолы (через 30 с), не чаще раза в 10 минут. Работает, только если Shizuku запущен.", "After boot (in 90 s) and head unit wake-up (in 30 s), at most once every 10 minutes. Works only while Shizuku is running."),
                    checked = settings?.autoBoost == true,
                    onChange = vm::setAutoBoost,
                )
                SwitchRow(
                    title = tr("Мягкое ускорение", "Gentle boost"),
                    subtitle = tr(
                        "Приложения, которые «Ускорить» не останавливает (навигатор, музыка, лаунчер, белый список), получают просьбу освободить память. Они продолжают работать, маршрут и музыка не прерываются.",
                        "Apps that Boost does not stop (navigator, music, launcher, whitelist) are asked to free memory. They keep running; route and music are not interrupted.",
                    ),
                    checked = settings?.gentleTrim != false,
                    onChange = vm::setGentleTrim,
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
                val frozenList by vm.frozenEntries.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(tr("Заморозка приложений: ${frozenList.size}", "Frozen apps: ${frozenList.size}"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr(
                                "Замороженное приложение отключено системой, но не удалено: его не видно в списке приложений, оно не запускается, не работает в фоне и не занимает память. Данные и настройки сохраняются, после разморозки оно работает как раньше. " +
                                    "Заморозить: «Приложения» → нажмите на приложение → «Заморозить» (только в полном режиме). Системные приложения, приложения машины, лаунчер, клавиатуру и белый список заморозить нельзя.",
                                "A frozen app is disabled by the system but not uninstalled: it is hidden from the app list, does not start, does not run in the background and takes no memory. Its data and settings are kept; after unfreezing it works as before. " +
                                    "To freeze: Apps → tap the app → Freeze (full mode only). System and car apps, the launcher, the keyboard and the whitelist cannot be frozen.",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = vm::unfreezeAll,
                        enabled = full && !settings?.frozen.isNullOrEmpty(),
                        modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text(tr("Разморозить всё", "Unfreeze all")) }
                }
                frozenList.forEach { (pkg, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label)
                            if (label != pkg) Text(pkg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.TextButton(onClick = { vm.unfreeze(pkg, label, open = false) }, enabled = full, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(tr("Разморозить", "Unfreeze"))
                        }
                        OutlinedButton(onClick = { vm.unfreeze(pkg, label, open = true) }, enabled = full, modifier = Modifier.heightIn(min = MinTouch)) {
                            Text(tr("Разморозить и открыть", "Unfreeze and open"))
                        }
                    }
                }
            }
            Section { BackupSection(vm) }
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
