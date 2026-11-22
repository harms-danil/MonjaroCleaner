package app.mcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.mcleaner.ui.MainScreen
import app.mcleaner.ui.theme.MCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MCleanerTheme { MainScreen() }
        }
    }
}
