package app.mcleaner.ui.cleanup

import app.mcleaner.core.i18n.tr
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
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.junk.CategoryState
import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.core.junk.JunkSelection
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
                    tr("Очистить кэш всех приложений", "Clear cache of all apps"),
                    onClick = vm::clearAllCache,
                    enabled = full && !ui.busy,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                )
                PrimaryButton(
                    tr("Найти мусор", "Find junk"),
                    onClick = vm::scan,
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                )
            }
            Text(
                if (full) tr("Кэш навигаторов и музыки из белого списка не трогаем, чтобы не пропали офлайн-карты и треки.", "Cache of whitelisted navigators and music is kept so offline maps and tracks are not lost.")
                else tr("Очистка кэша всех приложений доступна в полном режиме (Shizuku).", "Clearing the cache of all apps is available in full mode (Shizuku)."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!full && !vm.hasFileAccess()) {
                PermissionPlate(tr("Чтобы искать и удалять мусор, нужен доступ ко всем файлам.", "Access to all files is needed to find and delete junk."), tr("Выдать доступ", "Grant access")) {
                    SystemScreens.allFilesAccess(context)
                }
            }
            if (ui.busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(tr("Работаю…", "Working…"))
                }
            }
            if (ui.scanned) {
                if (ui.junk.isEmpty()) {
                    Text(tr("Мусора не найдено", "No junk found"), style = MaterialTheme.typography.titleMedium)
                } else {
                    JunkCategory.entries.filter { it in ui.junk }.forEach { cat ->
                        val items = ui.junk[cat].orEmpty()
                        var expanded by rememberSaveable(cat) { mutableStateOf(false) }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            val state = when (JunkSelection.state(cat, ui.junk, ui.selected)) {
                                CategoryState.ALL -> ToggleableState.On
                                CategoryState.SOME -> ToggleableState.Indeterminate
                                CategoryState.NONE -> ToggleableState.Off
                            }
                            val chosen = items.filter { it.path in ui.selected }
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.toggleCategory(cat) }
                                    .heightIn(min = MinTouch).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TriStateCheckbox(state = state, onClick = { vm.toggleCategory(cat) })
                                Column(Modifier.weight(1f)) {
                                    Text(cat.title)
                                    if (cat == JunkCategory.APK) {
                                        Text(tr("Проверьте: возможно, это нужные установщики", "Check first: these may be installers you need"), color = Palette.Warn)
                                    }
                                }
                                Text(
                                    if (chosen.size == items.size || chosen.isEmpty()) tr("${items.size} шт. · ${Formatters.bytes(items.sumOf { it.sizeBytes })}", "${items.size} items · ${Formatters.bytes(items.sumOf { it.sizeBytes })}")
                                    else tr("выбрано ${chosen.size} из ${items.size} · ${Formatters.bytes(chosen.sumOf { it.sizeBytes })}", "selected ${chosen.size} of ${items.size} · ${Formatters.bytes(chosen.sumOf { it.sizeBytes })}"),
                                )
                                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = MinTouch)) {
                                    Text(if (expanded) tr("Скрыть", "Hide") else tr("Выбрать файлы", "Select files"))
                                }
                            }
                            if (expanded) {
                                Column(Modifier.padding(start = 36.dp, end = 12.dp, bottom = 12.dp)) {
                                    items.take(MAX_SHOWN).forEach { item ->
                                        Row(
                                            Modifier.fillMaxWidth().clickable { vm.toggleItem(item.path) }.heightIn(min = 48.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(checked = item.path in ui.selected, onCheckedChange = { vm.toggleItem(item.path) })
                                            Text(
                                                item.path.removePrefix(STORAGE_PREFIX),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.MiddleEllipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                Formatters.bytes(item.sizeBytes),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 12.dp),
                                            )
                                        }
                                    }
                                    if (items.size > MAX_SHOWN) {
                                        Text(
                                            tr("…и ещё ${items.size - MAX_SHOWN}: их выбор меняется галочкой категории", "…and ${items.size - MAX_SHOWN} more: toggled with the category checkbox"),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = vm::askDelete,
                        enabled = ui.selectedItems.isNotEmpty() && !ui.busy,
                        modifier = Modifier.heightIn(min = MinTouch),
                    ) { Text(tr("Удалить выбранное (${Formatters.bytes(ui.selectedBytes)})", "Delete selected (${Formatters.bytes(ui.selectedBytes)})")) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (ui.askTrimAll) {
        AlertDialog(
            onDismissRequest = vm::dismissTrimAll,
            title = { Text(tr("Внутренний кэш — только целиком", "Internal cache — all or nothing")) },
            text = {
                val ext = ui.externalCleared
                Text(
                    (if (ext != null) tr(
                        "Внешний кэш очищен поштучно, без белого списка: ${ext.apps} прилож., ${Formatters.bytes(ext.bytes)}.\n\n",
                        "External cache cleared per app, whitelist kept: ${ext.apps} apps, ${Formatters.bytes(ext.bytes)}.\n\n",
                    ) else "") + tr(
                        "Внутренний кэш по одному приложению Android 8+ разрешает чистить только системе (нужно разрешение прошивки, у ADB и Shizuku его нет). " +
                            "Можно очистить внутренний кэш всех приложений сразу — включая навигатор и музыку, офлайн-карты могут скачаться заново. " +
                            "Или очистить кэш отдельного приложения в его системной карточке: «Хранилище» → «Очистить кэш».",
                        "Android 8+ lets only the system clear one app's internal cache (a firmware permission ADB and Shizuku don't have). " +
                            "You can clear the internal cache of all apps at once — including navigator and music, offline maps may need re-downloading. " +
                            "Or clear a single app's cache in its system page: Storage → Clear cache.",
                    ),
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmTrimAll) { Text(tr("Очистить внутренний у всех", "Clear internal for all")) } },
            dismissButton = { OutlinedButton(onClick = vm::dismissTrimAll) { Text(tr("Отмена", "Cancel")) } },
        )
    }
    if (ui.confirmDelete) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text(tr("Удалить файлы?", "Delete files?")) },
            text = { Text(tr("Удалить ${app.mcleaner.core.i18n.count(ui.selectedItems.size, "файл", "файла", "файлов", "file", "files")}, ${Formatters.bytes(ui.selectedBytes)}? Это нельзя отменить.", "Delete ${app.mcleaner.core.i18n.count(ui.selectedItems.size, "файл", "файла", "файлов", "file", "files")}, ${Formatters.bytes(ui.selectedBytes)}? This cannot be undone.")) },
            confirmButton = { TextButton(onClick = vm::delete) { Text(tr("Удалить", "Delete")) } },
            dismissButton = { OutlinedButton(onClick = vm::dismissDelete) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}
