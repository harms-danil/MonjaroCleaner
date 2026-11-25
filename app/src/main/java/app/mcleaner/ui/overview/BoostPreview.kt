package app.mcleaner.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mcleaner.core.i18n.count
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.policy.SkipReason
import app.mcleaner.ui.common.Formatters

/** The line under «Ускорить»: who will actually be stopped right now. */
fun boostPreview(full: Boolean, ui: OverviewUi): String {
    if (!full) return tr("Попросит систему выгрузить фоновые приложения", "Will ask the system to unload background apps")
    if (!ui.loaded) return tr("Считаю…", "Counting…")
    if (!ui.busyKnown) {
        return tr(
            "Не удалось проверить, что сейчас играет или ведёт маршрут, — приложения останавливаться не будут, только кэш",
            "Could not check what is playing or navigating — no apps will be stopped, only cache",
        )
    }
    if (ui.toStop.isEmpty()) {
        return tr(
            "Сейчас нечего останавливать: работают только системные, занятые и приложения из белого списка. Очистит кэш",
            "Nothing to stop right now: only system, busy and whitelisted apps are running. Will clear cache",
        )
    }
    val names = ui.toStop.take(3).joinToString(", ") { it.label } + if (ui.toStop.size > 3) tr(" и ещё ${ui.toStop.size - 3}", " and ${ui.toStop.size - 3} more") else ""
    val ram = Formatters.kb(ui.toStop.sumOf { it.pssKb ?: 0 })
    val what = count(ui.toStop.size, "приложение", "приложения", "приложений", "app", "apps")
    return tr("Остановит $what ($names) — около $ram памяти — и очистит кэш", "Will stop $what ($names) — about $ram of memory — and clear cache")
}

private fun SkipReason.title(): String = when (this) {
    SkipReason.WHITELIST -> tr("в белом списке", "whitelisted")
    SkipReason.IN_USE -> tr("занято: играет, ведёт маршрут, лаунчер, клавиатура или кнопки руля", "busy: playing, navigating, launcher, keyboard or steering buttons")
    SkipReason.SYSTEM -> tr("системное или машины", "system or car")
    SkipReason.GOOGLE -> tr("сервисы Google (включается в настройках)", "Google services (can be enabled in Settings)")
    SkipReason.SELF -> tr("MCleaner и Shizuku", "MCleaner and Shizuku")
}

@Composable
fun BoostPreviewDialog(ui: OverviewUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = app.mcleaner.ui.common.MinTouch)) { Text(tr("Закрыть", "Close")) } },
        title = { Text(tr("Что сделает «Ускорить»", "What Boost will do")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Остановит", "Will stop"), style = MaterialTheme.typography.titleMedium)
                if (ui.toStop.isEmpty()) Text(tr("Никого", "Nothing"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ui.toStop.forEach { AppLine(it.label, Formatters.kb(it.pssKb ?: 0)) }
                Text(tr("Не тронет", "Will keep"), style = MaterialTheme.typography.titleMedium)
                // Whitelist and busy apps first: they explain the choice; the long system list goes last, shortened.
                val order = listOf(SkipReason.WHITELIST, SkipReason.IN_USE, SkipReason.GOOGLE, SkipReason.SELF, SkipReason.SYSTEM)
                val groups = ui.kept.groupBy({ it.second }, { it.first })
                order.forEach { reason ->
                    val apps = groups[reason] ?: return@forEach
                    Text(reason.title(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val shown = if (reason == SkipReason.SYSTEM) apps.take(5) else apps
                    shown.forEach { AppLine(it.label, Formatters.kb(it.pssKb ?: 0)) }
                    if (apps.size > shown.size) {
                        Text(tr("…и ещё ${apps.size - shown.size}", "…and ${apps.size - shown.size} more"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    tr(
                        "Кэш: чистится у всех, кроме белого списка и занятых. На Android 9 внутренний кэш по одному приложению система не даёт — чистится внешний.",
                        "Cache: cleared for everyone except whitelisted and busy apps. On Android 9 the system refuses one app's internal cache — the external one is cleared.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun AppLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value)
    }
}
