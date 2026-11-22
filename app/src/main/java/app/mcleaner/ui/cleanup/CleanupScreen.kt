package app.mcleaner.ui.cleanup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.PermissionPlate
import app.mcleaner.ui.common.PrimaryButton
import app.mcleaner.ui.common.SystemScreens
import app.mcleaner.ui.common.containerViewModel
import app.mcleaner.ui.theme.Palette

private const val MAX_SHOWN = 100
private const val STORAGE_PREFIX = "/storage/emulated/0"

@Composable
fun CleanupScreen() {
    val vm = containerViewModel { CleanupViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val full = shizuku == ShizukuState.READY
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    "Очистить кэш всех приложений",
                    onClick = vm::clearAllCache,
                    enabled = full && !ui.busy,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                )
                PrimaryButton(
                    "Найти мусор",
                    onClick = vm::scan,
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                )
            }
            Text(
                if (full) "Кэш навигаторов и музыки из белого списка не трогаем, чтобы не пропали офлайн-карты и треки."
                else "Очистка кэша всех приложений доступна в полном режиме (Shizuku).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!full && !vm.hasFileAccess()) {
                PermissionPlate("Чтобы искать и удалять мусор, нужен доступ ко всем файлам.", "Выдать доступ") {
                    SystemScreens.allFilesAccess(context)
                }
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Работаю…")
                }
            }
            if (ui.scanned) {
                if (ui.junk.isEmpty()) {
                    Text("Мусора не найдено", style = MaterialTheme.typography.titleMedium)
                } else {
                    JunkCategory.entries.filter { it in ui.junk }.forEach { cat ->
                        val items = ui.junk[cat].orEmpty()
                        var expanded by rememberSaveable(cat) { mutableStateOf(false) }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.toggle(cat) }
                                    .heightIn(min = MinTouch).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = cat in ui.selected, onCheckedChange = { vm.toggle(cat) })
                                Column(Modifier.weight(1f)) {
                                    Text(cat.title)
                                    if (cat == JunkCategory.APK) {
                                        Text("Проверьте: возможно, это нужные установщики", color = Palette.Warn)
                                    }
                                }
                                Text("${items.size} шт. · ${Formatters.bytes(items.sumOf { it.sizeBytes })}")
                                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Text(if (expanded) "Скрыть" else "Файлы")
                                }
                            }
                            if (expanded) {
                                Column(Modifier.padding(start = 60.dp, end = 12.dp, bottom = 12.dp)) {
                                    items.take(MAX_SHOWN).forEach { item ->
                                        Text(
                                            "${item.path.removePrefix(STORAGE_PREFIX)}  ·  ${Formatters.bytes(item.sizeBytes)}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (items.size > MAX_SHOWN) {
                                        Text("…и ещё ${items.size - MAX_SHOWN}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = vm::askDelete,
                        enabled = ui.selectedItems.isNotEmpty() && !ui.busy,
                        modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text("Удалить выбранное (${Formatters.bytes(ui.selectedBytes)})") }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (ui.askTrimAll) {
        AlertDialog(
            onDismissRequest = vm::dismissTrimAll,
            title = { Text("Поштучная очистка недоступна") },
            text = { Text("Прошивка не даёт чистить кэш по одному приложению. Очистить кэш всех приложений, включая навигатор и музыку? Офлайн-карты могут скачаться заново.") },
            confirmButton = { TextButton(onClick = vm::confirmTrimAll) { Text("Очистить всё") } },
            dismissButton = { OutlinedButton(onClick = vm::dismissTrimAll) { Text("Отмена") } },
        )
    }
    if (ui.confirmDelete) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text("Удалить файлы?") },
            text = { Text("Удалить ${ui.selectedItems.size} файлов, ${Formatters.bytes(ui.selectedBytes)}? Это нельзя отменить.") },
            confirmButton = { TextButton(onClick = vm::delete) { Text("Удалить") } },
            dismissButton = { OutlinedButton(onClick = vm::dismissDelete) { Text("Отмена") } },
        )
    }
}
