package app.mcleaner.ui.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class AppSort { MEMORY, CACHE }

data class AppsUi(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val sort: AppSort = AppSort.MEMORY,
    val onlyRunning: Boolean = false,
    val message: String? = null,
) {
    val visible: List<AppInfo>
        get() = apps
            .filter { !onlyRunning || it.isRunning }
            .sortedWith(
                when (sort) {
                    AppSort.MEMORY -> compareByDescending<AppInfo> { it.pssKb ?: -1L }.thenByDescending { it.cacheBytes ?: -1L }
                    AppSort.CACHE -> compareByDescending<AppInfo> { it.cacheBytes ?: -1L }.thenByDescending { it.pssKb ?: -1L }
                },
            )
}

class AppsViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(AppsUi())
    val ui: StateFlow<AppsUi> = _ui.asStateFlow()

    val whitelist: StateFlow<Set<String>> =
        c.settings.settings.map { it.whitelist }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val shizuku = c.shizuku.state
    val selfPackage: String = c.appContext.packageName

    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    init {
        reload()
    }

    val hasUsageAccess: Boolean get() = c.apps.hasUsageAccess()

    fun reload() {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val apps = c.apps.loadApps()
            _ui.value = _ui.value.copy(loading = false, apps = apps)
        }
    }

    fun setSort(sort: AppSort) {
        _ui.value = _ui.value.copy(sort = sort)
    }

    fun setOnlyRunning(on: Boolean) {
        _ui.value = _ui.value.copy(onlyRunning = on)
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun canFreeze(app: AppInfo) = BoostPolicy.canFreeze(app, selfPackage)

    fun canStop(app: AppInfo): Boolean = BoostPolicy.canStopManually(app, selfPackage)

    fun stop(app: AppInfo) = act("${app.label}: остановлено", "${app.label}: не удалось остановить") {
        c.shizuku.exec("am force-stop ${app.packageName}").ok
    }

    fun clearCache(app: AppInfo) = act("${app.label}: кэш очищен", "${app.label}: не удалось очистить кэш") {
        c.cacheCleaner.clearOne(app.packageName, c.apps.currentUser())
    }

    fun freeze(app: AppInfo) = act("${app.label}: заморожено", "${app.label}: не удалось заморозить") {
        c.freezeManager.freeze(app, selfPackage)
    }

    fun unfreeze(app: AppInfo) = act("${app.label}: разморожено", "${app.label}: не удалось разморозить") {
        c.freezeManager.unfreeze(app.packageName)
    }

    fun setWhitelisted(app: AppInfo, on: Boolean) {
        viewModelScope.launch { c.settings.setWhitelisted(app.packageName, on) }
    }

    suspend fun icon(pkg: String): ImageBitmap? = icons[pkg] ?: withContext(Dispatchers.IO) {
        runCatching {
            c.appContext.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()?.also { icons[pkg] = it }
    }

    private fun act(ok: String, fail: String, block: suspend () -> Boolean) {
        viewModelScope.launch {
            val success = runCatching { block() }.getOrDefault(false)
            _ui.value = _ui.value.copy(message = if (success) ok else fail)
            if (success) {
                val apps = c.apps.loadApps()
                _ui.value = _ui.value.copy(apps = apps)
            }
        }
    }
}
