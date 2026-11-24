package app.mcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.mcleaner.auto.WakeWatchService
import app.mcleaner.ui.MainScreen
import app.mcleaner.ui.theme.MCleanerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MCleanerApp).container
        lifecycleScope.launch {
            if (container.settings.current().autoBoost) WakeWatchService.start(this@MainActivity)
        }
        // Once per launch (not on rotation): silent, retries while offline.
        if (savedInstanceState == null) container.updates.autoCheck()
        setContent {
            MCleanerTheme { MainScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as MCleanerApp).container.shizuku.refresh()
    }
}
