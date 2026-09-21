package pro.sketchware.metrics

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import java.util.ArrayList
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object EditorPerformanceMetricsStore {
    @JvmField
    val OP_COMPLETION: String = "completion"

    @JvmField
    val OP_DEFINITION: String = "definition"

    @JvmField
    val OP_REFERENCES: String = "references"

    @JvmField
    val OP_DIAGNOSTICS_ANALYZE: String = "diagnostics_analyze"

    @JvmField
    val OP_DIAGNOSTICS_RENDER: String = "diagnostics_render"

    @JvmField
    val OP_DIAGNOSTICS_PUBLISH_TO_RENDER: String = "diagnostics_publish_to_render"

    private const val PREF_NAME = "editor_performance_metrics"
    private const val KEY_SAMPLES_PREFIX = "samples_"
    private const val MAX_SAMPLES_PER_OPERATION = 240
    private val LOCK = Any()

    @JvmStatic
    fun recordSample(context: Context?, operation: String?, durationMs: Long) {
        if (context == null || operation.isNullOrEmpty()) {
            return
        }

        val safeDuration = max(durationMs, 0L)
        synchronized(LOCK) {
            val prefs = prefs(context)
            val key = keyForOperation(operation)
            val samples = decodeSamples(prefs.getString(key, ""))
            samples.add(safeDuration)
            trimToWindow(samples, MAX_SAMPLES_PER_OPERATION)
            prefs.edit().putString(key, encodeSamples(samples)).apply()
        }
    }

    @JvmStatic
    fun snapshot(context: Context?): Snapshot {
        if (context == null) {
            return Snapshot.empty()
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val completion = toStats(decodeSamples(prefs.getString(keyForOperation(OP_COMPLETION), "")))
            val definition = toStats(decodeSamples(prefs.getString(keyForOperation(OP_DEFINITION), "")))
            val references = toStats(decodeSamples(prefs.getString(keyForOperation(OP_REFERENCES), "")))
            val diagnosticsAnalyze = toStats(
                decodeSamples(prefs.getString(keyForOperation(OP_DIAGNOSTICS_ANALYZE), ""))
            )
            val diagnosticsRender = toStats(
                decodeSamples(prefs.getString(keyForOperation(OP_DIAGNOSTICS_RENDER), ""))
            )
            val diagnosticsPublishToRender = toStats(
                decodeSamples(prefs.getString(keyForOperation(OP_DIAGNOSTICS_PUBLISH_TO_RENDER), ""))
            )

            val totalSamples = completion.count +
                definition.count +
                references.count +
                diagnosticsAnalyze.count +
                diagnosticsRender.count +
                diagnosticsPublishToRender.count

            return Snapshot(
                totalSamples,
                completion,
                definition,
                references,
                diagnosticsAnalyze,
                diagnosticsRender,
                diagnosticsPublishToRender
            )
        }
    }

    @JvmStatic
    fun clear(context: Context?) {
        if (context == null) {
            return
        }

        synchronized(LOCK) {
            val editor = prefs(context).edit()
            editor.remove(keyForOperation(OP_COMPLETION))
            editor.remove(keyForOperation(OP_DEFINITION))
            editor.remove(keyForOperation(OP_REFERENCES))
            editor.remove(keyForOperation(OP_DIAGNOSTICS_ANALYZE))
            editor.remove(keyForOperation(OP_DIAGNOSTICS_RENDER))
            editor.remove(keyForOperation(OP_DIAGNOSTICS_PUBLISH_TO_RENDER))
            editor.apply()
        }
    }

    @JvmStatic
    fun formatDuration(millis: Long): String {
        if (millis < 1000L) {
            return "$millis ms"
        }
        return String.format(Locale.US, "%.2f s", millis / 1000f)
    }

    @JvmStatic
    fun formatOperation(label: String?, stats: OperationStats?): String {
        val safeLabel = label ?: ""
        if (stats == null || stats.count == 0) {
            return "$safeLabel: no data"
        }

        return safeLabel +
            ": n=" + stats.count +
            " | p50 " + formatDuration(stats.p50Ms) +
            " | p95 " + formatDuration(stats.p95Ms) +
            " | avg " + formatDuration(stats.averageMs) +
            " | last " + formatDuration(stats.lastMs)
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun keyForOperation(operation: String): String {
        return KEY_SAMPLES_PREFIX + sanitizeKey(operation)
    }

    private fun sanitizeKey(raw: String): String {
        return raw.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    private fun trimToWindow(samples: MutableList<Long>, maxSize: Int) {
        if (samples.size <= maxSize) {
            return
        }
        val overflow = samples.size - maxSize
        samples.subList(0, overflow).clear()
    }

    private fun encodeSamples(samples: List<Long>): String {
        val array = JSONArray()
        for (sample in samples) {
            array.put(max(sample, 0L))
        }
        return array.toString()
    }

    private fun decodeSamples(raw: String?): MutableList<Long> {
        val values = ArrayList<Long>()
        if (raw.isNullOrEmpty()) {
            return values
        }

        return try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                values.add(max(array.optLong(index, 0L), 0L))
            }
            values
        } catch (_: JSONException) {
            ArrayList()
        }
    }

    private fun toStats(samples: List<Long>?): OperationStats {
        if (samples.isNullOrEmpty()) {
            return OperationStats.empty()
        }

        val sorted = samples.toMutableList()
        sorted.sort()

        var total = 0L
        var minValue = Long.MAX_VALUE
        var maxValue = 0L
        for (value in samples) {
            val safe = max(value, 0L)
            total += safe
            minValue = min(minValue, safe)
            maxValue = max(maxValue, safe)
        }

        val count = samples.size
        val average = if (count == 0) 0L else total / count
        val p50 = percentile(sorted, 50)
        val p95 = percentile(sorted, 95)
        val last = max(samples[samples.size - 1], 0L)

        return OperationStats(
            count,
            if (minValue == Long.MAX_VALUE) 0L else minValue,
            maxValue,
            average,
            p50,
            p95,
            last
        )
    }

    private fun percentile(sortedSamples: List<Long>?, percentile: Int): Long {
        if (sortedSamples.isNullOrEmpty()) {
            return 0L
        }

        val clampedPercentile = max(0, min(percentile, 100))
        var index = ceil((clampedPercentile / 100.0) * sortedSamples.size).toInt() - 1
        index = max(0, min(index, sortedSamples.size - 1))
        return max(sortedSamples[index], 0L)
    }

    class OperationStats(
        count: Int,
        minMs: Long,
        maxMs: Long,
        averageMs: Long,
        p50Ms: Long,
        p95Ms: Long,
        lastMs: Long
    ) {
        @JvmField
        val count: Int = max(count, 0)

        @JvmField
        val minMs: Long = max(minMs, 0L)

        @JvmField
        val maxMs: Long = max(maxMs, 0L)

        @JvmField
        val averageMs: Long = max(averageMs, 0L)

        @JvmField
        val p50Ms: Long = max(p50Ms, 0L)

        @JvmField
        val p95Ms: Long = max(p95Ms, 0L)

        @JvmField
        val lastMs: Long = max(lastMs, 0L)

        companion object {
            @JvmStatic
            fun empty(): OperationStats {
                return OperationStats(0, 0L, 0L, 0L, 0L, 0L, 0L)
            }
        }
    }

    class Snapshot(
        totalSamples: Int,
        completion: OperationStats?,
        definition: OperationStats?,
        references: OperationStats?,
        diagnosticsAnalyze: OperationStats?,
        diagnosticsRender: OperationStats?,
        diagnosticsPublishToRender: OperationStats?
    ) {
        @JvmField
        val totalSamples: Int = max(totalSamples, 0)

        @JvmField
        val completion: OperationStats = completion ?: OperationStats.empty()

        @JvmField
        val definition: OperationStats = definition ?: OperationStats.empty()

        @JvmField
        val references: OperationStats = references ?: OperationStats.empty()

        @JvmField
        val diagnosticsAnalyze: OperationStats = diagnosticsAnalyze ?: OperationStats.empty()

        @JvmField
        val diagnosticsRender: OperationStats = diagnosticsRender ?: OperationStats.empty()

        @JvmField
        val diagnosticsPublishToRender: OperationStats =
            diagnosticsPublishToRender ?: OperationStats.empty()

        companion object {
            @JvmStatic
            fun empty(): Snapshot {
                return Snapshot(
                    0,
                    OperationStats.empty(),
                    OperationStats.empty(),
                    OperationStats.empty(),
                    OperationStats.empty(),
                    OperationStats.empty(),
                    OperationStats.empty()
                )
            }
        }
    }
}
