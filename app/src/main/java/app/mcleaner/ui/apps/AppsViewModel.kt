package app.mcleaner.ui.apps

import app.mcleaner.core.i18n.tr
import app.mcleaner.core.log.L
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.model.AppInfo
import app.mcleaner.core.policy.BoostPolicy
import app.mcleaner.privileged.shellQuote
import app.mcleaner.ui.common.Formatters
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

enum class AppSort { MEMORY, CPU, CACHE }

data class CacheStuck(val packageName: String, val label: String, val bytes: Long, val pageOpened: Boolean)

data class AppsUi(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val sort: AppSort = AppSort.MEMORY,
    val onlyRunning: Boolean = false,
    val message: String? = null,
    /** Launchers and the keyboard: never frozen. Null until loaded, and then nothing may be frozen. */
    val freezeProtected: Set<String>? = null,
    /** «Кэш» did not help: the system kept the internal cache. Shown as an explanation dialog. */
    val cacheStuck: CacheStuck? = null,
) {
    val visible: List<AppInfo>
        get() = apps
            .filter { !onlyRunning || it.isRunning }
            .sortedWith(
                when (sort) {
                    AppSort.MEMORY -> compareByDescending<AppInfo> { it.pssKb ?: -1L }.thenByDescending { it.cacheBytes ?: -1L }
                    AppSort.CPU -> compareByDescending<AppInfo> { it.cpuPercent ?: -1.0 }.thenByDescending { it.pssKb ?: -1L }
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

    private var loadedAt = 0L
    private var loadJob: kotlinx.coroutines.Job? = null

    fun reload() {
        if (loadJob?.isActive == true) return
        _ui.value = _ui.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            try {
                val apps = c.apps.loadApps()
                val protected = c.apps.freezeProtectedPackages()
                _ui.value = _ui.value.copy(apps = apps, freezeProtected = protected)
                loadedAt = android.os.SystemClock.elapsedRealtime()
            } finally {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    /** On return to the screen: skip if the list was loaded a moment ago (first show, quick tab switches). */
    fun reloadIfStale() {
        val waiting = awaitingCache
        if (waiting != null) {
            // Back from the system app page: reload now and see whether the cache went down.
            awaitingCache = null
            viewModelScope.launch {
                loadJob?.join()
                reload()
                loadJob?.join()
                val after = _ui.value.apps.firstOrNull { it.packageName == waiting.packageName }?.cacheBytes
                L.i("Apps", "cache of ${waiting.packageName}: ${waiting.cacheBytes} -> $after")
                if (app.mcleaner.core.policy.CacheCheck.notCleared(waiting.cacheBytes, after)) {
                    _ui.value = _ui.value.copy(cacheStuck = CacheStuck(waiting.packageName, waiting.label, after ?: 0, pageOpened = true))
                }
            }
            return
        }
        if (android.os.SystemClock.elapsedRealtime() - loadedAt > STALE_MS) reload()
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

    fun canFreeze(app: AppInfo): Boolean {
        val protected = _ui.value.freezeProtected ?: return false
        return BoostPolicy.canFreeze(app, selfPackage, whitelist.value, protected)
    }

    fun canStop(app: AppInfo): Boolean = BoostPolicy.canStopManually(app, selfPackage)

    fun stop(app: AppInfo) = act(tr("${app.label}: остановлено", "${app.label}: stopped"), tr("${app.label}: не удалось остановить", "${app.label}: could not stop")) {
        c.shizuku.exec("am force-stop ${shellQuote(app.packageName)}").ok.also { ok ->
            if (ok) c.shizuku.removeTasks(listOf(app.packageName), c.apps.currentUser())
        }
    }

    /**
     * Android 8+ refuses one app's internal cache to non-system callers. Then: clear its external cache
     * and hand over to the system app page ([openSystemPage]) where «Очистить кэш» works.
     */
    /** Set when the system app page was opened for the internal cache: checked when the user comes back. */
    private var awaitingCache: AppInfo? = null

    fun dismissCacheStuck() {
        _ui.value = _ui.value.copy(cacheStuck = null)
    }

    fun clearCache(app: AppInfo, openSystemPage: () -> Boolean) {
        viewModelScope.launch {
            val ok = runCatching { c.cacheCleaner.clearOne(app.packageName, c.apps.currentUser()) }.getOrDefault(false)
            L.i("Apps", "clear cache ${app.packageName}: internal ${if (ok) "cleared" else "refused"}")
            if (ok) {
                _ui.value = _ui.value.copy(message = tr("${app.label}: кэш очищен", "${app.label}: cache cleared"))
            } else {
                val ext = c.cacheCleaner.clearExternal(listOf(app.packageName), android.os.Environment.getExternalStorageDirectory().path)
                _ui.value = _ui.value.copy(
                    message = (if (ext.bytes > 0) tr("Внешний кэш очищен (${Formatters.bytes(ext.bytes)}). ", "External cache cleared (${Formatters.bytes(ext.bytes)}). ") else "") +
                        tr("Внутренний — в системной карточке: «Хранилище» → «Очистить кэш»", "Internal cache: Storage → Clear cache in the system page"),
                )
                val opened = openSystemPage()
                L.i("Apps", "system app page for ${app.packageName}: opened $opened, cache before ${app.cacheBytes}")
                if (opened) {
                    awaitingCache = app
                } else {
                    _ui.value = _ui.value.copy(cacheStuck = CacheStuck(app.packageName, app.label, app.cacheBytes ?: 0, pageOpened = false))
                }
            }
            reload()
        }
    }

    fun freeze(app: AppInfo) = act(tr("${app.label}: заморожено", "${app.label}: frozen"), tr("${app.label}: не удалось заморозить", "${app.label}: could not freeze")) {
        // Fresh values, not the cached UI state: the launcher or keyboard may have changed meanwhile.
        c.freezeManager.freeze(app, selfPackage, c.settings.current().whitelist, c.apps.freezeProtectedPackages())
    }

    fun unfreeze(app: AppInfo) = act(tr("${app.label}: разморожено", "${app.label}: unfrozen"), tr("${app.label}: не удалось разморозить", "${app.label}: could not unfreeze")) {
        c.freezeManager.unfreeze(app.packageName)
    }

    fun setWhitelisted(app: AppInfo, on: Boolean) {
        viewModelScope.launch {
            c.settings.setWhitelisted(app.packageName, on)
            L.i("Apps", "whitelist ${if (on) "+" else "-"}${app.packageName}")
            _ui.value = _ui.value.copy(
                message = if (on) tr("${app.label}: в белом списке — «Ускорить» его не тронет", "${app.label}: whitelisted — Boost will not touch it")
                else tr("${app.label}: убрано из белого списка", "${app.label}: removed from the whitelist"),
            )
        }
    }

    suspend fun icon(pkg: String): ImageBitmap? = icons[pkg] ?: withContext(Dispatchers.IO) {
        runCatching {
            c.appContext.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()?.also { icons[pkg] = it }
    }

    private fun act(ok: String, fail: String, block: suspend () -> Boolean) {
        viewModelScope.launch {
            val success = runCatching { block() }.onFailure { L.e("Apps", "action failed: $fail", it) }.getOrDefault(false)
            L.i("Apps", if (success) ok else fail)
            _ui.value = _ui.value.copy(message = if (success) ok else fail)
            if (success) reload()
        }
    }

    private companion object {
        const val STALE_MS = 3_000L
    }
}
