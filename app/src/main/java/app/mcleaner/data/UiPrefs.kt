package app.mcleaner.data

import android.content.Context
import app.mcleaner.core.i18n.Lang
import app.mcleaner.core.i18n.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Language and theme. SharedPreferences, not DataStore: both are needed synchronously before the first frame.
 */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(read(KEY_THEME, ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    val language: Language get() = Lang.current

    init {
        Lang.current = read(KEY_LANG, Language.RU)
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    /** Returns true if the language changed (the caller recreates the activity). */
    fun setLanguage(lang: Language): Boolean {
        if (lang == Lang.current) return false
        prefs.edit().putString(KEY_LANG, lang.name).apply()
        Lang.current = lang
        return true
    }

    private inline fun <reified T : Enum<T>> read(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LANG = "language"
    }
}
