package com.smsforwarder.gateway.data.perf

/**
 * Spec 0024: splits an ordered per-frame jank sequence into [segmentCount] contiguous,
 * as-evenly-as-possible chunks (by frame position, not by time) and returns each chunk's
 * janky%. Chunk sizes differ by at most one frame when [isJankByFrame].size isn't a
 * multiple of [segmentCount] (standard "as evenly as possible" partition, not a fixed-size
 * partition that would drop trailing frames).
 *
 * An empty input yields an empty result. A chunk that ends up with zero frames (only
 * possible when [isJankByFrame] has fewer frames than [segmentCount]) reports 0.0, the
 * same "no data" convention [FrameCounter.jankyPercent] uses for an empty run.
 */
fun segmentJankyPercents(isJankByFrame: List<Boolean>, segmentCount: Int): List<Double> {
    require(segmentCount > 0) { "segmentCount must be positive" }
    if (isJankByFrame.isEmpty()) return emptyList()

    val total = isJankByFrame.size
    val baseSize = total / segmentCount
    val remainder = total % segmentCount

    var start = 0
    return (0 until segmentCount).map { segmentIndex ->
        val size = baseSize + if (segmentIndex < remainder) 1 else 0
        val end = start + size
        val chunk = isJankByFrame.subList(start, end)
        start = end
        if (chunk.isEmpty()) 0.0 else chunk.count { it } * 100.0 / chunk.size
    }
}
