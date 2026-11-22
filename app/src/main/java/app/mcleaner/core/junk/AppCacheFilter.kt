package app.mcleaner.core.junk

import app.mcleaner.core.policy.BoostPolicy

/** Decides whether an `Android/data/<pkg>/cache` dir may be offered as junk: same rules as "clear all cache". */
object AppCacheFilter {
    private val pattern = Regex("""/Android/data/([^/]+)/cache/?$""")

    fun packageOf(path: String): String? = pattern.find(path)?.groupValues?.get(1)

    fun allowed(path: String, whitelist: Set<String>): Boolean {
        val pkg = packageOf(path) ?: return false
        return pkg !in whitelist && !BoostPolicy.isCarSystem(pkg)
    }
}
