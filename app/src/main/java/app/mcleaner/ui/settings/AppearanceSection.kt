package app.mcleaner.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mcleaner.core.i18n.Language
import app.mcleaner.core.i18n.tr
import app.mcleaner.data.ThemeMode
import app.mcleaner.data.UiPrefs

/** «Настройки» → language (Russian by default) and theme (system / light / dark). */
@Composable
fun AppearanceSection(prefs: UiPrefs) {
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Внешний вид", "Appearance"), style = MaterialTheme.typography.titleMedium)
        ChoiceRow(tr("Язык", "Language")) {
            Language.entries.forEach { lang ->
                Choice(
                    label = if (lang == Language.RU) "Русский" else "English",
                    selected = prefs.language == lang,
                    onClick = { if (prefs.setLanguage(lang)) activity?.recreate() },
                )
            }
        }
        ChoiceRow(tr("Тема", "Theme")) {
            ThemeMode.entries.forEach { mode ->
                Choice(
                    label = when (mode) {
                        ThemeMode.SYSTEM -> tr("Как в системе", "System")
                        ThemeMode.LIGHT -> tr("Светлая", "Light")
                        ThemeMode.DARK -> tr("Тёмная", "Dark")
                    },
                    selected = theme == mode,
                    onClick = { prefs.setTheme(mode) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.width(120.dp))
        chips()
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
}
