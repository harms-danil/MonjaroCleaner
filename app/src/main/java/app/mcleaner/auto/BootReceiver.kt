package app.mcleaner.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mcleaner.MCleanerApp
import app.mcleaner.core.policy.AutoBoostGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext as MCleanerApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.container.settings.current().autoBoost) {
                    AutoBoostScheduler.schedule(context, AutoBoostGate.BOOT_DELAY_MS)
                    WakeWatchService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
