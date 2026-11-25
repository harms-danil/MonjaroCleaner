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
import app.mcleaner.domain.StorageInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NoBackgroundEntry(val packageName: String, val label: String, val restricted: Boolean?)

data class MaintenanceUi(
    val loading: Boolean = true,
    /** Apps by dexopt status: how many are prepared well (profile or full) out of all with code. */
    val optimal: Int = 0,
    val total: Int = 0,
    val plan: List<CompileStep> = emptyList(),
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
    val shizuku = c.shizuku.state
    val settings = c.settings.settings

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
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
            _ui.value = _ui.value.copy(
                loading = false,
                optimal = statuses.values.count(Dexopt::isOptimal),
                total = statuses.size,
                plan = plan,
                labels = labels,
                storage = c.maintenance.storageInfo(),
                speed = c.maintenance.speedHistory(),
                noBackground = entries,
                suggestions = BackgroundPolicy.suggestions(top, s.noBackground, canRestrict).take(5),
                candidates = installed.filter { canRestrict(it.packageName) && it.packageName !in s.noBackground }.sortedBy { it.label.lowercase() },
            )
        }
    }

    /** «Обслужить сейчас»: compile what is worth it, then trim the storage. */
    fun runAll() = runJob {
        val p = c.maintenance.compile(_ui.value.plan)
        val trimmed = c.maintenance.trimStorage()
        c.settings.setLastMaintenance(System.currentTimeMillis())
        tr(
            "Готово: скомпилировано ${p.done - p.failed.size} из ${p.total}" + (if (p.failed.isNotEmpty()) ", с ошибкой: ${p.failed.size}" else "") +
                (if (trimmed) ". Обслуживание накопителя запущено" else ""),
            "Done: compiled ${p.done - p.failed.size} of ${p.total}" + (if (p.failed.isNotEmpty()) ", failed: ${p.failed.size}" else "") +
                (if (trimmed) ". Storage trim started" else ""),
        )
    }

    fun compileOnly() = runJob {
        val p = c.maintenance.compile(_ui.value.plan)
        tr("Скомпилировано ${p.done - p.failed.size} из ${p.total}", "Compiled ${p.done - p.failed.size} of ${p.total}")
    }

    fun trim() = runJob {
        if (c.maintenance.trimStorage()) tr("Обслуживание накопителя запущено — система закончит его сама за минуту-две", "Storage trim started — the system finishes it in a minute or two")
        else tr("Не удалось запустить обслуживание накопителя", "Could not start the storage trim")
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
            val r = c.maintenance.writeSpeedTest()
            _ui.value = _ui.value.copy(
                testing = false,
                speed = c.maintenance.speedHistory(),
                message = r?.let { tr("Запись: ${mbps(it.writeMBps)} МБ/с", "Write: ${mbps(it.writeMBps)} MB/s") } ?: tr("Тест не удался", "Test failed"),
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

    private fun runJob(block: suspend () -> String) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val msg = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _ui.value = _ui.value.copy(message = tr("Остановлено", "Stopped"))
                throw e
            }
            _ui.value = _ui.value.copy(message = msg)
            refresh()
        }
    }
}
