package pro.sketchware.metrics

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pro.sketchware.kmp.KmpBuildOrchestrationReport
import pro.sketchware.kmp.KmpTargetBuildResult
import java.util.ArrayList
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object KmpBuildPerformanceMetricsStore {
    private const val PREF_NAME = "kmp_build_performance_metrics"
    private const val INCREMENTAL_WINDOW_MS = 10L * 60L * 1000L
    private const val MAX_SAMPLES_PER_SERIES = 240

    private const val KEY_TOTAL_RUNS = "total_runs"
    private const val KEY_COLD_RUNS = "cold_runs"
    private const val KEY_INCREMENTAL_RUNS = "incremental_runs"
    private const val KEY_LAST_TIMESTAMP_MS = "last_timestamp_ms"
    private const val KEY_LAST_PROFILE = "last_profile"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"
    private const val KEY_TARGET_IDS = "target_ids"
    private const val KEY_TOTAL_DURATION_SAMPLES = "samples_total_duration"
    private const val KEY_TARGET_DURATION_PREFIX = "samples_target_duration_"
    private const val KEY_LAST_STAGES_JSON = "last_stages_json"

    private val LOCK = Any()

    @JvmStatic
    fun recordReport(context: Context?, report: KmpBuildOrchestrationReport?) {
        if (context == null || report == null) {
            return
        }

        val safeDurationMs = max(report.finishedAtMs - report.startedAtMs, 0L)
        val now = System.currentTimeMillis()

        synchronized(LOCK) {
            val prefs = prefs(context)
            val previousRunTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP_MS, 0L)
            val incremental = previousRunTimestamp > 0 && (now - previousRunTimestamp) <= INCREMENTAL_WINDOW_MS

            val totalRuns = prefs.getInt(KEY_TOTAL_RUNS, 0) + 1
            val coldRuns = prefs.getInt(KEY_COLD_RUNS, 0) + if (incremental) 0 else 1
            val incrementalRuns = prefs.getInt(KEY_INCREMENTAL_RUNS, 0) + if (incremental) 1 else 0

            val totalSamples = decodeSamples(prefs.getString(KEY_TOTAL_DURATION_SAMPLES, ""))
            totalSamples.add(safeDurationMs)
            trimToWindow(totalSamples, MAX_SAMPLES_PER_SERIES)

            val knownTargets = decodeTargetIds(prefs.getString(KEY_TARGET_IDS, ""))
            for (result in report.results) {
                val targetId = result.target.name
                knownTargets.add(targetId)

                val targetSamples = decodeSamples(
                    prefs.getString(KEY_TARGET_DURATION_PREFIX + sanitizeKey(targetId), "")
                )
                targetSamples.add(max(result.durationMs, 0L))
                trimToWindow(targetSamples, MAX_SAMPLES_PER_SERIES)

                prefs.edit()
                    .putString(KEY_TARGET_DURATION_PREFIX + sanitizeKey(targetId), encodeSamples(targetSamples))
                    .apply()
            }

            prefs.edit()
                .putInt(KEY_TOTAL_RUNS, totalRuns)
                .putInt(KEY_COLD_RUNS, coldRuns)
                .putInt(KEY_INCREMENTAL_RUNS, incrementalRuns)
                .putLong(KEY_LAST_TIMESTAMP_MS, now)
                .putString(KEY_LAST_PROFILE, if (incremental) "incremental" else "cold")
                .putLong(KEY_LAST_DURATION_MS, safeDurationMs)
                .putString(KEY_TOTAL_DURATION_SAMPLES, encodeSamples(totalSamples))
                .putString(KEY_TARGET_IDS, encodeTargetIds(knownTargets))
                .putString(KEY_LAST_STAGES_JSON, encodeStages(report.results))
                .apply()
        }
    }

    @JvmStatic
    fun snapshot(context: Context?): Snapshot {
        if (context == null) {
            return Snapshot.empty()
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val targetTrends = ArrayList<TargetDurationTrend>()
            for (targetId in decodeTargetIds(prefs.getString(KEY_TARGET_IDS, ""))) {
                val targetSamples = decodeSamples(
                    prefs.getString(KEY_TARGET_DURATION_PREFIX + sanitizeKey(targetId), "")
                )
                targetTrends.add(TargetDurationTrend(targetId, toStats(targetSamples)))
            }

            targetTrends.sortBy { it.targetId }

            return Snapshot(
                prefs.getInt(KEY_TOTAL_RUNS, 0),
                prefs.getInt(KEY_COLD_RUNS, 0),
                prefs.getInt(KEY_INCREMENTAL_RUNS, 0),
                prefs.getString(KEY_LAST_PROFILE, "-") ?: "-",
                prefs.getLong(KEY_LAST_DURATION_MS, 0L),
                prefs.getLong(KEY_LAST_TIMESTAMP_MS, 0L),
                toStats(decodeSamples(prefs.getString(KEY_TOTAL_DURATION_SAMPLES, ""))),
                targetTrends,
                decodeStages(prefs.getString(KEY_LAST_STAGES_JSON, ""))
            )
        }
    }

    @JvmStatic
    fun clear(context: Context?) {
        if (context == null) {
            return
        }

        synchronized(LOCK) {
            prefs(context).edit().clear().apply()
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

    @JvmStatic
    fun formatTargetTrend(trends: List<TargetDurationTrend>?, maxTargets: Int): String {
        if (trends.isNullOrEmpty()) {
            return "No target trend data yet."
        }

        val builder = StringBuilder()
        val limit = max(maxTargets, 1)
        var count = 0
        for (trend in trends) {
            if (count >= limit) {
                break
            }
            if (count > 0) {
                builder.append('\n')
            }
            builder.append(formatOperation(trend.targetId, trend.stats))
            count++
        }

        if (trends.size > limit) {
            builder.append("\n+").append(trends.size - limit).append(" more targets")
        }

        return builder.toString()
    }

    @JvmStatic
    fun formatStageSummary(stages: List<TargetStageDuration>?, maxStages: Int): String {
        if (stages.isNullOrEmpty()) {
            return "No stage data yet."
        }

        val builder = StringBuilder()
        val limit = max(maxStages, 1)
        var count = 0
        for (stage in stages) {
            if (count >= limit) {
                break
            }
            if (count > 0) {
                builder.append(" | ")
            }
            builder.append(stage.targetId)
                .append("(")
                .append(stage.status)
                .append("): ")
                .append(formatDuration(stage.durationMs))
            count++
        }

        if (stages.size > limit) {
            builder.append(" | +").append(stages.size - limit).append(" more")
        }

        return builder.toString()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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

    private fun encodeTargetIds(targetIds: Collection<String>): String {
        if (targetIds.isEmpty()) {
            return ""
        }

        return targetIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString(",")
    }

    private fun decodeTargetIds(raw: String?): MutableList<String> {
        if (raw.isNullOrEmpty()) {
            return ArrayList()
        }

        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()
    }

    private fun encodeStages(results: List<KmpTargetBuildResult>): String {
        val array = JSONArray()
        for (result in results) {
            val objectNode = JSONObject()
            try {
                objectNode.put("targetId", result.target.name)
                objectNode.put("status", result.status.name)
                objectNode.put("durationMs", max(result.durationMs, 0L))
                objectNode.put("artifactPath", result.artifactPath ?: "")
                objectNode.put("message", result.message)
                array.put(objectNode)
            } catch (_: JSONException) {
            }
        }
        return array.toString()
    }

    private fun decodeStages(raw: String?): List<TargetStageDuration> {
        if (raw.isNullOrEmpty()) {
            return emptyList()
        }

        return try {
            val array = JSONArray(raw)
            val stages = ArrayList<TargetStageDuration>()
            for (index in 0 until array.length()) {
                val objectNode = array.optJSONObject(index) ?: continue
                stages.add(
                    TargetStageDuration(
                        objectNode.optString("targetId", "unknown"),
                        objectNode.optString("status", "UNKNOWN"),
                        max(objectNode.optLong("durationMs", 0L), 0L),
                        objectNode.optString("artifactPath", ""),
                        objectNode.optString("message", "")
                    )
                )
            }
            stages
        } catch (_: JSONException) {
            emptyList()
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

    class TargetDurationTrend(
        targetId: String,
        stats: OperationStats?
    ) {
        @JvmField
        val targetId: String = targetId.trim().ifEmpty { "unknown" }

        @JvmField
        val stats: OperationStats = stats ?: OperationStats.empty()
    }

    class TargetStageDuration(
        targetId: String,
        status: String,
        durationMs: Long,
        artifactPath: String,
        message: String
    ) {
        @JvmField
        val targetId: String = targetId.trim().ifEmpty { "unknown" }

        @JvmField
        val status: String = status.trim().ifEmpty { "UNKNOWN" }

        @JvmField
        val durationMs: Long = max(durationMs, 0L)

        @JvmField
        val artifactPath: String = artifactPath

        @JvmField
        val message: String = message
    }

    class Snapshot(
        totalRuns: Int,
        coldRuns: Int,
        incrementalRuns: Int,
        lastProfile: String,
        lastDurationMs: Long,
        lastTimestampMs: Long,
        totalDurationTrend: OperationStats?,
        targetDurationTrends: List<TargetDurationTrend>?,
        lastTargetStages: List<TargetStageDuration>?
    ) {
        @JvmField
        val totalRuns: Int = max(totalRuns, 0)

        @JvmField
        val coldRuns: Int = max(coldRuns, 0)

        @JvmField
        val incrementalRuns: Int = max(incrementalRuns, 0)

        @JvmField
        val lastProfile: String = lastProfile.trim().ifEmpty { "-" }

        @JvmField
        val lastDurationMs: Long = max(lastDurationMs, 0L)

        @JvmField
        val lastTimestampMs: Long = max(lastTimestampMs, 0L)

        @JvmField
        val totalDurationTrend: OperationStats = totalDurationTrend ?: OperationStats.empty()

        @JvmField
        val targetDurationTrends: List<TargetDurationTrend> = targetDurationTrends ?: emptyList()

        @JvmField
        val lastTargetStages: List<TargetStageDuration> = lastTargetStages ?: emptyList()

        companion object {
            @JvmStatic
            fun empty(): Snapshot {
                return Snapshot(
                    0,
                    0,
                    0,
                    "-",
                    0L,
                    0L,
                    OperationStats.empty(),
                    emptyList(),
                    emptyList()
                )
            }
        }
    }
}