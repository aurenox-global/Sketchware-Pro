package pro.sketchware.metrics

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object StartupRegressionPolicy {
    const val MIN_BASELINE_SAMPLES: Int = 5
    const val DEFAULT_THRESHOLD_MS: Long = 1800L
    const val RELATIVE_FACTOR: Double = 1.35
    const val ABSOLUTE_BUFFER_MS: Long = 120L

    @JvmStatic
    fun evaluate(historicalDurationsMs: List<Long?>?, currentDurationMs: Long): StartupRegressionDecision {
        val normalized = normalize(historicalDurationsMs)
        val baselineP50 = percentile(normalized, 50)

        val dynamicThreshold = if (normalized.size < MIN_BASELINE_SAMPLES) {
            DEFAULT_THRESHOLD_MS
        } else {
            round((baselineP50 * RELATIVE_FACTOR) + ABSOLUTE_BUFFER_MS).toLong()
        }

        val threshold = max(DEFAULT_THRESHOLD_MS, dynamicThreshold)
        val regression = max(currentDurationMs, 0L) > threshold

        return StartupRegressionDecision(
            normalized.size,
            baselineP50,
            threshold,
            regression
        )
    }

    private fun normalize(samples: List<Long?>?): List<Long> {
        if (samples.isNullOrEmpty()) {
            return emptyList()
        }

        val normalized = ArrayList<Long>(samples.size)
        for (sample in samples) {
            if (sample == null) {
                continue
            }
            normalized.add(max(sample, 0L))
        }
        return normalized
    }

    private fun percentile(sortedSamplesInput: List<Long>?, percentile: Int): Long {
        if (sortedSamplesInput.isNullOrEmpty()) {
            return 0L
        }

        val sortedSamples = sortedSamplesInput.toMutableList()
        sortedSamples.sort()

        val clampedPercentile = max(0, min(percentile, 100))
        var index = ceil((clampedPercentile / 100.0) * sortedSamples.size).toInt() - 1
        index = max(0, min(index, sortedSamples.size - 1))
        return max(sortedSamples[index], 0L)
    }
}
