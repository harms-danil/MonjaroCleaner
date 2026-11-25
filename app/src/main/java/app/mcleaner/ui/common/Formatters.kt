package app.mcleaner.ui.common

import app.mcleaner.core.i18n.Lang
import app.mcleaner.core.i18n.Language
import app.mcleaner.core.i18n.tr
import java.util.Locale
import kotlin.math.roundToLong

object Formatters {
    private const val MB = 1024.0 * 1024
    private const val GB = 1024.0 * MB
    private val ru = Locale.forLanguageTag("ru-RU")
    private val numLocale: Locale get() = if (Lang.current == Language.EN) Locale.US else ru

    fun bytes(b: Long): String {
        val v = b.coerceAtLeast(0)
        return if (v >= GB) "${gigs(v)} ${tr("ГБ", "GB")}" else "${(v / MB).roundToLong()} ${tr("МБ", "MB")}"
    }

    fun kb(kb: Long): String = bytes(kb * 1024)

    fun usedOfTotal(usedBytes: Long, totalBytes: Long): String =
        "${gigs(usedBytes.coerceAtLeast(0))} / ${gigs(totalBytes.coerceAtLeast(0))} ${tr("ГБ", "GB")}"

    private fun gigs(b: Long): String = String.format(numLocale, "%.1f", b / GB).removeSuffix(",0").removeSuffix(".0")
}
