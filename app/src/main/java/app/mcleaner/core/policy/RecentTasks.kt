package app.mcleaner.core.policy

/** One Recents card: task id, the package that owns it (null when unknown) and its stack (-1: none). */
data class RecentTask(val id: Int, val packageName: String?, val stackId: Int = -1)

/** [home]: the launcher's (or Recents') own stack — never removed. */
data class ActivityStack(val id: Int, val tasks: List<RecentTask>, val home: Boolean = false)

/**
 * Force-stop leaves an app's card in the system's running-apps screen. Android 9 does not give shell
 * REMOVE_TASKS, but it does give MANAGE_ACTIVITY_STACKS, and removing a stack drops its tasks from Recents:
 *  1. a card without a stack (StackId=-1) is put back into a stack of its own by `am stack move-task`,
 *  2. every stack made only of stopped apps is removed with `am stack remove`.
 */
object RecentTasks {
    private val recentHeader = Regex("""\* Recent #\d+: TaskRecord\{\S+ #(\d+) .*StackId=(-?\d+)""")
    private val realActivity = Regex("""^\s*realActivity=([^/\s]+)/""")
    private val stackHeader = Regex("""^Stack id=(\d+)""")
    private val specialType = Regex("""mActivityType=(home|recents|assistant)""")
    private val stackTask = Regex("""^\s*taskId=(\d+): ([^/\s]+)/""")

    /** Not a real stack: `move-task` restores the card into a new stack, then fails on this id. */
    const val NO_SUCH_STACK = Int.MAX_VALUE

    /** Task ids to drop from Recents after force-stopping [stopped]: only their own cards. */
    fun idsToRemove(tasks: List<RecentTask>, stopped: Set<String>): List<Int> =
        tasks.filter { it.packageName != null && it.packageName in stopped }.map { it.id }

    /** `dumpsys activity recents`. */
    fun parseRecents(out: String): List<RecentTask> {
        val result = mutableListOf<RecentTask>()
        var id = -1
        var stack = -1
        var pkg: String? = null
        fun flush() {
            if (id > 0) result += RecentTask(id, pkg, stack)
        }
        for (line in out.lineSequence()) {
            val header = recentHeader.find(line)
            if (header != null) {
                flush()
                id = header.groupValues[1].toInt()
                stack = header.groupValues[2].toInt()
                pkg = null
                continue
            }
            if (id > 0 && pkg == null) realActivity.find(line)?.let { pkg = it.groupValues[1] }
        }
        flush()
        return result
    }

    /** `am stack list`. */
    fun parseStacks(out: String): List<ActivityStack> {
        val result = mutableListOf<ActivityStack>()
        var id = -1
        var home = false
        val tasks = mutableListOf<RecentTask>()
        fun flush() {
            if (id >= 0) result += ActivityStack(id, tasks.toList(), home)
            tasks.clear()
            home = false
        }
        for (line in out.lineSequence()) {
            val header = stackHeader.find(line)
            if (header != null) {
                flush()
                id = header.groupValues[1].toInt()
                continue
            }
            if (id >= 0 && specialType.containsMatchIn(line)) home = true
            if (id >= 0) stackTask.find(line)?.let { tasks += RecentTask(it.groupValues[1].toInt(), it.groupValues[2], id) }
        }
        flush()
        return result
    }

    fun orphansToRestore(tasks: List<RecentTask>, stopped: Set<String>): List<Int> =
        tasks.filter { it.stackId < 0 && it.packageName != null && it.packageName in stopped }.map { it.id }

    /** A stack is removed only when every task in it belongs to a stopped app, so nothing else is lost. */
    fun stacksToRemove(stacks: List<ActivityStack>, stopped: Set<String>): List<Int> =
        stacks.filter { s -> !s.home && s.tasks.isNotEmpty() && s.tasks.all { it.packageName != null && it.packageName in stopped } }.map { it.id }
}
