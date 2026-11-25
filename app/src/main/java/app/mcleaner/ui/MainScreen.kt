package app.mcleaner.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import app.mcleaner.auto.WakeWatchService
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.apps.AppsScreen
import app.mcleaner.ui.cleanup.CleanupScreen
import app.mcleaner.ui.common.FullModeBanner
import app.mcleaner.ui.common.container
import app.mcleaner.ui.overview.OverviewScreen
import app.mcleaner.ui.settings.SettingsScreen
import app.mcleaner.ui.update.UpdateDialog

private enum class Tab(val title: String, val icon: ImageVector) {
    OVERVIEW("Обзор", Icons.Outlined.Speed),
    APPS("Прилож.", Icons.Outlined.Apps),
    CLEANUP("Очистка", Icons.Outlined.CleaningServices),
    SETTINGS("Настр.", Icons.Outlined.Settings),
}

@Composable
fun MainScreen() {
    val c = container()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shizuku by c.shizuku.state.collectAsStateWithLifecycle()

    var previous by remember { mutableIntStateOf(-1) }
    LaunchedEffect(shizuku) {
        if (shizuku == ShizukuState.READY) {
            c.apps.ensureGrants()
            c.syncSettingsBackup() // file access may have just been granted
        }
        if (previous == ShizukuState.READY.ordinal && shizuku != ShizukuState.READY) {
            Toast.makeText(context, "Shizuku остановлен — базовый режим", Toast.LENGTH_LONG).show()
        }
        previous = shizuku.ordinal
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title, fontSize = 14.sp) },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shizuku != ShizukuState.READY && tab != Tab.SETTINGS.ordinal) {
                FullModeBanner(shizuku) { tab = Tab.SETTINGS.ordinal }
            }
            Box(Modifier.weight(1f)) {
                when (Tab.entries[tab]) {
                    Tab.OVERVIEW -> OverviewScreen()
                    Tab.APPS -> AppsScreen()
                    Tab.CLEANUP -> CleanupScreen()
                    Tab.SETTINGS -> SettingsScreen(onAutoBoostChanged = { on ->
                        if (on) WakeWatchService.start(context) else WakeWatchService.stop(context)
                    })
                }
            }
        }
    }
    UpdateDialog(c.updates)
}
