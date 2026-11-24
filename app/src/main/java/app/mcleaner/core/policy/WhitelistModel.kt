package app.mcleaner.core.policy

data class InstalledApp(val packageName: String, val label: String, val isSystem: Boolean)

data class WhitelistEntry(val packageName: String, val label: String, val installed: Boolean)

object WhitelistModel {
    /** Whitelisted apps: installed first (by label), then packages not installed on this unit. */
    fun entries(whitelist: Set<String>, installed: List<InstalledApp>): List<WhitelistEntry> {
        val byPkg = installed.associateBy { it.packageName }
        return whitelist
            .map { pkg -> byPkg[pkg]?.let { WhitelistEntry(pkg, it.label, true) } ?: WhitelistEntry(pkg, pkg, false) }
            .sortedWith(compareBy<WhitelistEntry> { !it.installed }.thenBy { it.label.lowercase() })
    }

    /**
     * Apps that can be added: not yet whitelisted, not the car's own (always protected anyway), not MCleaner.
     * User apps first, then other system apps; filtered by [query] on label or package.
     */
    fun candidates(whitelist: Set<String>, installed: List<InstalledApp>, query: String, selfPackage: String): List<InstalledApp> {
        val q = query.trim().lowercase()
        return installed
            .filter { it.packageName !in whitelist && it.packageName != selfPackage && !BoostPolicy.isCarSystem(it.packageName) }
            .filter { q.isEmpty() || q in it.label.lowercase() || q in it.packageName.lowercase() }
            .sortedWith(compareBy<InstalledApp> { it.isSystem }.thenBy { it.label.lowercase() })
    }
}
