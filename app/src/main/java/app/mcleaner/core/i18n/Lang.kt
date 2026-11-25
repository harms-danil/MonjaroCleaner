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

/** Russian noun form for [n]: 1 ускорение, 2 ускорения, 5 ускорений (11–14 → many). */
fun ruPlural(n: Long, one: String, few: String, many: String): String {
    val n100 = kotlin.math.abs(n) % 100
    val n10 = n100 % 10
    return when {
        n100 in 11..14 -> many
        n10 == 1L -> one
        n10 in 2..4 -> few
        else -> many
    }
}

/** "5 ускорений" / "5 boosts". */
fun count(n: Int, ruOne: String, ruFew: String, ruMany: String, enOne: String, enMany: String): String =
    "$n " + tr(ruPlural(n.toLong(), ruOne, ruFew, ruMany), if (n == 1) enOne else enMany)
