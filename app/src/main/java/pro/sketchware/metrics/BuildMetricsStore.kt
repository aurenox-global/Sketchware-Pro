package pro.sketchware.metrics

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.Locale
import kotlin.math.max

object BuildMetricsStore {
    private const val INCREMENTAL_WINDOW_MS = 10L * 60L * 1000L
    private const val PREF_NAME = "build_metrics"
    private const val KEY_TOTAL_COUNT = "total_count"
    private const val KEY_SUCCESS_COUNT = "success_count"
    private const val KEY_FAILURE_COUNT = "failure_count"
    private const val KEY_CANCELED_COUNT = "canceled_count"
    private const val KEY_COLD_COUNT = "cold_count"
    private const val KEY_INCREMENTAL_COUNT = "incremental_count"
    private const val KEY_CACHE_HIT_ESTIMATE_COUNT = "cache_hit_estimate_count"
    private const val KEY_CACHE_MISS_ESTIMATE_COUNT = "cache_miss_estimate_count"
    private const val KEY_TOTAL_SUCCESS_DURATION_MS = "total_success_duration_ms"
    private const val KEY_LAST_TYPE = "last_type"
    private const val KEY_LAST_PROFILE = "last_profile"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_CANCELED = "last_canceled"
    private const val KEY_LAST_TIMESTAMP_MS = "last_timestamp_ms"
    private const val KEY_LAST_STAGE_DURATIONS_JSON = "last_stage_durations_json"
    private const val KEY_LAST_TYPE_TIMESTAMP_PREFIX = "last_type_timestamp_"

    @JvmStatic
    fun recordBuild(context: Context?, buildType: String?, durationMs: Long, success: Boolean, canceled: Boolean) {
        recordBuild(context, buildType, durationMs, success, canceled, null)
    }

    @JvmStatic
    fun recordBuild(
        context: Context?,
        buildType: String?,
        durationMs: Long,
        success: Boolean,
        canceled: Boolean,
        stageDurations: List<StageDuration?>?
    ) {
        if (context == null || buildType == null) {
            return
        }

        val prefs = prefs(context)
        val safeDuration = max(durationMs, 0L)
        val now = System.currentTimeMillis()
        val incremental = isIncrementalBuild(prefs, buildType, now)
        val safeStageDurations: List<StageDuration?> = stageDurations ?: ArrayList()

        val totalCount = prefs.getInt(KEY_TOTAL_COUNT, 0) + 1
        var successCount = prefs.getInt(KEY_SUCCESS_COUNT, 0)
        var failureCount = prefs.getInt(KEY_FAILURE_COUNT, 0)
        var canceledCount = prefs.getInt(KEY_CANCELED_COUNT, 0)
        var coldCount = prefs.getInt(KEY_COLD_COUNT, 0)
        var incrementalCount = prefs.getInt(KEY_INCREMENTAL_COUNT, 0)
        var cacheHitEstimateCount = prefs.getInt(KEY_CACHE_HIT_ESTIMATE_COUNT, 0)
        var cacheMissEstimateCount = prefs.getInt(KEY_CACHE_MISS_ESTIMATE_COUNT, 0)
        var totalSuccessDuration = prefs.getLong(KEY_TOTAL_SUCCESS_DURATION_MS, 0L)
        val cacheHitEstimated = success && incremental

        if (incremental) {
            incrementalCount++
        } else {
            coldCount++
        }

        if (cacheHitEstimated) {
            cacheHitEstimateCount++
        } else {
            cacheMissEstimateCount++
        }

        if (success) {
            successCount++
            totalSuccessDuration += safeDuration
        } else if (canceled) {
            canceledCount++
        } else {
            failureCount++
        }

        prefs.edit()
            .putInt(KEY_TOTAL_COUNT, totalCount)
            .putInt(KEY_SUCCESS_COUNT, successCount)
            .putInt(KEY_FAILURE_COUNT, failureCount)
            .putInt(KEY_CANCELED_COUNT, canceledCount)
            .putInt(KEY_COLD_COUNT, coldCount)
            .putInt(KEY_INCREMENTAL_COUNT, incrementalCount)
            .putInt(KEY_CACHE_HIT_ESTIMATE_COUNT, cacheHitEstimateCount)
            .putInt(KEY_CACHE_MISS_ESTIMATE_COUNT, cacheMissEstimateCount)
            .putLong(KEY_TOTAL_SUCCESS_DURATION_MS, totalSuccessDuration)
            .putString(KEY_LAST_TYPE, buildType)
            .putString(KEY_LAST_PROFILE, if (incremental) "incremental" else "cold")
            .putLong(KEY_LAST_DURATION_MS, safeDuration)
            .putBoolean(KEY_LAST_SUCCESS, success)
            .putBoolean(KEY_LAST_CANCELED, canceled)
            .putLong(KEY_LAST_TIMESTAMP_MS, now)
            .putString(KEY_LAST_STAGE_DURATIONS_JSON, encodeStageDurations(safeStageDurations))
            .putLong(KEY_LAST_TYPE_TIMESTAMP_PREFIX + sanitizeKey(buildType), now)
            .apply()
    }

