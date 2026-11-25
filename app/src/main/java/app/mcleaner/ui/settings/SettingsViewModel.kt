package app.mcleaner.ui.settings

import app.mcleaner.core.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mcleaner.AppContainer
import app.mcleaner.BuildConfig
import app.mcleaner.core.policy.InstalledApp
import app.mcleaner.core.policy.WhitelistEntry
import app.mcleaner.core.policy.WhitelistModel
import app.mcleaner.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val shizuku = c.shizuku.state
    val version: String = BuildConfig.VERSION_NAME

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Hook for Task 9: starts/stops the wake watcher. */
    var onAutoBoostChanged: (Boolean) -> Unit = {}

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val selfPackage: String = c.appContext.packageName

    val whitelistEntries: StateFlow<List<WhitelistEntry>> =
        combine(c.settings.settings, installed) { s, apps -> WhitelistModel.entries(s.whitelist, apps) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadInstalled()
    }

    fun loadInstalled() {
        viewModelScope.launch { installed.value = c.apps.installedApps() }
    }

    fun candidates(query: String): List<InstalledApp> =
        WhitelistModel.candidates(settings.value?.whitelist.orEmpty(), installed.value, query, selfPackage)

    fun setWhitelisted(pkg: String, on: Boolean) {
        viewModelScope.launch { c.settings.setWhitelisted(pkg, on) }
    }

    fun resetWhitelist() {
        viewModelScope.launch {
            c.settings.resetWhitelist()
            _message.value = tr("Белый список сброшен к стандартному", "Whitelist reset to default")
        }
    }

    fun refreshShizuku() = c.shizuku.refresh()

    fun requestPermission() = c.shizuku.requestPermission()

    fun setAutoBoost(on: Boolean) {
        viewModelScope.launch {
            c.settings.setAutoBoost(on)
            onAutoBoostChanged(on)
        }
    }

    fun setStopGoogle(on: Boolean) {
        viewModelScope.launch { c.settings.setStopGoogle(on) }
    }

    fun unfreezeAll() {
        viewModelScope.launch {
            val frozen = c.settings.current().frozen
            val n = c.freezeManager.unfreezeAll(frozen)
            _message.value = tr("Разморожено: $n из ${frozen.size}", "Unfrozen: $n of ${frozen.size}")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
