package app.mcleaner.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.policy.LowMemoryGate
import app.mcleaner.data.Settings
import app.mcleaner.ui.common.MinTouch

private val SCHEDULE_CHOICES = listOf(0, 15, 30, 60)

/** «Ускорять по расписанию» and «Предупреждать о нехватке памяти», under the auto-boost switch. */
@Composable
fun AutomationRows(settings: Settings?, full: Boolean, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Ускорять по расписанию", "Scheduled boost"))
        Text(
            tr(
                "Пока магнитола включена. Без уведомлений, результат — в «Истории». Нужен Shizuku.",
                "While the head unit is on. No notifications, results go to History. Needs Shizuku.",
            ) + if (!full && (settings?.scheduleMinutes ?: 0) > 0) tr(" Сейчас Shizuku не активен.", " Shizuku is not active now.") else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SCHEDULE_CHOICES.forEach { m ->
                FilterChip(
                    selected = (settings?.scheduleMinutes ?: 0) == m,
                    onClick = { vm.setScheduleMinutes(m) },
                    label = { Text(if (m == 0) tr("Выкл", "Off") else tr("каждые $m мин", "every $m min")) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(tr("Предупреждать о нехватке памяти", "Warn when memory is low"))
            Text(
                tr(
                    "Уведомление с кнопкой «Ускорить», когда свободной памяти меньше порога. Не чаще раза в 10 минут.",
                    "A notification with a Boost button when free memory drops below the threshold. At most once every 10 minutes.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings?.lowMemoryAlert == true, onCheckedChange = vm::setLowMemoryAlert)
    }
    if (settings?.lowMemoryAlert == true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Порог", "Threshold"), modifier = Modifier.width(120.dp))
            LowMemoryGate.PERCENT_CHOICES.forEach { p ->
                FilterChip(
                    selected = settings.lowMemoryPercent == p,
                    onClick = { vm.setLowMemoryPercent(p) },
                    label = { Text(tr("меньше $p%", "below $p%")) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Export / import of the settings through the system file picker (a flash drive), with a folder fallback. */
@Composable
fun BackupSection(vm: SettingsViewModel) {
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    Text(tr("Перенос настроек", "Transfer settings"), style = MaterialTheme.typography.titleMedium)
    Text(
        tr(
            "Белый список, автоускорение, расписание и предупреждение о памяти — в файл, например на флешку, и обратно. Список замороженных приложений не переносится.",
            "Whitelist, auto-boost, schedule and memory warning — to a file, e.g. on a flash drive, and back. The frozen apps list is not transferred.",
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = {
                try {
                    export.launch("mcleaner-settings.json")
                } catch (_: ActivityNotFoundException) {
                    vm.exportToFolder()
                }
            },
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(tr("Экспорт в файл…", "Export to file…")) }
        OutlinedButton(
            onClick = {
                try {
                    import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } catch (_: ActivityNotFoundException) {
                    vm.reportNoPicker()
                }
            },
            modifier = Modifier.heightIn(min = MinTouch),
        ) { Text(tr("Импорт из файла…", "Import from file…")) }
    }

    pending?.let { d ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text(tr("Импортировать настройки?", "Import settings?")) },
            text = {
                Text(
                    tr(
                        "Белый список: ${app.mcleaner.core.i18n.count(d.whitelist.size, "приложение", "приложения", "приложений", "app", "apps")}. Автоускорение: ${onOff(d.autoBoost)}. " +
                            "Расписание: ${if (d.scheduleMinutes == 0) "выкл" else "каждые ${d.scheduleMinutes} мин"}. " +
                            "Предупреждение о памяти: ${onOff(d.lowMemoryAlert)}.\n\nТекущие настройки заменятся.",
                        "Whitelist: ${d.whitelist.size} apps. Auto-boost: ${onOff(d.autoBoost)}. " +
                            "Schedule: ${if (d.scheduleMinutes == 0) "off" else "every ${d.scheduleMinutes} min"}. " +
                            "Memory warning: ${onOff(d.lowMemoryAlert)}.\n\nCurrent settings will be replaced.",
                    ),
                )
            },
            confirmButton = { Button(onClick = vm::confirmImport, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Импортировать", "Import")) } },
            dismissButton = { TextButton(onClick = vm::cancelImport, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

private fun onOff(on: Boolean) = if (on) tr("вкл", "on") else tr("выкл", "off")
