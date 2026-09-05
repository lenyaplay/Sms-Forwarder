package com.smsforwarder.gateway.tooling

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x relative luminance / contrast ratio (spec 0033, Stage A: report-only, non-blocking). */
object ContrastRatio {

    private fun linearize(channel: Float): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun ratio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    const val AA_NORMAL_TEXT_THRESHOLD = 4.5
    const val AA_LARGE_TEXT_THRESHOLD = 3.0
}
