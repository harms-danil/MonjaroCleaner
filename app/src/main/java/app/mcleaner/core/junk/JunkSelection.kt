package app.mcleaner.core.junk

enum class CategoryState { ALL, SOME, NONE }

/** Selection of junk files by path; categories are just a way to (de)select many at once. */
object JunkSelection {
    /** Everything except installers, which may still be needed. */
    fun initial(junk: Map<JunkCategory, List<JunkItem>>): Set<String> =
        junk.filterKeys { it != JunkCategory.APK }.values.flatten().mapTo(mutableSetOf()) { it.path }

    fun state(category: JunkCategory, junk: Map<JunkCategory, List<JunkItem>>, selected: Set<String>): CategoryState {
        val items = junk[category].orEmpty()
        val n = items.count { it.path in selected }
        return when {
            n == 0 -> CategoryState.NONE
            n == items.size -> CategoryState.ALL
            else -> CategoryState.SOME
        }
    }

    fun toggleItem(selected: Set<String>, path: String): Set<String> =
        if (path in selected) selected - path else selected + path

    /** Full category → clear it; partial or empty → select all its files. */
    fun toggleCategory(selected: Set<String>, category: JunkCategory, junk: Map<JunkCategory, List<JunkItem>>): Set<String> {
        val paths = junk[category].orEmpty().map { it.path }
        return if (state(category, junk, selected) == CategoryState.ALL) selected - paths.toSet() else selected + paths
    }

    fun selectedItems(junk: Map<JunkCategory, List<JunkItem>>, selected: Set<String>): List<JunkItem> =
        JunkCategory.entries.flatMap { junk[it].orEmpty() }.filter { it.path in selected }

    fun selectedBytes(junk: Map<JunkCategory, List<JunkItem>>, selected: Set<String>): Long =
        selectedItems(junk, selected).sumOf { it.sizeBytes }

    fun retain(selected: Set<String>, junk: Map<JunkCategory, List<JunkItem>>): Set<String> =
        selected intersect junk.values.flatten().mapTo(mutableSetOf()) { it.path }
}
