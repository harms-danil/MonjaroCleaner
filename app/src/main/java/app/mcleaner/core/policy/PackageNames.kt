package app.mcleaner.core.policy

/** Android package names: at least two dot-separated segments, each starting with a letter. */
object PackageNames {
    private val valid = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    fun isValid(name: String): Boolean = valid.matches(name)
}
