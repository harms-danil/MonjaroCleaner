package app.mcleaner

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.mcleaner.ui.MainScreen
import app.mcleaner.ui.theme.MCleanerTheme
import app.mcleaner.ui.theme.isDarkTheme
import app.mcleaner.ui.theme.windowColor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MCleanerApp).container
        lifecycleScope.launch {
            container.syncBackgroundWork()
            if (container.settings.current().shizukuAutostart) app.mcleaner.auto.ShizukuAutostartWorker.enqueue(this@MainActivity)
        }
        // Once per launch (not on rotation or language change): silent, retries while offline.
        if (savedInstanceState == null) container.updates.autoCheck()
        setContent {
            val mode by container.ui.theme.collectAsStateWithLifecycle()
            val dark = isDarkTheme(mode)
            SideEffect { paintSystemBars(dark) }
            MCleanerTheme(dark) { MainScreen() }
        }
    }

    /** Status and navigation bars follow the chosen theme (dark icons on the light theme). */
    @Suppress("DEPRECATION")
    private fun paintSystemBars(dark: Boolean) {
        val color = windowColor(dark)
        window.statusBarColor = color
        window.navigationBarColor = color
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
        var flags = window.decorView.systemUiVisibility
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        flags = if (dark) flags and light.inv() else flags or light
        window.decorView.systemUiVisibility = flags
    }

    override fun onResume() {
        super.onResume()
        app.mcleaner.core.log.L.i("UI", "app opened")
        (application as MCleanerApp).container.shizuku.refresh()
    }

    override fun onPause() {
        app.mcleaner.core.log.L.i("UI", "app hidden")
        super.onPause()
    }
}
