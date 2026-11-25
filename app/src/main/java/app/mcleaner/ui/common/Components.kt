package app.mcleaner.ui.common

import app.mcleaner.core.i18n.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mcleaner.MCleanerApp
import app.mcleaner.privileged.ShizukuState
import app.mcleaner.ui.theme.Palette

val MinTouch = 56.dp

@Composable
fun container() = (LocalContext.current.applicationContext as MCleanerApp).container

@Composable
fun StatCard(label: String, value: String, progress: Float?, barColor: Color, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

enum class BadgeKind { WARN, GOOD, NEUTRAL, INFO }

@Composable
fun Badge(text: String, kind: BadgeKind) {
    val (bg, fg) = when (kind) {
        BadgeKind.WARN -> Palette.Warn.copy(alpha = 0.18f) to Palette.Warn
        BadgeKind.GOOD -> Palette.Good.copy(alpha = 0.18f) to Palette.Good
        BadgeKind.INFO -> Palette.Accent.copy(alpha = 0.18f) to Palette.Accent
        BadgeKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        color = fg,
        fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@androidx.compose.runtime.Composable
fun ramColor(fraction: Float): Color = when {
    fraction < 0.70f -> Palette.Good
    fraction <= 0.85f -> Palette.Warn
    else -> Palette.Bad
}

fun ShizukuState.title(): String = when (this) {
    ShizukuState.READY -> tr("Shizuku активен", "Shizuku active")
    ShizukuState.NOT_RUNNING -> tr("Shizuku не запущен", "Shizuku not running")
    ShizukuState.NO_PERMISSION -> tr("Нет разрешения Shizuku", "No Shizuku permission")
    ShizukuState.NOT_INSTALLED -> tr("Shizuku не установлен", "Shizuku not installed")
}

@Composable
fun FullModeBanner(state: ShizukuState, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Warn.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Palette.Warn)
        Text(
            tr("Базовый режим: ${state.title().replaceFirstChar { it.lowercase() }}. Включите полный режим, чтобы останавливать приложения и чистить кэш.", "Basic mode: ${state.title().replaceFirstChar { it.lowercase() }}. Enable full mode to stop apps and clear cache."),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = MinTouch)) { Text(tr("Как включить", "How to enable")) }
    }
}

@Composable
fun PermissionPlate(text: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Button(onClick = onClick, modifier = Modifier.heightIn(min = MinTouch)) { Text(button) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(12.dp))
        }
        Text(text, fontSize = 22.sp)
    }
}

object SystemScreens {
    fun appDetails(ctx: Context, pkg: String) = start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    fun usageAccess(ctx: Context) = start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun allFilesAccess(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            appDetails(ctx, ctx.packageName)
            return
        }
        val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        if (!start(ctx, specific)) start(ctx, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
    fun openApp(ctx: Context, pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { start(ctx, it) }
    }

    private fun start(ctx: Context, intent: Intent): Boolean = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
