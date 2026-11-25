package app.mcleaner.ui.apps

import app.mcleaner.core.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.model.AppInfo
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.common.Badge
import app.mcleaner.ui.common.BadgeKind
import app.mcleaner.ui.common.Formatters
import app.mcleaner.ui.common.MinTouch
import app.mcleaner.ui.common.PermissionPlate
import app.mcleaner.ui.common.SystemScreens
import app.mcleaner.ui.common.containerViewModel

@Composable
fun AppsScreen() {
    val vm = containerViewModel { AppsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val whitelist by vm.whitelist.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val full = shizuku == ShizukuState.READY
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(full) { vm.reload() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(ui.sort == AppSort.MEMORY, { vm.setSort(AppSort.MEMORY) }, { Text(tr("По памяти", "By memory")) }, Modifier.heightIn(min = 48.dp))
                FilterChip(ui.sort == AppSort.CACHE, { vm.setSort(AppSort.CACHE) }, { Text(tr("По кэшу", "By cache")) }, Modifier.heightIn(min = 48.dp))
                FilterChip(ui.onlyRunning, { vm.setOnlyRunning(!ui.onlyRunning) }, { Text(tr("Только работающие", "Running only")) }, Modifier.heightIn(min = 48.dp), enabled = full)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::reload, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = tr("Обновить", "Refresh"))
                }
            }
            if (!vm.hasUsageAccess && !full) {
                PermissionPlate(tr("Чтобы видеть размер кэша, выдайте доступ к статистике.", "Grant usage access to see cache sizes."), tr("Выдать доступ", "Grant access")) {
                    SystemScreens.usageAccess(context)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Spacer(Modifier.width(52.dp))
                Header(tr("Приложение", "App"), Modifier.weight(1f))
                Header(tr("ОЗУ", "RAM"), Modifier.width(100.dp))
                Header(tr("Кэш", "Cache"), Modifier.width(100.dp))
                Spacer(Modifier.width(150.dp))
            }
            if (ui.loading && ui.apps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            whitelisted = app.packageName in whitelist,
                            loadIcon = vm::icon,
                            actionLabel = if (app.isRunning && vm.canStop(app)) tr("Остановить", "Stop") else tr("Кэш", "Cache"),
                            onAction = {
                                when {
                                    !full -> SystemScreens.appDetails(context, app.packageName)
                                    app.isRunning && vm.canStop(app) -> vm.stop(app)
                                    else -> vm.clearCache(app) { SystemScreens.appDetails(context, app.packageName) }
                                }
                            },
                            onClick = { selected = app },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    selected?.let { app ->
        AppActionsSheet(
            app = app,
            full = full,
            whitelisted = app.packageName in whitelist,
            canStop = vm.canStop(app),
            canFreeze = vm.canFreeze(app),
            onDismiss = { selected = null },
            onStop = { vm.stop(app) },
            onClearCache = { vm.clearCache(app) { SystemScreens.appDetails(context, app.packageName) } },
            onFreeze = { vm.freeze(app) },
            onUnfreeze = { vm.unfreeze(app) },
            onWhitelist = { vm.setWhitelisted(app, it) },
            onOpenSettings = { SystemScreens.appDetails(context, app.packageName) },
        )
    }
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
}

@Composable
private fun AppRow(
    app: AppInfo,
    whitelisted: Boolean,
    loadIcon: suspend (String) -> ImageBitmap?,
    actionLabel: String,
    onAction: () -> Unit,
    onClick: () -> Unit,
) {
    val icon by produceState<ImageBitmap?>(null, app.packageName) { value = loadIcon(app.packageName) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            val bmp = icon
            if (bmp != null) Image(bmp, contentDescription = null, Modifier.size(40.dp))
            else Icon(Icons.Outlined.Android, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (app.isSystem) Badge(tr("системное", "system"), BadgeKind.WARN)
            if (whitelisted) Badge(tr("в белом списке", "whitelisted"), BadgeKind.GOOD)
            if (app.isFrozen) Badge(tr("заморожено", "frozen"), BadgeKind.INFO)
        }
        Text(app.pssKb?.let(Formatters::kb) ?: "—", Modifier.width(100.dp))
        Text(app.cacheBytes?.let(Formatters::bytes) ?: "—", Modifier.width(100.dp))
        OutlinedButton(onClick = onAction, modifier = Modifier.width(150.dp).heightIn(min = 48.dp)) {
            Text(actionLabel, fontSize = 15.sp, maxLines = 1)
        }
    }
}
