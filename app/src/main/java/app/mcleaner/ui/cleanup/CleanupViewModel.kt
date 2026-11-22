package app.mcleaner.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.core.junk.JunkCategory
import app.mcleaner.core.junk.JunkItem
import app.mcleaner.core.policy.BoostPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CleanupUi(
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val junk: Map<JunkCategory, List<JunkItem>> = emptyMap(),
    val selected: Set<JunkCategory> = emptySet(),
    val askTrimAll: Boolean = false,
    val confirmDelete: Boolean = false,
    val message: String? = null,
) {
    val selectedItems: List<JunkItem> get() = selected.flatMap { junk[it].orEmpty() }
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
        val result = c.cacheCleaner.clearMany(BoostPolicy.cacheTargets(apps, whitelist), c.apps.currentUser())
        if (result.perPackageUnsupported) {
            _ui.value = _ui.value.copy(askTrimAll = true)
        } else {
            val freed = (before - c.apps.totalCacheBytes()).coerceAtLeast(0)
            _ui.value = _ui.value.copy(
                message = "Кэш очищен у ${result.cleared} приложений" +
                    if (freed > 0) ", освобождено ${app.mcleaner.ui.common.Formatters.bytes(freed)}" else "",
            )
        }
    }

    fun confirmTrimAll() = work {
        _ui.value = _ui.value.copy(askTrimAll = false)
        val ok = c.cacheCleaner.trimAll()
        _ui.value = _ui.value.copy(message = if (ok) "Кэш всех приложений очищен" else "Не удалось очистить кэш")
    }

    fun dismissTrimAll() {
        _ui.value = _ui.value.copy(askTrimAll = false)
    }

    fun scan() = work {
        val items = c.junkCleaner.scan()
        val grouped = items.groupBy { it.category }
        _ui.value = _ui.value.copy(scanned = true, junk = grouped, selected = grouped.keys - JunkCategory.APK)
    }

    fun toggle(category: JunkCategory) {
        val s = _ui.value.selected
        _ui.value = _ui.value.copy(selected = if (category in s) s - category else s + category)
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
        _ui.value = _ui.value.copy(message = "Удалено: $removed из ${items.size}")
        val rescanned = c.junkCleaner.scan().groupBy { it.category }
        _ui.value = _ui.value.copy(junk = rescanned, selected = _ui.value.selected intersect rescanned.keys)
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
