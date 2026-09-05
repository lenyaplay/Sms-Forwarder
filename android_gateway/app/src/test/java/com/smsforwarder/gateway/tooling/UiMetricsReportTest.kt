package com.smsforwarder.gateway.tooling

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import java.io.File

/**
 * Spec 0033, Stage A: informational report only. Never fails the build/CI
 * (product decision, see docs/specs/0033-ui-metrics-tooling.md Допущение 4) -
 * every check is wrapped so no exception from this tool can fail the test.
 */
class UiMetricsReportTest {

    @Test
    fun printUiMetricsReport() {
        try {
            printContrastReport()
        } catch (e: Throwable) {
            println("[uiMetricsReport] contrast report failed to run: $e")
        }
        try {
            printTouchTargetReport()
        } catch (e: Throwable) {
            println("[uiMetricsReport] touch target report failed to run: $e")
        }
    }

    private data class RolePair(val name: String, val select: (ColorScheme) -> Pair<Color, Color>)

    private val rolePairs = listOf(
        RolePair("primary/onPrimary") { it.primary to it.onPrimary },
        RolePair("surface/onSurface") { it.surface to it.onSurface },
        RolePair("background/onBackground") { it.background to it.onBackground },
        RolePair("error/onError") { it.error to it.onError },
        RolePair("secondaryContainer/onSecondaryContainer") { it.secondaryContainer to it.onSecondaryContainer },
        RolePair("surfaceVariant/onSurfaceVariant") { it.surfaceVariant to it.onSurfaceVariant },
    )

    private fun printContrastReport() {
        println("[uiMetricsReport] WCAG contrast report (informational, non-blocking)")
        println("%-42s %-8s %6s %10s %10s".format("theme / role pair", "ratio", "", "AA-normal", "AA-large"))
        for ((themeName, scheme) in listOf("light" to lightColorScheme(), "dark" to darkColorScheme())) {
            for (pair in rolePairs) {
                val (bg, fg) = pair.select(scheme)
                val ratio = ContrastRatio.ratio(bg, fg)
                val normalPass = if (ratio >= ContrastRatio.AA_NORMAL_TEXT_THRESHOLD) "PASS" else "FAIL"
                val largePass = if (ratio >= ContrastRatio.AA_LARGE_TEXT_THRESHOLD) "PASS" else "FAIL"
                println("%-42s %-8.2f %10s %10s".format("$themeName / ${pair.name}", ratio, normalPass, largePass))
            }
        }
    }

    private val interactiveElementPattern = Regex(
        "IconButton|FloatingActionButton|\\bButton\\(|TextButton|OutlinedButton|FilledTonalButton|Checkbox|Switch|RadioButton|\\.clickable\\(",
    )
    // Window-based, not scoped to a single composable's modifier chain: two interactive
    // elements within 3 lines of each other can cross-contaminate (one's real >=48dp
    // size can mask the other's missing one). Accepted per spec 0033 (best-effort,
    // informational) - biases toward under- not over-reporting in that specific case,
    // so a real gap could go unflagged; noted here for whoever tightens this later.
    private val explicitMinSizePattern = Regex("""\.(size|sizeIn|defaultMinSize)\([^)]*(\d+)\.dp""")

    private fun printTouchTargetReport() {
        println()
        println("[uiMetricsReport] Touch-target heuristic report (best-effort, informational, non-blocking)")
        val uiDir = findUiSourceDir()
        if (uiDir == null) {
            println("  ui/ source directory not found from working dir ${File(".").absolutePath} - skipping")
            return
        }
        var flagged = 0
        uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                if (interactiveElementPattern.containsMatchIn(line)) {
                    val windowStart = maxOf(0, index - 3)
                    val windowEnd = minOf(lines.size, index + 4)
                    val window = lines.subList(windowStart, windowEnd).joinToString("\n")
                    val hasExplicitSize = explicitMinSizePattern.findAll(window).any { match ->
                        (match.groupValues[2].toIntOrNull() ?: 0) >= 48
                    }
                    if (!hasExplicitSize) {
                        flagged++
                        println("  ${file.relativeTo(uiDir.parentFile ?: uiDir)}:${index + 1} - no >=48dp size modifier found nearby (heuristic)")
                    }
                }
            }
        }
        println("  Total flagged occurrences: $flagged (heuristic - false positives/negatives expected, see spec 0033)")
    }

    /** Best-effort project-root discovery: Gradle's Test task working dir is normally the module dir already, but this doesn't assume it. */
    private fun findUiSourceDir(): File? {
        var dir = File(".").absoluteFile
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/smsforwarder/gateway/ui")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return null
        }
        return null
    }
}
