package app.mcleaner.ui

import app.mcleaner.core.i18n.tr
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.about.AboutScreen
import app.mcleaner.ui.apps.AppsScreen
import app.mcleaner.ui.cleanup.CleanupScreen
import app.mcleaner.ui.common.FullModeBanner
import app.mcleaner.ui.common.container
import app.mcleaner.ui.overview.OverviewScreen
import app.mcleaner.ui.settings.SettingsScreen
import app.mcleaner.ui.update.UpdateDialog

private enum class Tab(private val ru: String, private val en: String, val icon: ImageVector) {
    OVERVIEW("Обзор", "Overview", Icons.Outlined.Speed),
    APPS("Приложения", "Apps", Icons.Outlined.Apps),
    CLEANUP("Очистка", "Cleanup", Icons.Outlined.CleaningServices),
    MAINTENANCE("Обслуживание", "Maintenance", Icons.Outlined.Build),
    JOURNAL("Журнал", "Journal", Icons.Outlined.Description),
    SETTINGS("Настройки", "Settings", Icons.Outlined.Settings),
    ABOUT("О программе", "About", Icons.Outlined.Info),
    ;
    val title: String get() = tr(ru, en)
}

@Composable
fun MainScreen() {
    val c = container()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()
    LaunchedEffect(tab) { app.mcleaner.core.log.L.i("UI", "tab ${Tab.entries[tab].name}") }

    var previous by remember { mutableIntStateOf(-1) }
    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuState.READY) {
            c.apps.ensureGrants()
            runCatching { c.syncSettingsBackup() } // file access may have just been granted; I/O runs on Dispatchers.IO
            c.updates.cleanupStaged()
            runCatching { c.backgroundLimiter.reapply(c.settings.current().noBackground) }
        }
        if (previous == ShizukuState.READY.ordinal && shizuku != ShizukuState.READY) {
            Toast.makeText(context, tr("Shizuku остановлен — базовый режим", "Shizuku stopped — basic mode"), Toast.LENGTH_LONG).show()
        }
        previous = shizuku.ordinal
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SideMenu(selected = tab, onSelect = { tab = it })
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shizuku != ShizukuState.READY && tab != Tab.SETTINGS.ordinal && tab != Tab.ABOUT.ordinal && tab != Tab.MAINTENANCE.ordinal && tab != Tab.JOURNAL.ordinal) {
                FullModeBanner(shizuku) { tab = Tab.SETTINGS.ordinal }
            }
            Box(Modifier.weight(1f)) {
                when (Tab.entries[tab]) {
                    Tab.OVERVIEW -> OverviewScreen()
                    Tab.APPS -> AppsScreen()
                    Tab.CLEANUP -> CleanupScreen()
                    Tab.MAINTENANCE -> app.mcleaner.ui.maintenance.MaintenanceScreen()
                    Tab.JOURNAL -> app.mcleaner.ui.journal.JournalScreen()
                    Tab.SETTINGS -> SettingsScreen()
                    Tab.ABOUT -> AboutScreen()
                }
            }
        }
    }
    UpdateDialog(c.updates)
}

/** Wide side menu with icon and full label, like the head unit's own settings. */
@Composable
private fun SideMenu(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(220.dp).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val active = i == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = active, onClick = { onSelect(i) }, role = androidx.compose.ui.semantics.Role.Tab)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Text(t.title, color = tint, fontSize = 17.sp, maxLines = 1)
            }
        }
    }
}
