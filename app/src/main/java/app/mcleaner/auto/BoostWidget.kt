package app.mcleaner.auto

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.mcleaner.R
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.parse.MemInfoParser
import app.mcleaner.domain.BoostReport
import app.mcleaner.ui.common.Formatters
import java.io.File

/** Optional home-screen widget (where the launcher supports widgets): free RAM and a «Ускорить» tap target. */
class BoostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, null)) }
    }

    companion object {
        /** Redraw all widgets; after a boost, [report] shows what was freed. */
        fun refresh(context: Context, report: BoostReport?) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BoostWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context, report)
            ids.forEach { runCatching { manager.updateAppWidget(it, v) } }
        }

        private fun views(context: Context, report: BoostReport?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_boost)
            v.setTextViewText(R.id.widget_title, tr("Ускорить", "Boost"))
            val free = runCatching { MemInfoParser.parse(File("/proc/meminfo").readText()) }.getOrNull()
            val status = buildString {
                if (free != null) append(tr("Свободно ", "Free ")).append(Formatters.kb(free.availableKb))
                if (report != null) {
                    if (isNotEmpty()) append(" · ")
                    append(tr("освобождено ", "freed ")).append(Formatters.kb(report.freedRamKb))
                }
            }
            v.setTextViewText(R.id.widget_status, status)
            val tap = PendingIntent.getActivity(
                context, 0, BoostShortcutActivity.intent(context, BoostSource.SHORTCUT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, tap)
            return v
        }
    }
}
