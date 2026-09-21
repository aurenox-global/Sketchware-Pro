package pro.sketchware.metrics

import kotlin.math.max

class StartupRegressionDecision(
    sampleCount: Int,
    baselineP50Ms: Long,
    thresholdMs: Long,
    @JvmField val regression: Boolean
) {
    @JvmField
    val sampleCount: Int = max(sampleCount, 0)

    @JvmField
    val baselineP50Ms: Long = max(baselineP50Ms, 0L)

    @JvmField
    val thresholdMs: Long = max(thresholdMs, 0L)
}
