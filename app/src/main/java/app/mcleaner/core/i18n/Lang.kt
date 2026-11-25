package app.mcleaner.core.i18n

/** App language. Russian is the default; English is chosen in «Настройки». */
enum class Language { RU, EN }

object Lang {
    /** Set at app start from the saved choice; the activity is recreated when it changes. */
    @Volatile
    var current: Language = Language.RU
}

/** The user-visible text in the current language. Keep both variants side by side at the call site. */
fun tr(ru: String, en: String): String = if (Lang.current == Language.EN) en else ru
