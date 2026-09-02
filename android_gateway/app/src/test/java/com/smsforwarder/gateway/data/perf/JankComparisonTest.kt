package com.smsforwarder.gateway.data.perf

import org.junit.Assert.assertEquals
import org.junit.Test

class JankComparisonTest {

    private fun runs(vararg percents: Double) = JankRunSet(percents.toList())

    @Test
    fun candidateWorseWhenDifferenceExceedsBaselineSpread() {
        val baseline = runs(60.0, 62.0, 64.0, 61.0, 63.0) // spread 4.0, mean 62.0
        val candidate = runs(90.0, 91.0, 89.0, 90.0, 92.0) // mean 90.4, diff 28.4 > 4.0
        assertEquals(JankComparisonResult.WORSE, compareJank(candidate, baseline))
    }

    @Test
    fun candidateBetterWhenDifferenceExceedsBaselineSpreadInReverse() {
        val baseline = runs(60.0, 62.0, 64.0, 61.0, 63.0) // spread 4.0, mean 62.0
        val candidate = runs(10.0, 11.0, 9.0, 10.0, 12.0) // mean 10.4, diff -51.6
        assertEquals(JankComparisonResult.BETTER, compareJank(candidate, baseline))
    }

    @Test
    fun candidateIndistinguishableWhenDifferenceWithinBaselineSpread() {
        val baseline = runs(60.0, 62.0, 64.0, 61.0, 63.0) // spread 4.0, mean 62.0
        val candidate = runs(63.0, 64.0, 65.0, 63.0, 64.0) // mean 63.8, diff 1.8 < 4.0
        assertEquals(JankComparisonResult.INDISTINGUISHABLE, compareJank(candidate, baseline))
    }

    @Test
    fun candidateIndistinguishableWhenDifferenceExactlyEqualsSpread() {
        // diff == spread is not strictly greater than spread, so it must NOT count as an effect.
        val baseline = runs(60.0, 64.0) // spread 4.0, mean 62.0
        val candidate = runs(66.0, 66.0) // mean 66.0, diff exactly 4.0
        assertEquals(JankComparisonResult.INDISTINGUISHABLE, compareJank(candidate, baseline))
    }

    @Test
    fun comparisonUnreliableWhenBaselineSpreadExceedsHalfItsMean() {
        // spread 40.0, mean 40.0 -> spread > mean/2 (20.0) -> unreliable regardless of candidate
        val baseline = runs(20.0, 60.0, 40.0, 40.0, 40.0)
        val candidate = runs(90.0, 91.0, 89.0, 90.0, 92.0)
        assertEquals(JankComparisonResult.COMPARISON_SET_UNRELIABLE, compareJank(candidate, baseline))
    }

    @Test
    fun comparisonNotUnreliableWhenBaselineIsPerfectZeroJankWithNoSpread() {
        val baseline = runs(0.0, 0.0, 0.0)
        val candidate = runs(5.0, 6.0, 4.0)
        assertEquals(JankComparisonResult.WORSE, compareJank(candidate, baseline))
    }
}
