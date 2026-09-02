package com.smsforwarder.gateway.data.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSegmentationTest {

    @Test
    fun emptySequenceYieldsEmptyResult() {
        assertEquals(emptyList<Double>(), segmentJankyPercents(emptyList(), segmentCount = 10))
    }

    @Test
    fun singleFrameOnlyFillsFirstSegmentOthersReportZero() {
        val result = segmentJankyPercents(listOf(true), segmentCount = 10)
        assertEquals(10, result.size)
        assertEquals(100.0, result[0], 0.0001)
        assertEquals(List(9) { 0.0 }, result.drop(1))
    }

    @Test
    fun evenlyDivisibleSequenceSplitsIntoEqualChunks() {
        // 10 frames, 2 segments of 5 -> [0,1,2,3,4]=all jank, [5..9]=all clean
        val frames = List(5) { true } + List(5) { false }
        val result = segmentJankyPercents(frames, segmentCount = 2)
        assertEquals(listOf(100.0, 0.0), result)
    }

    @Test
    fun frameCountNotMultipleOfSegmentCountDistributesRemainderToEarlySegments() {
        // 11 frames, 3 segments -> sizes 4,4,3 (remainder 2 goes to the first two segments)
        val frames = List(11) { true }
        val result = segmentJankyPercents(frames, segmentCount = 3)
        assertEquals(3, result.size)
        result.forEach { assertEquals(100.0, it, 0.0001) }
    }

    @Test
    fun mixedFramesProduceCorrectPerSegmentPercent() {
        // 4 segments of 5 frames each: [1 jank/5], [5 jank/5], [0 jank/5], [3 jank/5]
        val frames = listOf(true, false, false, false, false) +
            List(5) { true } +
            List(5) { false } +
            listOf(true, true, true, false, false)
        val result = segmentJankyPercents(frames, segmentCount = 4)
        assertEquals(listOf(20.0, 100.0, 0.0, 60.0), result)
    }
}
