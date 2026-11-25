package app.mcleaner.privileged

import app.mcleaner.core.i18n.tr
import app.mcleaner.core.model.ShellResult

/**
 * Operations that need shell (uid 2000) rights. Implementations never throw:
 * when unavailable or failing, [exec] returns a non-zero [ShellResult] and [deleteCache] returns false.
 */
interface Privileged {
    val isAvailable: Boolean
    suspend fun exec(command: String): ShellResult
    suspend fun deleteCache(packageName: String, userId: Int): Boolean
}

object NoPrivileged : Privileged {
    override val isAvailable: Boolean = false
    override suspend fun exec(command: String) = ShellResult(-1, tr("Полный режим недоступен", "Full mode unavailable"))
    override suspend fun deleteCache(packageName: String, userId: Int) = false
}

/** Single-quote a value for `sh -c`. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
