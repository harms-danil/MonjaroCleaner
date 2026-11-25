package app.mcleaner.core.junk

import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.core.policy.PackageNames

/** Decides whether an `Android/data/<pkg>/cache` dir may be offered as junk: same rules as "clear all cache". */
object AppCacheFilter {
    private val pattern = Regex("""/Android/data/([^/]+)/cache/?$""")

    fun packageOf(path: String): String? = pattern.find(path)?.groupValues?.get(1)

    fun allowed(path: String, whitelist: Set<String>): Boolean {
        val pkg = packageOf(path) ?: return false
        return PackageNames.isValid(pkg) && pkg !in whitelist && !BoostPolicy.isCarSystem(pkg)
    }

    private val ownerPattern = Regex("""/Android/(?:data|media)/([^/]+)(?:/|$)""")

    /** The app that owns a path under `Android/data/<pkg>` or `Android/media/<pkg>`. */
    fun ownerOf(path: String): String? = ownerPattern.find(path)?.groupValues?.get(1)

    /** Files of whitelisted and car apps are never offered as junk (offline maps, tracks, settings). */
    fun protectedOwner(path: String, whitelist: Set<String>): Boolean {
        val pkg = ownerOf(path) ?: return false
        return pkg in whitelist || BoostPolicy.isCarSystem(pkg)
    }
}
