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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import app.mcleaner.core.update.BackupData
import app.mcleaner.core.update.SettingsBackup

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val shizuku = c.shizuku.state
    val version: String = BuildConfig.VERSION_NAME

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()


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
            c.syncBackgroundWork()
        }
    }

    fun setScheduleMinutes(minutes: Int) {
        viewModelScope.launch {
            c.settings.setScheduleMinutes(minutes)
            c.syncBackgroundWork()
        }
    }

    fun setLowMemoryAlert(on: Boolean) {
        viewModelScope.launch {
            c.settings.setLowMemoryAlert(on)
            c.syncBackgroundWork()
        }
    }

    fun setLowMemoryPercent(percent: Int) {
        viewModelScope.launch { c.settings.setLowMemoryPercent(percent) }
    }

    // --- export / import (a flash drive via the system file picker) ---------------------------

    /** Parsed file waiting for «Импортировать?» confirmation. */
    private val _pendingImport = MutableStateFlow<BackupData?>(null)
    val pendingImport: StateFlow<BackupData?> = _pendingImport.asStateFlow()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val text = SettingsBackup.encode(c.settings.current().toBackup())
            val ok = withContext(Dispatchers.IO) {
                runCatching { c.appContext.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess
            }
            _message.value = if (ok) tr("Настройки сохранены в файл", "Settings saved to file") else tr("Не удалось записать файл", "Could not write the file")
        }
    }

    /** No system file picker on the head unit: save into /sdcard/MCleaner/export instead. */
    fun exportToFolder() {
        viewModelScope.launch {
            val text = SettingsBackup.encode(c.settings.current().toBackup())
            val path = withContext(Dispatchers.IO) { c.folder.writeExport(text) }
            _message.value = path?.let { tr("Сохранено: $it", "Saved: $it") } ?: tr("Нет доступа к памяти: включите полный режим", "No storage access: enable full mode")
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    c.appContext.contentResolver.openInputStream(uri)!!.use { input ->
                        // A settings file is a few KB; refuse anything over 1 MB instead of reading it whole.
                        // (InputStream.readNBytes is API 33+, the head unit runs Android 9.)
                        val maxBytes = 1 shl 20
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8 * 1024)
                        while (out.size() <= maxBytes) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        if (out.size() > maxBytes) null else out.toByteArray().decodeToString()
                    }
                }.getOrNull()
            }
            val data = text?.let(SettingsBackup::decode)
            if (data == null) _message.value = tr("Это не файл настроек MCleaner", "This is not an MCleaner settings file")
            else _pendingImport.value = data
        }
    }

    fun confirmImport() {
        val data = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            c.settings.applyImport(data)
            c.syncBackgroundWork()
            _message.value = tr("Настройки импортированы", "Settings imported")
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun reportNoPicker() {
        _message.value = tr(
            "На магнитоле нет окна выбора файла. Скопируйте файл в /sdcard/MCleaner/settings.json и переустановите MCleaner или используйте ноутбук.",
            "The head unit has no file picker. Copy the file to /sdcard/MCleaner/settings.json and reinstall MCleaner, or use a laptop.",
        )
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
