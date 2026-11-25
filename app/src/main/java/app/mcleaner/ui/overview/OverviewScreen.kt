package app.mcleaner.ui.overview

import app.mcleaner.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Badge
import app.mcleaner.ui.common.BadgeKind
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.PrimaryButton
import app.mcleaner.ui.common.StatCard
import app.mcleaner.ui.common.containerViewModel
import app.mcleaner.ui.common.ramColor
import app.mcleaner.ui.common.title
import app.mcleaner.ui.theme.Palette

@Composable
fun OverviewScreen() {
    val vm = containerViewModel { OverviewViewModel(it) }
    val stats by vm.stats.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val full = shizuku == ShizukuState.READY
    var showHistory by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showHistory) HistoryDialog(app.mcleaner.ui.common.container().history) { showHistory = false }
    var byCache by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Состояние магнитолы", "Head unit status"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (full) Icons.Outlined.VerifiedUser else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (full) Palette.Good else Palette.Warn,
                    modifier = Modifier.size(20.dp),
                )
                Badge(shizuku.title(), if (full) BadgeKind.GOOD else BadgeKind.WARN)
            }
        }

        val s = stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ram = s?.ram
            val ramFraction = ram?.let { it.usedKb.toFloat() / it.totalKb } ?: 0f
            StatCard(
                label = tr("Оперативная память", "Memory (RAM)"),
                value = ram?.let { Formatters.usedOfTotal(it.usedKb * 1024, it.totalKb * 1024) } ?: "—",
                progress = ram?.let { ramFraction },
                barColor = ramColor(ramFraction),
                caption = ram?.let { tr("Свободно ${Formatters.kb(it.availableKb)} · ${(ramFraction * 100).toInt()}%", "Free ${Formatters.kb(it.availableKb)} · ${(ramFraction * 100).toInt()}%") } ?: tr("Загрузка…", "Loading…"),
                modifier = Modifier.weight(1f),
            )
            val used = s?.let { it.storageTotalBytes - it.storageFreeBytes }
            StatCard(
                label = tr("Накопитель", "Storage"),
                value = s?.let { Formatters.usedOfTotal(used!!, it.storageTotalBytes) } ?: "—",
                progress = s?.let { used!!.toFloat() / it.storageTotalBytes.coerceAtLeast(1) },
                barColor = MaterialTheme.colorScheme.primary,
                caption = s?.let { tr("Свободно ${Formatters.bytes(it.storageFreeBytes)}", "Free ${Formatters.bytes(it.storageFreeBytes)}") } ?: tr("Загрузка…", "Loading…"),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = tr("Процессор", "CPU"),
                value = s?.cpuPercent?.let { "$it%" } ?: "—",
                progress = s?.cpuPercent?.let { it / 100f },
                barColor = ramColor((s?.cpuPercent ?: 0) / 100f),
                caption = listOfNotNull(
                    s?.tempC?.let { tr("Температура $it °C", "Temperature $it °C") },
                    if (s?.cpuPercent == null) tr("Нагрузка — в полном режиме", "Load — in full mode") else null,
                ).joinToString(" · ").ifEmpty { " " },
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth().heightIn(min = 220.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (byCache) tr("Больше всего кэша", "Largest cache") else tr("Больше всего памяти", "Top memory users"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.FilterChip(!byCache, { byCache = false }, { Text(tr("Память", "Memory")) }, Modifier.heightIn(min = 40.dp))
                    androidx.compose.material3.FilterChip(byCache, { byCache = true }, { Text(tr("Кэш", "Cache")) }, Modifier.heightIn(min = 40.dp))
                }
                val list = if (byCache) ui.topCache else ui.topApps
                when {
                    !byCache && !full -> Text(tr("Доступно в полном режиме", "Available in full mode"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !ui.loaded -> Text(tr("Загрузка…", "Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.isEmpty() -> Text(
                        if (byCache) tr("Кэша нет или нет доступа к статистике", "No cache or no usage access") else tr("Нет данных", "No data"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                list.forEach { app ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (byCache) Formatters.bytes(app.cacheBytes ?: 0) else Formatters.kb(app.pssKb ?: 0))
                    }
                }
            }
            Column(
                Modifier.width(300.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    text = if (ui.boosting) tr("Ускоряю…", "Boosting…") else tr("Ускорить", "Boost"),
                    onClick = vm::boost,
                    enabled = !ui.boosting,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        if (ui.boosting) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Outlined.RocketLaunch, contentDescription = null)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (full) tr(
                        "Завершит ${app.mcleaner.core.i18n.count(ui.candidates, "фоновое приложение", "фоновых приложения", "фоновых приложений", "background app", "background apps")} и очистит кэш",
                        "Will stop ${app.mcleaner.core.i18n.count(ui.candidates, "фоновое приложение", "фоновых приложения", "фоновых приложений", "background app", "background apps")} and clear cache",
                    ) else tr("Попросит систему выгрузить фоновые приложения", "Will ask the system to unload background apps"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                )
                androidx.compose.material3.TextButton(
                    onClick = { showHistory = true },
                    modifier = Modifier.heightIn(min = app.mcleaner.ui.common.MinTouch),
                ) { Text(tr("История", "History")) }
            }
        }
    }

    ui.report?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissReport,
            confirmButton = { TextButton(onClick = vm::dismissReport) { Text(tr("Готово", "Done")) } },
            title = { Text(tr("Готово", "Done")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Освобождено ${Formatters.kb(r.freedRamKb)} оперативной памяти", "Freed ${Formatters.kb(r.freedRamKb)} of RAM"))
                    if (r.fullMode) Text(tr("Очищено ${Formatters.bytes(r.freedCacheBytes)} кэша", "Cleared ${Formatters.bytes(r.freedCacheBytes)} of cache"))
                    Text(tr("Остановлено приложений: ${r.stopped}", "Apps stopped: ${r.stopped}"))
                    if (r.failed.isNotEmpty()) Text(tr("Не удалось остановить: ${r.failed.size}", "Could not stop: ${r.failed.size}"), color = Palette.Warn)
                    if (r.protectionUnavailable) {
                        Text(
                            tr("Не удалось проверить, какие приложения сейчас играют или ведут маршрут, поэтому приложения не останавливались — только очищен кэш.", "Could not check which apps are playing audio or navigating, so no apps were stopped — only cache was cleared."),
                            color = Palette.Warn,
                        )
                    }
                    if (!r.fullMode) {
                        Text(tr("Для полного эффекта включите Shizuku в настройках.", "For the full effect, enable Shizuku in Settings."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
}
