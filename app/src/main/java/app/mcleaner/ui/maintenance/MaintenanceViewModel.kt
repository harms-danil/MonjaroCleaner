package app.mcleaner.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.i18n.tr
import app.mcleaner.core.log.L
import app.mcleaner.core.maintenance.CompileStep
import app.mcleaner.core.maintenance.Dexopt
import app.mcleaner.core.maintenance.SpeedSample
import app.mcleaner.core.policy.BackgroundPolicy
import app.mcleaner.core.policy.InstalledApp
import app.mcleaner.domain.CompileReport
import app.mcleaner.domain.StorageInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class NoBackgroundEntry(val packageName: String, val label: String, val restricted: Boolean?)

data class MaintenanceUi(
    val loading: Boolean = true,
    /** Apps by dexopt status: how many are prepared well (profile or full) out of all with code. */
    val optimal: Int = 0,
    val total: Int = 0,
    val plan: List<CompileStep> = emptyList(),
    /** Current dexopt status of the apps in the plan. */
    val statuses: Map<String, String> = emptyMap(),
    /** Apps of the plan the user keeps ticked. */
    val selected: Set<String> = emptySet(),
    val lastCompile: CompileReport? = null,
    val labels: Map<String, String> = emptyMap(),
    val storage: StorageInfo? = null,
    val speed: List<SpeedSample> = emptyList(),
    val testing: Boolean = false,
    val noBackground: List<NoBackgroundEntry> = emptyList(),
    /** From the boost history: often stopped apps that can be restricted (package, times). */
    val suggestions: List<Pair<String, Int>> = emptyList(),
    val candidates: List<InstalledApp> = emptyList(),
    val message: String? = null,
)

class MaintenanceViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(MaintenanceUi())
    val ui: StateFlow<MaintenanceUi> = _ui.asStateFlow()
    val compile = c.maintenance.compile
    val trim = c.maintenance.trim
    val shizuku = c.shizuku.state
    val settings = c.settings.settings

    private var job: Job? = null
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val installed = c.apps.installedApps()
            val labels = installed.associate { it.packageName to it.label }
            val statuses = c.maintenance.dexoptStatus()
            val plan = c.compilePlan(statuses, installed)
            val s = c.settings.current()
            val protected = c.apps.carUsabilityPackages() + c.apps.freezeProtectedPackages()
            val canRestrict = { pkg: String ->
                installed.firstOrNull { it.packageName == pkg }?.let { a ->
                    BackgroundPolicy.canRestrict(app.mcleaner.core.model.AppInfo(a.packageName, a.label, a.isSystem, false, null, null, false), c.appContext.packageName, s.whitelist, protected)
                } ?: false
            }
            val top = app.mcleaner.core.history.BoostHistory.topStopped(c.history.load(), limit = 20)
            val entries = s.noBackground.sorted().map { NoBackgroundEntry(it, labels[it] ?: it, c.backgroundLimiter.state(it)) }
            val prev = _ui.value
            // Keep the user's ticks across refreshes; new plan entries start ticked.
            val known = prev.plan.map { it.packageName }.toSet()
            val selected = plan.map { it.packageName }.filter { it !in known || it in prev.selected }.toSet()
            _ui.value = prev.copy(
                loading = false,
                optimal = statuses.values.count(Dexopt::isOptimal),
                total = statuses.size,
                plan = plan,
                statuses = statuses,
                selected = selected,
                labels = labels,
                storage = c.maintenance.storageInfo(),
                speed = c.maintenance.speedHistory(),
                noBackground = entries,
                suggestions = BackgroundPolicy.suggestions(top, s.noBackground, canRestrict).take(5),
                candidates = installed.filter { canRestrict(it.packageName) && it.packageName !in s.noBackground }.sortedBy { it.label.lowercase() },
            )
        }
    }

    fun toggle(pkg: String) {
        val u = _ui.value
        _ui.value = u.copy(selected = if (pkg in u.selected) u.selected - pkg else u.selected + pkg)
    }

    fun selectAll(on: Boolean) {
        val u = _ui.value
        _ui.value = u.copy(selected = if (on) u.plan.map { it.packageName }.toSet() else emptySet())
    }

    private fun chosen() = _ui.value.let { u -> u.plan.filter { it.packageName in u.selected } }

    /** «Обслужить сейчас»: compile the ticked apps, then trim the storage. */
    fun runAll() = runJob {
        val report = c.maintenance.compile(chosen())
        _ui.value = _ui.value.copy(lastCompile = report)
        c.maintenance.trimStorage()
        c.settings.setLastMaintenance(System.currentTimeMillis())
        compileMessage(report)
    }

    fun compileOnly() = runJob {
        val report = c.maintenance.compile(chosen())
        _ui.value = _ui.value.copy(lastCompile = report)
        compileMessage(report)
    }

    private fun compileMessage(r: CompileReport): String {
        val ok = r.outcomes.count { it.ok }
        return tr("Скомпилировано $ok из ${r.outcomes.size}", "Compiled $ok of ${r.outcomes.size}")
    }

    fun trim() = runJob {
        val p = c.maintenance.trimStorage()
        if (p.events.isEmpty() && !p.timedOut) tr("Не удалось запустить обслуживание накопителя", "Could not start the storage trim") else null
    }

    fun stop() {
        job?.cancel()
        L.i("Maintenance", "stopped by user")
    }

    fun speedTest() {
        if (_ui.value.testing) return
        viewModelScope.launch {
            val free = android.os.StatFs(c.appContext.filesDir.path).availableBytes
            if (free < 1024L * 1024 * 1024) {
                _ui.value = _ui.value.copy(message = tr("Для теста нужно хотя бы 1 ГБ свободного места", "The test needs at least 1 GB of free space"))
                return@launch
            }
            _ui.value = _ui.value.copy(testing = true)
            // Read test: APKs of apps that are not running, so their files are unlikely to be in memory.
            val notRunning = c.apps.loadApps().filter { !it.isRunning }.map { it.packageName }
            val pm = c.appContext.packageManager
            val files = notRunning.mapNotNull { runCatching { File(pm.getApplicationInfo(it, 0).sourceDir) }.getOrNull() }
            val r = c.maintenance.speedTest(files)
            _ui.value = _ui.value.copy(
                testing = false,
                speed = c.maintenance.speedHistory(),
                message = if (r == null) tr("Тест не удался", "Test failed") else null,
            )
        }
    }

    fun setNoBackground(pkg: String, on: Boolean) {
        viewModelScope.launch {
            c.settings.setNoBackground(pkg, on)
            val ok = c.backgroundLimiter.set(pkg, on)
            if (!ok) _ui.value = _ui.value.copy(message = tr("Система не приняла настройку для $pkg", "The system did not accept the setting for $pkg"))
            refresh()
        }
    }

    fun setWeekly(on: Boolean) {
        viewModelScope.launch {
            c.settings.setWeeklyMaintenance(on)
            c.syncBackgroundWork()
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun runJob(block: suspend () -> String?) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val msg = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _ui.value = _ui.value.copy(message = tr("Остановлено", "Stopped"))
                throw e
            }
            if (msg != null) _ui.value = _ui.value.copy(message = msg)
            refresh()
        }
    }
}
