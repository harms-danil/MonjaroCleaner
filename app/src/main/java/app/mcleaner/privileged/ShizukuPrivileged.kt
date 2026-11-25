package app.mcleaner.privileged

import app.mcleaner.core.i18n.tr
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.mcleaner.BuildConfig
import app.mcleaner.IShellService
import app.mcleaner.core.model.ShellResult
import app.mcleaner.core.policy.BoostPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import app.mcleaner.core.policy.RecentTasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

class ShizukuPrivileged(private val context: Context) : Privileged {

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var service: IShellService? = null
    @Volatile
    private var serviceReady = CompletableDeferred<IShellService>()

    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShellUserService::class.java))
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val s = IShellService.Stub.asInterface(binder)
            service = s
            serviceReady.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dropService()
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            dropService()
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    override val isAvailable: Boolean get() = _state.value == ShizukuState.READY

    /** Recomputes [state]; binds the user service when ready. */
    fun refresh() {
        val newState = computeState()
        _state.value = newState
        if (newState == ShizukuState.READY) bind()
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun bind() {
        if (service != null) return
        runCatching { Shizuku.bindUserService(args, connection) }
    }

    override suspend fun exec(command: String): ShellResult {
        val s = awaitService() ?: return ShellResult(-1, tr("Shizuku недоступен", "Shizuku unavailable"))
        return withContext(Dispatchers.IO) {
            try {
                val raw = s.exec(command) ?: return@withContext ShellResult(-1, "")
                val code = raw.substringBefore('\n').trim().toIntOrNull() ?: -1
                ShellResult(code, raw.substringAfter('\n', ""))
            } catch (t: Throwable) {
                dropService()
                refresh()
                ShellResult(-1, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    override suspend fun deleteCache(packageName: String, userId: Int): Boolean {
        val s = awaitService() ?: return false
        return withContext(Dispatchers.IO) {
            try {
                s.deleteCache(packageName, userId)
            } catch (t: Throwable) {
                dropService()
                refresh()
                false
            }
        }
    }

    override suspend fun removeTasks(packageNames: Collection<String>, userId: Int): Int {
        if (packageNames.isEmpty()) return 0
        val s = awaitService() ?: return 0
        val viaBinder = withContext(Dispatchers.IO) {
            try {
                s.removeTasks(packageNames.toTypedArray(), userId)
            } catch (t: Throwable) {
                dropService()
                refresh()
                return@withContext -1
            }
        }
        if (viaBinder != 0) return viaBinder.coerceAtLeast(0)
        // Android 9: shell has no REMOVE_TASKS (binder returns 0), but may remove stacks.
        val stopped = packageNames.toSet()
        val orphans = RecentTasks.orphansToRestore(RecentTasks.parseRecents(exec("dumpsys activity recents").out), stopped)
        orphans.forEach { exec("am stack move-task $it ${RecentTasks.NO_SUCH_STACK} true") }
        val stacks = RecentTasks.stacksToRemove(RecentTasks.parseStacks(exec("am stack list").out), stopped)
        return stacks.count { exec("am stack remove $it").ok }
    }

    private suspend fun awaitService(): IShellService? {
        if (!isAvailable) return null
        service?.let { return it }
        bind()
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { serviceReady.await() }
    }

    private fun dropService() {
        service = null
        if (serviceReady.isCompleted) serviceReady = CompletableDeferred()
    }

    private fun computeState(): ShizukuState {
        val installed = runCatching {
            context.packageManager.getPackageInfo(BoostPolicy.SHIZUKU_PACKAGE, 0)
        }.isSuccess
        val alive = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        return when {
            !alive && !installed -> ShizukuState.NOT_INSTALLED
            !alive -> ShizukuState.NOT_RUNNING
            runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1) != PackageManager.PERMISSION_GRANTED ->
                ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    }

    private companion object {
        const val REQUEST_CODE = 307
        const val BIND_TIMEOUT_MS = 8_000L
    }
}
