package pro.sketchware.metrics

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import pro.sketchware.featureflags.FeatureFlags

object StartupPerformanceTracker {
    private val PROCESS_START_ELAPSED_MS: Long = SystemClock.elapsedRealtime()
    private val STARTUP_RECORDED = AtomicBoolean(false)

    @Volatile
    private var applicationReadyElapsedMs: Long = -1L

    @JvmStatic
    fun markApplicationCreated(context: Context?) {
        if (context == null) {
            return
        }
        applicationReadyElapsedMs = SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun markMainActivityResumed(context: Context?) {
        if (context == null || !STARTUP_RECORDED.compareAndSet(false, true)) {
            return
        }

        val appContext = context.applicationContext
        if (!FeatureFlags.isEnabled(appContext, FeatureFlags.Key.STARTUP_PROFILING_THRESHOLDS)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val appReadyAt = if (applicationReadyElapsedMs > 0L) applicationReadyElapsedMs else now

        val totalDurationMs = max(now - PROCESS_START_ELAPSED_MS, 0L)
        val applicationInitDurationMs = max(appReadyAt - PROCESS_START_ELAPSED_MS, 0L)
        val mainActivityReadyDurationMs = max(now - appReadyAt, 0L)

        StartupPerformanceMetricsStore.recordStartup(
            appContext,
            totalDurationMs,
            applicationInitDurationMs,
            mainActivityReadyDurationMs
        )
    }
}
