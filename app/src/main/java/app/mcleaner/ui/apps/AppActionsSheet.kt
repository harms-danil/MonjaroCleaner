package app.mcleaner.ui.apps

import app.mcleaner.core.i18n.tr
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
    /** Null when the app has no screen to start (services, frozen apps). */
    onOpen: (() -> Unit)?,
    onUnfreezeAndOpen: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleLarge)
            Text(
                tr("${app.packageName} · ОЗУ ${app.pssKb?.let(Formatters::kb) ?: "—"} · кэш ${app.cacheBytes?.let(Formatters::bytes) ?: "—"}", "${app.packageName} · RAM ${app.pssKb?.let(Formatters::kb) ?: "—"} · cache ${app.cacheBytes?.let(Formatters::bytes) ?: "—"}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wraps onto a second line: the sheet is narrow and a frozen app has more buttons.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fun act(block: () -> Unit): () -> Unit = {
                    block()
                    onDismiss()
                }
                // Leftmost: «Разморозить» only lifts the freeze, nothing is started; then the ways to open.
                if (full && app.isFrozen) SheetButton(tr("Разморозить", "Unfreeze"), act(onUnfreeze))
                if (full && app.isFrozen) SheetButton(tr("Разморозить и открыть", "Unfreeze and open"), act(onUnfreezeAndOpen))
                onOpen?.let { open -> androidx.compose.material3.Button(onClick = act(open), modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Открыть", "Open")) } }
                if (canStop) SheetButton(tr("Закрыть", "Close"), act(if (full) onStop else onOpenSettings))
                SheetButton(tr("Очистить кэш", "Clear cache"), act(if (full) onClearCache else onOpenSettings))
                if (full && canFreeze && !app.isFrozen) SheetButton(tr("Заморозить", "Freeze"), act(onFreeze))
                androidx.compose.material3.TextButton(onClick = act(onOpenSettings), modifier = Modifier.heightIn(min = MinTouch)) {
                    Text(tr("Системная карточка", "System app page"))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("В белом списке", "In whitelist"))
                    Text(tr("«Ускорить» не будет останавливать и чистить это приложение", "Boost will not stop or clear this app"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
