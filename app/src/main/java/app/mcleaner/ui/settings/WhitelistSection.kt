package app.mcleaner.ui.settings

import app.mcleaner.core.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mcleaner.core.policy.InstalledApp
import app.mcleaner.core.policy.WhitelistEntry
import app.mcleaner.ui.common.Badge
import app.mcleaner.ui.common.BadgeKind
import app.mcleaner.ui.common.MinTouch

@Composable
fun WhitelistSection(
    entries: List<WhitelistEntry>,
    candidates: (String) -> List<InstalledApp>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReset: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(tr("Белый список: ${entries.size}", "Whitelist: ${entries.size}"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("«Ускорить» не останавливает эти приложения и не чистит их кэш. Системные приложения машины защищены всегда.", "Boost does not stop these apps or clear their cache. The car's system apps are always protected."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.heightIn(min = MinTouch)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(tr("Добавить", "Add"))
        }
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Сбросить", "Reset")) }
    }
    Column {
        entries.forEach { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!e.installed) Badge(tr("не установлено", "not installed"), BadgeKind.NEUTRAL)
                    }
                    if (e.installed) Text(e.packageName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRemove(e.packageName) }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Убрать ${e.label} из белого списка", "Remove ${e.label} from whitelist"))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }

    if (adding) {
        AddDialog(candidates = candidates, onPick = onAdd, onDismiss = { adding = false })
    }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Сбросить белый список?", "Reset whitelist?")) },
            text = { Text(tr("Останутся только стандартные навигаторы, музыка, Shizuku и DiPlay. Ваши добавленные приложения уберутся.", "Only the default navigators, music, Shizuku and DiPlay will remain. Apps you added will be removed.")) },
            confirmButton = { TextButton(onClick = { onReset(); confirmReset = false }) { Text(tr("Сбросить", "Reset")) } },
            dismissButton = { OutlinedButton(onClick = { confirmReset = false }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

@Composable
private fun AddDialog(candidates: (String) -> List<InstalledApp>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(setOf<String>()) }
    val list = candidates(query).filter { it.packageName !in added }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Head unit screens are only ~720 px tall: shrink the list above the keyboard instead of hiding it.
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 720.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Добавить в белый список", "Add to whitelist"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(tr("Название или пакет", "Name or package")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 380.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.packageName); added = added + app.packageName }
                                .heightIn(min = MinTouch).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (app.isSystem) Badge(tr("системное", "system"), BadgeKind.WARN)
                            Icon(Icons.Outlined.Add, contentDescription = tr("Добавить", "Add"), Modifier.padding(start = 12.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                if (list.isEmpty()) Text(tr("Ничего не найдено", "Nothing found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(if (added.isEmpty()) tr("Закрыть", "Close") else tr("Готово (добавлено: ${added.size})", "Done (added: ${added.size})"))
                    }
                }
            }
        }
        }
    }
}
