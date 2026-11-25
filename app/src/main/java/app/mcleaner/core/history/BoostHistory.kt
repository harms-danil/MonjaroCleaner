package app.mcleaner.core.history

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Where a boost came from: shown in the history. */
enum class BoostSource { MANUAL, AUTO, SCHEDULE, SHORTCUT, LOW_MEMORY }

data class BoostRecord(
    val timeMs: Long,
    val source: BoostSource,
    val freedRamKb: Long,
    val freedCacheBytes: Long,
    /** Packages that were force-stopped. */
    val stopped: List<String>,
)

data class HistorySummary(val count: Int, val freedRamKb: Long, val freedCacheBytes: Long)

object BoostHistory {
    const val MAX_RECORDS = 200

    /**
     * Latest first, at most [max] records. Ordered by arrival, not by [BoostRecord.timeMs]:
     * the head unit clock is often wrong or resets, so a sort would bury new boosts or drop them on the cap.
     */
    fun append(list: List<BoostRecord>, record: BoostRecord, max: Int = MAX_RECORDS): List<BoostRecord> =
        (listOf(record) + list).take(max)

    /** Apps stopped most often, with how many times. */
    fun topStopped(list: List<BoostRecord>, limit: Int): List<Pair<String, Int>> =
        list.flatMap { it.stopped }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key to it.value }

    fun summary(list: List<BoostRecord>, sinceMs: Long): HistorySummary {
        val recent = list.filter { it.timeMs >= sinceMs }
        return HistorySummary(recent.size, recent.sumOf { it.freedRamKb }, recent.sumOf { it.freedCacheBytes })
    }

    fun encode(list: List<BoostRecord>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject().put("t", r.timeMs).put("src", r.source.name).put("ram", r.freedRamKb)
                    .put("cache", r.freedCacheBytes).put("stopped", JSONArray(r.stopped)),
            )
        }
    }.toString()

    fun decode(text: String): List<BoostRecord> = try {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val stopped = o.optJSONArray("stopped")
            BoostRecord(
                timeMs = o.optLong("t"),
                source = BoostSource.entries.firstOrNull { it.name == o.optString("src") } ?: BoostSource.MANUAL,
                freedRamKb = o.optLong("ram"),
                freedCacheBytes = o.optLong("cache"),
                stopped = (0 until (stopped?.length() ?: 0)).map { stopped!!.optString(it) }.filter { it.isNotBlank() },
            )
        }
    } catch (_: JSONException) {
        emptyList()
    }
}
