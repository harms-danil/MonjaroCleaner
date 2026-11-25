package app.mcleaner.core.policy

/** Parsers for the sources of packages that a boost must never stop (the car would lose a function). */
object ProtectedPackages {
    /**
     * Packages of a secure-setting component list such as `enabled_accessibility_services` or
     * `enabled_notification_listeners`: `pkg/cls:pkg2/cls2`. Garbage entries are ignored.
     */
    fun fromComponentList(value: String?): Set<String> =
        value.orEmpty().split(':')
            .map { it.trim().substringBefore('/').trim() }
            .filterTo(mutableSetOf()) { PackageNames.isValid(it) }

    /**
     * Owners of every media session in `dumpsys media_session`, in any state: a paused or stopped
     * player still holds its queue and position, which a force-stop would lose.
     */
    fun mediaSessionPackages(dumpsysMediaSession: String): Set<String> =
        dumpsysMediaSession.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package=") }
            .map { it.removePrefix("package=").trim() }
            .filterTo(mutableSetOf()) { PackageNames.isValid(it) }
}
