package app.mcleaner.data

import android.content.Context
import app.mcleaner.core.history.BoostHistory
import app.mcleaner.core.history.BoostRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Boost history in the app's private files: the last [BoostHistory.MAX_RECORDS] boosts. */
class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "boost_history.json")
    private val lock = Mutex()
    private var loaded = false

    private val _records = MutableStateFlow<List<BoostRecord>>(emptyList())
    val records: StateFlow<List<BoostRecord>> = _records.asStateFlow()

    suspend fun load(): List<BoostRecord> = lock.withLock { ensureLoaded() }

    suspend fun add(record: BoostRecord) = lock.withLock {
        val list = BoostHistory.append(ensureLoaded(), record)
        _records.value = list
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(BoostHistory.encode(list))
                tmp.renameTo(file)
            }
        }
    }

    suspend fun clear() = lock.withLock {
        _records.value = emptyList()
        loaded = true
        withContext(Dispatchers.IO) { file.delete() }
    }

    private suspend fun ensureLoaded(): List<BoostRecord> {
        if (!loaded) {
            _records.value = withContext(Dispatchers.IO) {
                if (file.isFile) BoostHistory.decode(runCatching { file.readText() }.getOrDefault("")) else emptyList()
            }
            loaded = true
        }
        return _records.value
    }
}
