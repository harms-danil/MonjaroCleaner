package app.mcleaner.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mcleaner.core.model.AppInfo
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: AppInfo,
    full: Boolean,
    whitelisted: Boolean,
    canStop: Boolean,
    canFreeze: Boolean,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onClearCache: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
    onWhitelist: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${app.packageName} · ОЗУ ${app.pssKb?.let(Formatters::kb) ?: "—"} · кэш ${app.cacheBytes?.let(Formatters::bytes) ?: "—"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fun act(block: () -> Unit): () -> Unit = {
                    block()
                    onDismiss()
                }
                if (canStop) SheetButton("Остановить", act(if (full) onStop else onOpenSettings))
                SheetButton("Очистить кэш", act(if (full) onClearCache else onOpenSettings))
                if (full && app.isFrozen) SheetButton("Разморозить", act(onUnfreeze))
                if (full && canFreeze && !app.isFrozen) SheetButton("Заморозить", act(onFreeze))
                SheetButton("Открыть в настройках", act(onOpenSettings))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("В белом списке")
                    Text("«Ускорить» не будет останавливать и чистить это приложение", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = whitelisted, onCheckedChange = onWhitelist)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouch)) { Text(text) }
}
