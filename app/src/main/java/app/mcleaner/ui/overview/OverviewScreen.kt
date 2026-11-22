package app.mcleaner.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Состояние магнитолы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                label = "Оперативная память",
                value = ram?.let { Formatters.usedOfTotal(it.usedKb * 1024, it.totalKb * 1024) } ?: "—",
                progress = ram?.let { ramFraction },
                barColor = ramColor(ramFraction),
                caption = ram?.let { "Свободно ${Formatters.kb(it.availableKb)} · ${(ramFraction * 100).toInt()}%" } ?: "Загрузка…",
                modifier = Modifier.weight(1f),
            )
            val used = s?.let { it.storageTotalBytes - it.storageFreeBytes }
            StatCard(
                label = "Накопитель",
                value = s?.let { Formatters.usedOfTotal(used!!, it.storageTotalBytes) } ?: "—",
                progress = s?.let { used!!.toFloat() / it.storageTotalBytes.coerceAtLeast(1) },
                barColor = MaterialTheme.colorScheme.primary,
                caption = s?.let { "Свободно ${Formatters.bytes(it.storageFreeBytes)}" } ?: "Загрузка…",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Процессор",
                value = s?.cpuPercent?.let { "$it%" } ?: "—",
                progress = s?.cpuPercent?.let { it / 100f },
                barColor = ramColor((s?.cpuPercent ?: 0) / 100f),
                caption = listOfNotNull(
                    s?.tempC?.let { "Температура $it °C" },
                    if (s?.cpuPercent == null) "Нагрузка — в полном режиме" else null,
                ).joinToString(" · ").ifEmpty { " " },
                modifier = Modifier.weight(1f),
            )
        }

        Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Больше всего памяти", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                if (!full) {
                    Text("Доступно в полном режиме", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (ui.topApps.isEmpty()) {
                    Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.topApps.forEach { app ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Formatters.kb(app.pssKb ?: 0))
                    }
                }
            }
            Column(
                Modifier.width(300.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    text = if (ui.boosting) "Ускоряю…" else "Ускорить",
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
                    if (full) "Завершит ${ui.candidates} фоновых приложений и очистит кэш" else "Попросит систему выгрузить фоновые приложения",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                )
            }
        }
    }

    ui.report?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissReport,
            confirmButton = { TextButton(onClick = vm::dismissReport) { Text("Готово") } },
            title = { Text("Готово") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Освобождено ${Formatters.kb(r.freedRamKb)} оперативной памяти")
                    if (r.fullMode) Text("Очищено ${Formatters.bytes(r.freedCacheBytes)} кэша")
                    Text("Остановлено приложений: ${r.stopped}")
                    if (r.failed.isNotEmpty()) Text("Не удалось остановить: ${r.failed.size}", color = Palette.Warn)
                    if (r.protectionUnavailable) {
                        Text(
                            "Не удалось проверить, какие приложения сейчас играют или ведут маршрут, поэтому приложения не останавливались — только очищен кэш.",
                            color = Palette.Warn,
                        )
                    }
                    if (!r.fullMode) {
                        Text("Для полного эффекта включите Shizuku в настройках.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
}
