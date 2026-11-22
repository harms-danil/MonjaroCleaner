package app.mcleaner.ui.common

import java.util.Locale
import kotlin.math.roundToLong

object Formatters {
    private const val MB = 1024.0 * 1024
    private const val GB = 1024.0 * MB
    private val ru = Locale.forLanguageTag("ru-RU")

    fun bytes(b: Long): String {
        val v = b.coerceAtLeast(0)
        return if (v >= GB) "${gigs(v)} ГБ" else "${(v / MB).roundToLong()} МБ"
    }

    fun kb(kb: Long): String = bytes(kb * 1024)

    fun usedOfTotal(usedBytes: Long, totalBytes: Long): String =
        "${gigs(usedBytes.coerceAtLeast(0))} / ${gigs(totalBytes.coerceAtLeast(0))} ГБ"

    private fun gigs(b: Long): String = String.format(ru, "%.1f", b / GB).removeSuffix(",0")
}
