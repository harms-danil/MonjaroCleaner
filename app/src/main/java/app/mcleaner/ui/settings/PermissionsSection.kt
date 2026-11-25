package app.mcleaner.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.mcleaner.AppContainer
import app.mcleaner.core.i18n.tr
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.SystemScreens
import app.mcleaner.ui.theme.Palette
import kotlinx.coroutines.launch

private data class Perm(val title: String, val why: String, val granted: Boolean, val open: (Context) -> Unit)

/**
 * «Разрешения»: what MCleaner needs, whether it has it, and a way to grant it —
 * all at once through Shizuku, or one by one through the system screens.
 */
@Composable
fun PermissionsSection(c: AppContainer, full: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) } // re-read on return from system screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(full) { tick++ }

    val perms = remember(tick) {
        listOf(
            Perm(
                tr("Статистика использования", "Usage access"),
                tr("размер кэша приложений", "app cache sizes"),
                c.apps.hasUsageAccess(), SystemScreens::usageAccess,
            ),
            Perm(
                tr("Доступ к памяти", "Storage access"),
                tr("поиск мусора, резервная копия настроек, хранение версий", "junk search, settings backup, kept versions"),
                c.apps.hasAllFilesAccess(), SystemScreens::allFilesAccess,
            ),
            Perm(
                tr("Установка приложений", "Install apps"),
                tr("обновления MCleaner и установка Shizuku без Shizuku", "MCleaner updates and installing Shizuku without Shizuku"),
                c.apps.canInstallApps(),
            ) { ctx -> open(ctx, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) },
            Perm(
                tr("Без ограничений батареи", "No battery restrictions"),
                tr("чтобы система не останавливала автоускорение и слежение за памятью", "so the system does not stop auto-boost and memory watch"),
                c.apps.ignoresBatteryOptimizations(),
            ) { ctx ->
                @android.annotation.SuppressLint("BatteryLife")
                val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                if (!open(ctx, direct)) open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
        )
    }
    val missing = perms.count { !it.granted }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(tr("Разрешения", "Permissions"), style = MaterialTheme.typography.titleMedium)
            Text(
                if (missing == 0) tr("Всё выдано", "All granted")
                else if (full) tr("С Shizuku можно выдать всё одной кнопкой", "With Shizuku everything can be granted with one tap")
                else tr("Без Shizuku — по одному, через системные экраны", "Without Shizuku — one by one, via system screens"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (full && missing > 0) {
            Button(
                onClick = { scope.launch { c.apps.grantAll(); tick++ } },
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(tr("Выдать все", "Grant all")) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        perms.forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (p.granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (p.granted) Palette.Good else Palette.Warn,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(p.title)
                    Text(p.why, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!p.granted) {
                    OutlinedButton(onClick = { p.open(context) }, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(tr("Выдать", "Grant"))
                    }
                }
            }
        }
    }
}

private fun open(ctx: Context, intent: Intent): Boolean =
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
