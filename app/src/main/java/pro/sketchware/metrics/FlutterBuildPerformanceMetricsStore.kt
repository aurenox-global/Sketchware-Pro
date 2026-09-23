package pro.sketchware.metrics

import android.content.Context
import android.content.SharedPreferences
import pro.sketchware.flutter.FlutterBuildMode
import java.util.Locale
import kotlin.math.max

/**
 * Telemetria local de builds Flutter (analogo a [KmpBuildPerformanceMetricsStore]).
 *
 * Solo depende del enum [FlutterBuildMode] del carril B2: no toca tipos del carril C, de modo
 * que este fichero compila aunque el orquestador no este presente.
 */
object FlutterBuildPerformanceMetricsStore {

    private const val PREF_NAME = "flutter_build_performance_metrics"

    private const val KEY_TOTAL_RUNS = "total_runs"
    private const val KEY_SUCCESS_RUNS = "success_runs"
    private const val KEY_FAILURE_RUNS = "failure_runs"
    private const val KEY_LAST_TIMESTAMP_MS = "last_timestamp_ms"
    private const val KEY_LAST_MODE = "last_mode"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"
    private const val KEY_LAST_APK_PATH = "last_apk_path"
    private const val KEY_TOTAL_DURATION_MS = "total_duration_ms"

    private val LOCK = Any()

    @JvmStatic
    fun record(
        context: Context?,
        mode: FlutterBuildMode,
        success: Boolean,
        durationMs: Long,
        apkPath: String?
    ) {
        if (context == null) {
            return
        }

        val safeDurationMs = max(durationMs, 0L)

        synchronized(LOCK) {
            val prefs = prefs(context)
            prefs.edit()
                .putInt(KEY_TOTAL_RUNS, prefs.getInt(KEY_TOTAL_RUNS, 0) + 1)
                .putInt(KEY_SUCCESS_RUNS, prefs.getInt(KEY_SUCCESS_RUNS, 0) + if (success) 1 else 0)
                .putInt(KEY_FAILURE_RUNS, prefs.getInt(KEY_FAILURE_RUNS, 0) + if (success) 0 else 1)
                .putLong(KEY_LAST_TIMESTAMP_MS, System.currentTimeMillis())
                .putString(KEY_LAST_MODE, mode.name)
                .putBoolean(KEY_LAST_SUCCESS, success)
                .putLong(KEY_LAST_DURATION_MS, safeDurationMs)
                .putString(KEY_LAST_APK_PATH, apkPath ?: "")
                .putLong(KEY_TOTAL_DURATION_MS, prefs.getLong(KEY_TOTAL_DURATION_MS, 0L) + safeDurationMs)
                .apply()
        }
    }

    @JvmStatic
    fun snapshot(context: Context?): String {
        if (context == null) {
            return "Flutter build metrics unavailable"
        }

        synchronized(LOCK) {
            val prefs = prefs(context)
            val totalRuns = prefs.getInt(KEY_TOTAL_RUNS, 0)
            val successRuns = prefs.getInt(KEY_SUCCESS_RUNS, 0)
            val failureRuns = prefs.getInt(KEY_FAILURE_RUNS, 0)
            val totalDurationMs = prefs.getLong(KEY_TOTAL_DURATION_MS, 0L)
            val averageDurationMs = if (totalRuns == 0) 0L else totalDurationMs / totalRuns

            return String.format(
                Locale.US,
                "Ejecuciones: %d (ok: %d, fallos: %d)%nUltimo modo: %s | ultimo exito: %s | ultima duracion: %d ms%nDuracion media: %d ms",
                totalRuns,
                successRuns,
                failureRuns,
                prefs.getString(KEY_LAST_MODE, "-") ?: "-",
                prefs.getBoolean(KEY_LAST_SUCCESS, false).toString(),
                prefs.getLong(KEY_LAST_DURATION_MS, 0L),
                averageDurationMs
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

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
