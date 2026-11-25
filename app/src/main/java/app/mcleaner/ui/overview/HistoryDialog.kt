package app.mcleaner.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.history.BoostHistory
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.i18n.Lang
import app.mcleaner.core.i18n.Language
import app.mcleaner.core.i18n.count
import app.mcleaner.core.i18n.tr
import app.mcleaner.data.HistoryStore
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WEEK_MS = 7 * 24 * 3600_000L

/** «История»: totals for 7 days, apps stopped most often, and the latest boosts. */
@Composable
fun HistoryDialog(store: HistoryStore, onDismiss: () -> Unit) {
    val records by store.records.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { store.load() }
    val pm = LocalContext.current.packageManager
    val label = remember(pm) {
        { pkg: String -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    }
    val week = BoostHistory.summary(records, System.currentTimeMillis() - WEEK_MS)
    val top = BoostHistory.topStopped(records, limit = 5)
    val fmt = remember(Lang.current) {
        SimpleDateFormat("d MMM, HH:mm", if (Lang.current == Language.EN) Locale.US else Locale.forLanguageTag("ru-RU"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("История ускорений", "Boost history")) },
        text = {
            if (records.isEmpty()) {
                Text(tr("Пока пусто. Здесь появится каждое ускорение: вручную, автоматическое, по расписанию.", "Nothing yet. Every boost — manual, automatic or scheduled — will appear here."))
                return@AlertDialog
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(
                        "За 7 дней: ${count(week.count, "ускорение", "ускорения", "ускорений", "boost", "boosts")}, освобождено ${Formatters.kb(week.freedRamKb)} памяти и ${Formatters.bytes(week.freedCacheBytes)} кэша",
                        "Last 7 days: ${count(week.count, "ускорение", "ускорения", "ускорений", "boost", "boosts")}, freed ${Formatters.kb(week.freedRamKb)} of RAM and ${Formatters.bytes(week.freedCacheBytes)} of cache",
                    ),
                )
                if (top.isNotEmpty()) {
                    Text(tr("Чаще всего останавливались", "Stopped most often"), style = MaterialTheme.typography.titleMedium)
                    top.forEach { (pkg, n) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(label(pkg), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(count(n, "раз", "раза", "раз", "time", "times"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(tr("Последние", "Latest"), style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    // No key: timeMs is not unique (two boosts in one ms, or a reset clock), and duplicate keys crash.
                    items(records.take(50)) { r ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                            Text(fmt.format(Date(r.timeMs)), Modifier.width(140.dp), fontSize = 15.sp)
                            Text(sourceTitle(r.source), Modifier.width(150.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                tr("+${Formatters.kb(r.freedRamKb)}, остановлено ${r.stopped.size}", "+${Formatters.kb(r.freedRamKb)}, stopped ${r.stopped.size}"),
                                Modifier.weight(1f), fontSize = 15.sp, maxLines = 1,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Закрыть", "Close")) } },
    )
}

private fun sourceTitle(s: BoostSource) = when (s) {
    BoostSource.MANUAL -> tr("вручную", "manual")
    BoostSource.AUTO -> tr("автоускорение", "auto-boost")
    BoostSource.SCHEDULE -> tr("по расписанию", "scheduled")
    BoostSource.SHORTCUT -> tr("значок/виджет", "icon/widget")
    BoostSource.LOW_MEMORY -> tr("мало памяти", "low memory")
}
