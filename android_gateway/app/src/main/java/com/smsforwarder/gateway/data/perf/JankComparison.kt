package com.smsforwarder.gateway.data.perf

/** A set of janky-frame-percentage measurements from repeated runs of the same layer/condition. */
data class JankRunSet(val jankyPercents: List<Double>) {
    init { require(jankyPercents.isNotEmpty()) { "need at least one run" } }
    val mean: Double get() = jankyPercents.average()
    val min: Double get() = jankyPercents.min()
    val max: Double get() = jankyPercents.max()
    val spread: Double get() = max - min
}

enum class JankComparisonResult { WORSE, BETTER, INDISTINGUISHABLE, COMPARISON_SET_UNRELIABLE }

/**
 * Spec 0023, Допущение 3: `candidate` counts as distinguishable from `compareTo` only when
 * the difference in means exceeds `compareTo`'s own min-max spread - otherwise the
 * difference is read as noise, not effect (deliberately conservative, see the spec for the
 * specificity/sensitivity tradeoff this implies).
 *
 * If `compareTo`'s spread is itself more than half its mean, the comparison set is judged
 * too noisy to draw any conclusion from (COMPARISON_SET_UNRELIABLE) - the guard the spec
 * added against silently reading "no effect" out of an unreliable reference set.
 */
fun compareJank(candidate: JankRunSet, compareTo: JankRunSet): JankComparisonResult {
    if (compareTo.mean > 0.0 && compareTo.spread > compareTo.mean / 2) {
        return JankComparisonResult.COMPARISON_SET_UNRELIABLE
    }
    val diff = candidate.mean - compareTo.mean
    return when {
        diff > compareTo.spread -> JankComparisonResult.WORSE
        -diff > compareTo.spread -> JankComparisonResult.BETTER
        else -> JankComparisonResult.INDISTINGUISHABLE
    }
}
