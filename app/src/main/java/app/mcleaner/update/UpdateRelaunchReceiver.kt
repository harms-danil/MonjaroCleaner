package app.mcleaner.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mcleaner.MainActivity

/**
 * Basic mode: after an update that MCleaner itself started through the system installer, open the new
 * version. Armed right before the install so that updates from adb or elsewhere do not pop the app up.
 * (Android 9/10 allow starting an activity from this broadcast.)
 */
class UpdateRelaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val armedAt = prefs.getLong(KEY, 0L)
        prefs.edit().remove(KEY).apply()
        if (armedAt == 0L || System.currentTimeMillis() - armedAt > TEN_MINUTES) return
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        private const val PREFS = "update"
        private const val KEY = "relaunch_armed_at"
        private const val TEN_MINUTES = 10 * 60 * 1000L

        fun arm(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY, System.currentTimeMillis()).apply()
        }
    }
}
