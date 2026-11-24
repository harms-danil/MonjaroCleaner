package app.mcleaner.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val whitelist: Set<String>,
    val autoBoost: Boolean,
    val stopGoogle: Boolean,
    val frozen: Set<String>,
    val lastAutoBoostMs: Long?,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setWhitelisted(pkg: String, on: Boolean) = context.dataStore.edit { p ->
        val cur = p[WHITELIST] ?: DEFAULT_WHITELIST
        p[WHITELIST] = if (on) cur + pkg else cur - pkg
    }

    suspend fun resetWhitelist() = context.dataStore.edit { it[WHITELIST] = DEFAULT_WHITELIST }

    suspend fun setAutoBoost(on: Boolean) = context.dataStore.edit { it[AUTO_BOOST] = on }

    suspend fun setStopGoogle(on: Boolean) = context.dataStore.edit { it[STOP_GOOGLE] = on }

    suspend fun addFrozen(pkg: String) = context.dataStore.edit { it[FROZEN] = (it[FROZEN] ?: emptySet()) + pkg }

    suspend fun removeFrozen(pkg: String) = context.dataStore.edit { it[FROZEN] = (it[FROZEN] ?: emptySet()) - pkg }

    suspend fun setLastAutoBoost(ms: Long) = context.dataStore.edit { it[LAST_AUTO] = ms }

    private fun Preferences.toSettings() = Settings(
        whitelist = this[WHITELIST] ?: DEFAULT_WHITELIST,
        autoBoost = this[AUTO_BOOST] ?: false,
        stopGoogle = this[STOP_GOOGLE] ?: false,
        frozen = this[FROZEN] ?: emptySet(),
        lastAutoBoostMs = this[LAST_AUTO],
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
        )
        private val WHITELIST = stringSetPreferencesKey("whitelist")
        private val AUTO_BOOST = booleanPreferencesKey("auto_boost")
        private val STOP_GOOGLE = booleanPreferencesKey("stop_google")
        private val FROZEN = stringSetPreferencesKey("frozen")
        private val LAST_AUTO = longPreferencesKey("last_auto_boost")
    }
}
