package app.mcleaner.ui.cleanup

import app.mcleaner.core.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.core.junk.JunkItem
import app.mcleaner.core.junk.JunkSelection
import app.mcleaner.core.policy.BoostPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CleanupUi(
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val junk: Map<JunkCategory, List<JunkItem>> = emptyMap(),
    /** Selected file paths. */
    val selected: Set<String> = emptySet(),
    val askTrimAll: Boolean = false,
    /** Per-app external cache cleared before asking about the internal one. */
    val externalCleared: app.mcleaner.domain.ExternalCacheResult? = null,
    val confirmDelete: Boolean = false,
    val message: String? = null,
) {
    val selectedItems: List<JunkItem> get() = JunkSelection.selectedItems(junk, selected)
    val selectedBytes: Long get() = selectedItems.sumOf { it.sizeBytes }
}

class CleanupViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CleanupUi())
    val ui: StateFlow<CleanupUi> = _ui.asStateFlow()
    val shizuku = c.shizuku.state

    fun hasFileAccess() = c.apps.hasAllFilesAccess()

    fun clearAllCache() = work {
        val before = c.apps.totalCacheBytes()
        val apps = c.apps.loadApps()
        val whitelist = c.settings.current().whitelist
        val targets = BoostPolicy.cacheTargets(apps, whitelist)
        val result = c.cacheCleaner.clearMany(targets, c.apps.currentUser())
        if (result.perPackageUnsupported) {
            val ext = c.cacheCleaner.clearExternal(targets, android.os.Environment.getExternalStorageDirectory().path)
            _ui.value = _ui.value.copy(askTrimAll = true, externalCleared = ext)
        } else {
            val freed = (before - c.apps.totalCacheBytes()).coerceAtLeast(0)
            _ui.value = _ui.value.copy(
                message = tr("Кэш очищен: ${app.mcleaner.core.i18n.count(result.cleared, "приложение", "приложения", "приложений", "app", "apps")}", "Cache cleared: ${app.mcleaner.core.i18n.count(result.cleared, "приложение", "приложения", "приложений", "app", "apps")}") +
                    if (freed > 0) tr(", освобождено ${app.mcleaner.ui.common.Formatters.bytes(freed)}", ", freed ${app.mcleaner.ui.common.Formatters.bytes(freed)}") else "",
            )
        }
    }

    fun confirmTrimAll() = work {
        _ui.value = _ui.value.copy(askTrimAll = false)
        val ok = c.cacheCleaner.trimAll()
        _ui.value = _ui.value.copy(message = if (ok) tr("Кэш всех приложений очищен", "All apps' cache cleared") else tr("Не удалось очистить кэш", "Could not clear cache"))
    }

    fun dismissTrimAll() {
        _ui.value = _ui.value.copy(askTrimAll = false)
    }

    fun scan() = work {
        val items = c.junkCleaner.scan(c.settings.current().whitelist)
        val grouped = items.groupBy { it.category }
        _ui.value = _ui.value.copy(scanned = true, junk = grouped, selected = JunkSelection.initial(grouped))
    }

    fun toggleCategory(category: JunkCategory) {
        val u = _ui.value
        _ui.value = u.copy(selected = JunkSelection.toggleCategory(u.selected, category, u.junk))
    }

    fun toggleItem(path: String) {
        val u = _ui.value
        _ui.value = u.copy(selected = JunkSelection.toggleItem(u.selected, path))
    }

    fun askDelete() {
        if (_ui.value.selectedItems.isNotEmpty()) _ui.value = _ui.value.copy(confirmDelete = true)
    }

    fun dismissDelete() {
        _ui.value = _ui.value.copy(confirmDelete = false)
    }

    fun delete() = work {
        val items = _ui.value.selectedItems
        _ui.value = _ui.value.copy(confirmDelete = false)
        val removed = c.junkCleaner.delete(items)
        _ui.value = _ui.value.copy(message = tr("Удалено: $removed из ${items.size}", "Deleted: $removed of ${items.size}"))
        val rescanned = c.junkCleaner.scan(c.settings.current().whitelist).groupBy { it.category }
        _ui.value = _ui.value.copy(junk = rescanned, selected = JunkSelection.retain(_ui.value.selected, rescanned))
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun work(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            try {
                block()
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }
}
