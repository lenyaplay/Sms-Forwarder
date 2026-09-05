package com.smsforwarder.gateway.tooling

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastRatioTest {

    @Test
    fun whiteOnWhiteIsRatioOne() {
        assertEquals(1.0, ContrastRatio.ratio(Color.White, Color.White), 0.001)
    }

    @Test
    fun blackOnWhiteIsRatioTwentyOne() {
        assertEquals(21.0, ContrastRatio.ratio(Color.Black, Color.White), 0.01)
    }

    @Test
    fun orderOfArgumentsDoesNotMatter() {
        val a = ContrastRatio.ratio(Color.Black, Color.White)
        val b = ContrastRatio.ratio(Color.White, Color.Black)
        assertEquals(a, b, 0.0001)
    }

    @Test
    fun exactlyAaNormalThresholdPasses() {
        // A grey chosen so the ratio against white lands very close to 4.5:1,
        // then checked with a tolerance wide enough for the boundary itself
        // to matter (not the exact grey value).
        val grey = Color(0xFF767676.toInt())
        val ratio = ContrastRatio.ratio(grey, Color.White)
        assertEquals(ContrastRatio.AA_NORMAL_TEXT_THRESHOLD, ratio, 0.2)
    }

    @Test
    fun exactlyAaLargeThresholdPasses() {
        // Same approach as exactlyAaNormalThresholdPasses, targeting the 3.0:1
        // large-text threshold with a different grey.
        val grey = Color(0xFF969696.toInt())
        val ratio = ContrastRatio.ratio(grey, Color.White)
        assertEquals(ContrastRatio.AA_LARGE_TEXT_THRESHOLD, ratio, 0.1)
    }
}
