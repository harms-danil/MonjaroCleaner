package app.mcleaner.privileged

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.annotation.Keep
import app.mcleaner.IShellService
import app.mcleaner.core.policy.RecentTask
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku-spawned app_process as uid shell. Hidden API restrictions do not apply here.
 */
class ShellUserService() : IShellService.Stub() {

    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val (code, out) = ShellRunner.run(command, TIMEOUT_MS, MAX_OUTPUT_CHARS)
        "$code\n$out"
    } catch (t: Throwable) {
        "-1\n${t.javaClass.simpleName}: ${t.message}"
    }

    /**
     * PMS may "silently ignore" callers without INTERNAL_DELETE_CACHE_FILES (no exception, no callback),
     * so success is only what the observer reports within [OBSERVER_TIMEOUT_MS].
     */
    override fun deleteCache(packageName: String, userId: Int): Boolean = try {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "package") as IBinder
        val pm = Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
        val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
        val result = ObserverBinder()
        val observer = Class.forName("android.content.pm.IPackageDataObserver\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, result)
        pm.javaClass
            .getMethod("deleteApplicationCacheFilesAsUser", String::class.java, Int::class.javaPrimitiveType, observerClass)
            .invoke(pm, packageName, userId, observer)
        result.await(OBSERVER_TIMEOUT_MS) == true
    } catch (t: Throwable) {
        false
    }

    /**
     * Force-stop leaves the app's card in Recents, so it still looks running. Removes those tasks through
     * IActivityManager (Android 9) / IActivityTaskManager (10+); shell has REMOVE_TASKS.
     */
    override fun removeTasks(packageNames: Array<out String>, userId: Int): Int = try {
        val wanted = packageNames.toSet()
        val (name, stub) = if (android.os.Build.VERSION.SDK_INT >= 29) {
            "activity_task" to "android.app.IActivityTaskManager\$Stub"
        } else {
            "activity" to "android.app.IActivityManager\$Stub"
        }
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as IBinder
        val atm = Class.forName(stub).getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val slice = atm.javaClass
            .getMethod("getRecentTasks", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(atm, MAX_RECENTS, RECENT_WITH_EXCLUDED, userId)
        val tasks = (slice?.javaClass?.getMethod("getList")?.invoke(slice) as? List<*>).orEmpty().mapNotNull { info ->
            info ?: return@mapNotNull null
            val id = taskIdOf(info) ?: return@mapNotNull null
            RecentTask(id, packageOf(info))
        }
        val remove = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
        app.mcleaner.core.policy.RecentTasks.idsToRemove(tasks, wanted).count { id ->
            runCatching { remove.invoke(atm, id) as? Boolean ?: false }.getOrDefault(false)
        }
    } catch (t: Throwable) {
        0
    }

    private fun taskIdOf(info: Any): Int? = listOf("persistentId", "taskId").firstNotNullOfOrNull { f ->
        runCatching { info.javaClass.getField(f).getInt(info) }.getOrNull()?.takeIf { it > 0 }
    }

    private fun packageOf(info: Any): String? = listOf("baseIntent", "realActivity", "baseActivity", "topActivity").firstNotNullOfOrNull { f ->
        when (val v = runCatching { info.javaClass.getField(f).get(info) }.getOrNull()) {
            is android.content.Intent -> v.component?.packageName
            is android.content.ComponentName -> v.packageName
            else -> null
        }
    }

    /** Hand-rolled IPackageDataObserver stub: `oneway void onRemoveCompleted(String packageName, boolean succeeded)`. */
    private class ObserverBinder : Binder() {
        private val latch = CountDownLatch(1)

        @Volatile
        private var succeeded: Boolean? = null

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == FIRST_CALL_TRANSACTION) {
                data.enforceInterface(DESCRIPTOR)
                data.readString()
                succeeded = data.readInt() != 0
                latch.countDown()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }

        fun await(timeoutMs: Long): Boolean? = if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) succeeded else null

        private companion object {
            const val DESCRIPTOR = "android.content.pm.IPackageDataObserver"
        }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val MAX_OUTPUT_CHARS = 200_000
        const val OBSERVER_TIMEOUT_MS = 3_000L
        const val MAX_RECENTS = 200

        /** ActivityManager.RECENT_WITH_EXCLUDED: also tasks hidden from Recents by their own flags. */
        const val RECENT_WITH_EXCLUDED = 1
    }
}