    @JvmStatic
    fun snapshot(context: Context?): Snapshot {
        if (context == null) {
            return Snapshot(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                "-",
                "-",
                0L,
                false,
                false,
                0L,
                ArrayList()
            )
        }

        val prefs = prefs(context)
        val successCount = prefs.getInt(KEY_SUCCESS_COUNT, 0)
        val totalSuccessDuration = prefs.getLong(KEY_TOTAL_SUCCESS_DURATION_MS, 0L)
        val averageSuccessDuration = if (successCount == 0) 0L else totalSuccessDuration / successCount

        return Snapshot(
            prefs.getInt(KEY_TOTAL_COUNT, 0),
            successCount,
            prefs.getInt(KEY_FAILURE_COUNT, 0),
            prefs.getInt(KEY_CANCELED_COUNT, 0),
            prefs.getInt(KEY_COLD_COUNT, 0),
            prefs.getInt(KEY_INCREMENTAL_COUNT, 0),
            prefs.getInt(KEY_CACHE_HIT_ESTIMATE_COUNT, 0),
            prefs.getInt(KEY_CACHE_MISS_ESTIMATE_COUNT, 0),
            averageSuccessDuration,
            prefs.getString(KEY_LAST_TYPE, "-"),
            prefs.getString(KEY_LAST_PROFILE, "-"),
            prefs.getLong(KEY_LAST_DURATION_MS, 0L),
            prefs.getBoolean(KEY_LAST_SUCCESS, false),
            prefs.getBoolean(KEY_LAST_CANCELED, false),
            prefs.getLong(KEY_LAST_TIMESTAMP_MS, 0L),
            decodeStageDurations(prefs.getString(KEY_LAST_STAGE_DURATIONS_JSON, ""))
        )
    }

    @JvmStatic
    fun clear(context: Context?) {
        if (context == null) {
            return
        }
        prefs(context).edit().clear().apply()
    }

    @JvmStatic
    fun formatDuration(millis: Long): String {
        if (millis < 1000) {
            return "$millis ms"
        }
        return String.format(Locale.US, "%.2f s", millis / 1000f)
    }

    @JvmStatic
    fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value)
    }

    @JvmStatic
    fun formatStageSummary(stages: List<StageDuration?>?, maxStages: Int): String {
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
            if (stage == null) {
                continue
            }
            if (count > 0) {
                builder.append(" | ")
            }
            builder.append(stage.label).append(": ").append(formatDuration(stage.durationMs))
            count++
        }

        if (stages.size > limit) {
            builder.append(" | +").append(stages.size - limit).append(" more")
        }

        return builder.toString()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun isIncrementalBuild(prefs: SharedPreferences, buildType: String, now: Long): Boolean {
        val previous = prefs.getLong(KEY_LAST_TYPE_TIMESTAMP_PREFIX + sanitizeKey(buildType), 0L)
        return previous > 0 && (now - previous) <= INCREMENTAL_WINDOW_MS
    }

    private fun sanitizeKey(raw: String): String {
        return raw.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    private fun encodeStageDurations(stages: List<StageDuration?>): String {
        val array = JSONArray()
        for (stage in stages) {
            if (stage == null) {
                continue
            }
            val objectJson = JSONObject()
            try {
                objectJson.put("label", stage.label)
                objectJson.put("durationMs", stage.durationMs)
                array.put(objectJson)
            } catch (_: JSONException) {
            }
        }
        return array.toString()
    }

    private fun decodeStageDurations(raw: String?): List<StageDuration> {
        val stages = ArrayList<StageDuration>()
        if (raw.isNullOrEmpty()) {
            return stages
        }

        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val objectJson = array.optJSONObject(index) ?: continue
                val label = objectJson.optString("label", "Stage ${index + 1}")
                val durationMs = objectJson.optLong("durationMs", 0L)
                stages.add(StageDuration(label, durationMs))
            }
        } catch (_: JSONException) {
            return ArrayList()
        }

        return stages
    }

    class StageDuration(label: String?, durationMs: Long) {
        @JvmField
        val label: String = label ?: ""

        @JvmField
        val durationMs: Long = max(durationMs, 0L)
    }

    class Snapshot(
        @JvmField val totalCount: Int,
        @JvmField val successCount: Int,
        @JvmField val failureCount: Int,
        @JvmField val canceledCount: Int,
        @JvmField val coldCount: Int,
        @JvmField val incrementalCount: Int,
        @JvmField val cacheHitEstimateCount: Int,
        @JvmField val cacheMissEstimateCount: Int,
        averageSuccessDurationMs: Long,
        lastType: String?,
        lastProfile: String?,
        lastDurationMs: Long,
        @JvmField val lastSuccess: Boolean,
        @JvmField val lastCanceled: Boolean,
        lastTimestampMs: Long,
        lastStageDurations: List<StageDuration?>?
    ) {
        @JvmField
        val cacheHitEstimateRate: Double

        @JvmField
        val averageSuccessDurationMs: Long = averageSuccessDurationMs

        @JvmField
        val lastType: String = lastType ?: "-"

        @JvmField
        val lastProfile: String = lastProfile ?: "-"

        @JvmField
        val lastDurationMs: Long = lastDurationMs

        @JvmField
        val lastTimestampMs: Long = lastTimestampMs

        @JvmField
        val lastStageDurations: List<StageDuration?> = lastStageDurations ?: ArrayList()

        init {
            val totalCacheDecisions = cacheHitEstimateCount + cacheMissEstimateCount
            cacheHitEstimateRate = if (totalCacheDecisions == 0) {
                0.0
            } else {
                (cacheHitEstimateCount * 100.0) / totalCacheDecisions
            }
        }
    }
}
