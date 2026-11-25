package app.mcleaner.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mcleaner.core.update.BackupData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val whitelist: Set<String>,
    val autoBoost: Boolean,
    val stopGoogle: Boolean,
    val frozen: Set<String>,
    val lastAutoBoostMs: Long?,
    /** Boost every N minutes while the head unit is on; 0 = off. */
    val scheduleMinutes: Int = 0,
    val lowMemoryAlert: Boolean = false,
    val lowMemoryPercent: Int = 15,
) {
    fun toBackup() = BackupData(
        whitelist = whitelist, frozen = frozen, autoBoost = autoBoost, stopGoogle = stopGoogle,
        scheduleMinutes = scheduleMinutes, lowMemoryAlert = lowMemoryAlert, lowMemoryPercent = lowMemoryPercent,
    )
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    /**
     * A change made by the user. Also marks the install initialized: a backup restored later (once storage
     * access appears) must not overwrite what the user has already chosen on this install.
     */
    private suspend fun userEdit(block: (MutablePreferences) -> Unit) = context.dataStore.edit { p ->
        block(p)
        p[INITIALIZED] = true
    }

    suspend fun setWhitelisted(pkg: String, on: Boolean) = userEdit { p ->
        val cur = p[WHITELIST] ?: DEFAULT_WHITELIST
        p[WHITELIST] = if (on) cur + pkg else cur - pkg
    }

    suspend fun resetWhitelist() = userEdit { it[WHITELIST] = DEFAULT_WHITELIST }

    suspend fun setAutoBoost(on: Boolean) = userEdit { it[AUTO_BOOST] = on }

    suspend fun setStopGoogle(on: Boolean) = userEdit { it[STOP_GOOGLE] = on }

    suspend fun setScheduleMinutes(minutes: Int) = userEdit { it[SCHEDULE_MIN] = minutes }

    suspend fun setLowMemoryAlert(on: Boolean) = userEdit { it[LOW_MEM] = on }

    suspend fun setLowMemoryPercent(percent: Int) = userEdit { it[LOW_MEM_PCT] = percent }

    /**
     * Import from a file (another car or an older export). The frozen list is not applied:
     * it describes which apps are disabled on that particular head unit.
     */
    suspend fun applyImport(d: BackupData) = context.dataStore.edit { p ->
        p[WHITELIST] = d.whitelist
        p[AUTO_BOOST] = d.autoBoost
        p[STOP_GOOGLE] = d.stopGoogle
        p[SCHEDULE_MIN] = d.scheduleMinutes
        p[LOW_MEM] = d.lowMemoryAlert
        p[LOW_MEM_PCT] = d.lowMemoryPercent
        p[INITIALIZED] = true
    }

    suspend fun addFrozen(pkg: String) = userEdit { it[FROZEN] = (it[FROZEN] ?: emptySet()) + pkg }

    suspend fun removeFrozen(pkg: String) = userEdit { it[FROZEN] = (it[FROZEN] ?: emptySet()) - pkg }

    /** Whether this install already took its settings (from the backup file or as defaults). */
    suspend fun isInitialized(): Boolean = context.dataStore.data.first()[INITIALIZED] == true

    /**
     * First run of this install: take settings from the backup (if any) and mark the install initialized.
     * The frozen list is not restored: settings.json is on shared storage (writable by other apps on Android 9),
     * so which apps are disabled must come only from this install's own actions.
     */
    suspend fun initialize(backup: BackupData?) = context.dataStore.edit { p ->
        if (p[INITIALIZED] == true) return@edit
        if (backup != null) {
            p[WHITELIST] = backup.whitelist
            p[AUTO_BOOST] = backup.autoBoost
            p[STOP_GOOGLE] = backup.stopGoogle
            p[SCHEDULE_MIN] = backup.scheduleMinutes
            p[LOW_MEM] = backup.lowMemoryAlert
            p[LOW_MEM_PCT] = backup.lowMemoryPercent
        }
        p[INITIALIZED] = true
    }

    suspend fun setLastAutoBoost(ms: Long) = context.dataStore.edit { it[LAST_AUTO] = ms }

    private fun Preferences.toSettings() = Settings(
        whitelist = this[WHITELIST] ?: DEFAULT_WHITELIST,
        autoBoost = this[AUTO_BOOST] ?: false,
        stopGoogle = this[STOP_GOOGLE] ?: false,
        frozen = this[FROZEN] ?: emptySet(),
        lastAutoBoostMs = this[LAST_AUTO],
        scheduleMinutes = this[SCHEDULE_MIN] ?: 0,
        lowMemoryAlert = this[LOW_MEM] ?: false,
        lowMemoryPercent = this[LOW_MEM_PCT] ?: 15,
    )

    companion object {
        val DEFAULT_WHITELIST = setOf(
            "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps",
            "com.google.android.apps.maps",
            "ru.dublgis.dgismobile",
            "ru.yandex.music",
            "com.spotify.music",
            "com.uma.musicvk",
            "moe.shizuku.privileged.api",
            "com.shilapi.xcertplay",
            "com.shihab.diplay", // DiPlay geely-fixes build
        )
        private val WHITELIST = stringSetPreferencesKey("whitelist")
        private val AUTO_BOOST = booleanPreferencesKey("auto_boost")
        private val STOP_GOOGLE = booleanPreferencesKey("stop_google")
        private val FROZEN = stringSetPreferencesKey("frozen")
        private val LAST_AUTO = longPreferencesKey("last_auto_boost")
        private val INITIALIZED = booleanPreferencesKey("initialized")
        private val SCHEDULE_MIN = intPreferencesKey("schedule_minutes")
        private val LOW_MEM = booleanPreferencesKey("low_memory_alert")
        private val LOW_MEM_PCT = intPreferencesKey("low_memory_percent")
    }
}
