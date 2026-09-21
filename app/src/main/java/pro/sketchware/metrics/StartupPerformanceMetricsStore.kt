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

object StartupPerformanceMetricsStore {
    private const val PREF_NAME = "startup_performance_metrics"
    private const val KEY_TOTAL_COUNT = "total_count"
    private const val KEY_REGRESSION_COUNT = "regression_count"
    private const val KEY_LAST_TOTAL_DURATION_MS = "last_total_duration_ms"
    private const val KEY_LAST_APPLICATION_DURATION_MS = "last_application_duration_ms"
    private const val KEY_LAST_MAIN_ACTIVITY_DURATION_MS = "last_main_activity_duration_ms"
    private const val KEY_LAST_REGRESSION = "last_regression"
    private const val KEY_LAST_THRESHOLD_MS = "last_threshold_ms"
    private const val KEY_LAST_BASELINE_P50_MS = "last_baseline_p50_ms"
    private const val KEY_SAMPLES_TOTAL = "samples_total"
    private const val KEY_SAMPLES_APPLICATION = "samples_application"
    private const val KEY_SAMPLES_MAIN_ACTIVITY = "samples_main_activity"
    private const val MAX_SAMPLES_PER_STREAM = 240

    private val LOCK = Any()

    @JvmStatic
    fun recordStartup(
        context: Context?,
        totalDurationMs: Long,
        applicationInitDurationMs: Long,
        mainActivityReadyDurationMs: Long
    ) {
        if (context == null) {
            return
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val safeTotal = max(totalDurationMs, 0L)
            val safeApplication = max(applicationInitDurationMs, 0L)
            val safeMainActivity = max(mainActivityReadyDurationMs, 0L)

            val totalSamples = decodeSamples(prefs.getString(KEY_SAMPLES_TOTAL, ""))
            val decision = StartupRegressionPolicy.evaluate(totalSamples, safeTotal)

            val applicationSamples = decodeSamples(prefs.getString(KEY_SAMPLES_APPLICATION, ""))
            val mainActivitySamples = decodeSamples(prefs.getString(KEY_SAMPLES_MAIN_ACTIVITY, ""))

            totalSamples.add(safeTotal)
            applicationSamples.add(safeApplication)
            mainActivitySamples.add(safeMainActivity)

            trimToWindow(totalSamples, MAX_SAMPLES_PER_STREAM)
            trimToWindow(applicationSamples, MAX_SAMPLES_PER_STREAM)
            trimToWindow(mainActivitySamples, MAX_SAMPLES_PER_STREAM)

            val totalCount = prefs.getInt(KEY_TOTAL_COUNT, 0) + 1
            val regressionCount = prefs.getInt(KEY_REGRESSION_COUNT, 0) + if (decision.regression) 1 else 0

            prefs.edit()
                .putInt(KEY_TOTAL_COUNT, totalCount)
                .putInt(KEY_REGRESSION_COUNT, regressionCount)
                .putLong(KEY_LAST_TOTAL_DURATION_MS, safeTotal)
                .putLong(KEY_LAST_APPLICATION_DURATION_MS, safeApplication)
                .putLong(KEY_LAST_MAIN_ACTIVITY_DURATION_MS, safeMainActivity)
                .putBoolean(KEY_LAST_REGRESSION, decision.regression)
                .putLong(KEY_LAST_THRESHOLD_MS, decision.thresholdMs)
                .putLong(KEY_LAST_BASELINE_P50_MS, decision.baselineP50Ms)
                .putString(KEY_SAMPLES_TOTAL, encodeSamples(totalSamples))
                .putString(KEY_SAMPLES_APPLICATION, encodeSamples(applicationSamples))
                .putString(KEY_SAMPLES_MAIN_ACTIVITY, encodeSamples(mainActivitySamples))
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
            val totalSamples = decodeSamples(prefs.getString(KEY_SAMPLES_TOTAL, ""))
            val applicationSamples = decodeSamples(prefs.getString(KEY_SAMPLES_APPLICATION, ""))
            val mainActivitySamples = decodeSamples(prefs.getString(KEY_SAMPLES_MAIN_ACTIVITY, ""))

            val totalStats = toStats(totalSamples)
            val applicationStats = toStats(applicationSamples)
            val mainActivityStats = toStats(mainActivitySamples)

            val activeThresholdDecision = StartupRegressionPolicy.evaluate(totalSamples, totalStats.lastMs)

            val totalCount = prefs.getInt(KEY_TOTAL_COUNT, 0)
            val regressionCount = prefs.getInt(KEY_REGRESSION_COUNT, 0)
            val regressionRate = if (totalCount == 0) 0.0 else (regressionCount * 100.0) / totalCount

            return Snapshot(
                totalCount,
                regressionCount,
                regressionRate,
                prefs.getLong(KEY_LAST_TOTAL_DURATION_MS, 0L),
                prefs.getLong(KEY_LAST_APPLICATION_DURATION_MS, 0L),
                prefs.getLong(KEY_LAST_MAIN_ACTIVITY_DURATION_MS, 0L),
                prefs.getBoolean(KEY_LAST_REGRESSION, false),
                prefs.getLong(KEY_LAST_THRESHOLD_MS, StartupRegressionPolicy.DEFAULT_THRESHOLD_MS),
                prefs.getLong(KEY_LAST_BASELINE_P50_MS, 0L),
                activeThresholdDecision.thresholdMs,
                totalStats,
                applicationStats,
                mainActivityStats
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
    fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.1f%%", value)
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
        totalCount: Int,
        regressionCount: Int,
        regressionRate: Double,
        lastTotalDurationMs: Long,
        lastApplicationInitDurationMs: Long,
        lastMainActivityDurationMs: Long,
        @JvmField val lastRegression: Boolean,
        lastThresholdMs: Long,
        lastBaselineP50Ms: Long,
        activeThresholdMs: Long,
        total: OperationStats?,
        applicationInit: OperationStats?,
        mainActivity: OperationStats?
    ) {
        @JvmField
        val totalCount: Int = max(totalCount, 0)

        @JvmField
        val regressionCount: Int = max(regressionCount, 0)

        @JvmField
        val regressionRate: Double = max(regressionRate, 0.0)

        @JvmField
        val lastTotalDurationMs: Long = max(lastTotalDurationMs, 0L)

        @JvmField
        val lastApplicationInitDurationMs: Long = max(lastApplicationInitDurationMs, 0L)

        @JvmField
        val lastMainActivityDurationMs: Long = max(lastMainActivityDurationMs, 0L)

        @JvmField
        val lastThresholdMs: Long = max(lastThresholdMs, 0L)

        @JvmField
        val lastBaselineP50Ms: Long = max(lastBaselineP50Ms, 0L)

        @JvmField
        val activeThresholdMs: Long = max(activeThresholdMs, 0L)

        @JvmField
        val total: OperationStats = total ?: OperationStats.empty()

        @JvmField
        val applicationInit: OperationStats = applicationInit ?: OperationStats.empty()

        @JvmField
        val mainActivity: OperationStats = mainActivity ?: OperationStats.empty()

        companion object {
            @JvmStatic
            fun empty(): Snapshot {
                return Snapshot(
                    0,
                    0,
                    0.0,
                    0L,
                    0L,
                    0L,
                    false,
                    StartupRegressionPolicy.DEFAULT_THRESHOLD_MS,
                    0L,
                    StartupRegressionPolicy.DEFAULT_THRESHOLD_MS,
                    OperationStats.empty(),
                    OperationStats.empty(),
                    OperationStats.empty()
                )
            }
        }
    }
}
