package app.mcleaner.privileged

import app.mcleaner.core.model.ShellResult

/** Test double: records every call; after [dieAfterCalls] calls it behaves like a dead Shizuku. */
class FakePrivileged(
    var available: Boolean = true,
    val responses: MutableMap<String, ShellResult> = mutableMapOf(),
    var dieAfterCalls: Int? = null,
    var deleteCacheResult: (String) -> Boolean = { true },
) : Privileged {
    val calls = mutableListOf<String>()
    val deleteCacheCalls = mutableListOf<Pair<String, Int>>()
    val removedTasks = mutableListOf<String>()

    override val isAvailable: Boolean get() = available

    private fun tick(): Boolean {
        if (!available) return false
        val limit = dieAfterCalls
        if (limit != null && calls.size + deleteCacheCalls.size >= limit) {
            available = false
            return false
        }
        return true
    }

    override suspend fun exec(command: String): ShellResult {
        if (!tick()) return ShellResult(-1, "dead")
        calls += command
        return responses[command] ?: ShellResult(0, "")
    }

    override suspend fun deleteCache(packageName: String, userId: Int): Boolean {
        if (!tick()) return false
        deleteCacheCalls += packageName to userId
        return deleteCacheResult(packageName)
    }

    override suspend fun removeTasks(packageNames: Collection<String>, userId: Int): Int {
        if (!available) return 0
        removedTasks += packageNames
        return packageNames.size
    }
}
