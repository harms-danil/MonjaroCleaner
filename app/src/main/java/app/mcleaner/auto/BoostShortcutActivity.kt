package app.mcleaner.auto

import app.mcleaner.core.log.L
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import app.mcleaner.MCleanerApp
import app.mcleaner.core.history.BoostSource
import app.mcleaner.core.i18n.tr
import app.mcleaner.domain.BoostReport
import app.mcleaner.ui.common.Formatters
import kotlinx.coroutines.launch

/**
 * «Ускорить» without opening MCleaner: the separate home-screen icon, the long-press shortcut,
 * the widget and the low-memory notification all land here. Invisible; shows the result as a toast.
 */
class BoostShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { s -> BoostSource.entries.firstOrNull { it.name == s } } ?: BoostSource.SHORTCUT
        val app = applicationContext as MCleanerApp
        L.i("Shortcut", "boost requested from $source")
        if (source == BoostSource.LOW_MEMORY) {
            getSystemService(NotificationManager::class.java).cancel(WakeWatchService.LOW_MEMORY_NOTIFICATION_ID)
        }
        // Exported launcher entry: any app can start it. A boost force-stops apps, so not more than once a minute.
        val now = SystemClock.elapsedRealtime()
        val last = lastStartMs
        if (last != null && now - last in 0 until MIN_INTERVAL_MS) {
            Toast.makeText(app, tr("MCleaner: уже ускорено недавно", "MCleaner: Boosted recently"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lastStartMs = now
        Toast.makeText(app, tr("MCleaner: ускоряю…", "MCleaner: boosting…"), Toast.LENGTH_SHORT).show()
        app.container.appScope.launch {
            val report = runCatching { app.container.runBoost(source) }.getOrNull()
            Toast.makeText(app, describe(report), Toast.LENGTH_LONG).show()
            BoostWidget.refresh(app, report)
        }
        finish()
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val MIN_INTERVAL_MS = 60_000L

        /** When the last shortcut/widget/notification boost started (monotonic clock, this process). */
        private var lastStartMs: Long? = null

        fun intent(context: Context, source: BoostSource): Intent =
            Intent(context, BoostShortcutActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun describe(r: BoostReport?): String = when {
            r == null -> tr("MCleaner: не удалось ускорить", "MCleaner: boost failed")
            r.fullMode -> tr(
                "MCleaner: освобождено ${Formatters.kb(r.freedRamKb)}, остановлено приложений: ${r.stopped}",
                "MCleaner: freed ${Formatters.kb(r.freedRamKb)}, apps stopped: ${r.stopped}",
            )
            else -> tr(
                "MCleaner: освобождено ${Formatters.kb(r.freedRamKb)} (базовый режим)",
                "MCleaner: freed ${Formatters.kb(r.freedRamKb)} (basic mode)",
            )
        }
    }
}
