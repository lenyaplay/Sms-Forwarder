package com.smsforwarder.gateway.ui.tooling

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Spec 0034 (Milestone 29): exports the geometry of every visible
 * [SemanticsNode] under a rendered screen's semantics root as JSON, so
 * `tools/ui-metrics/ui_metrics/apb_formulas.py` can run the exact
 * Ngo/Teo/Byrne (2003) BM/EM/SYM formulas over real Compose-layout objects
 * instead of a pixel-based approximation (`symmetry.py`, spec 0033
 * Допущение 10). Reused by all three `*ScreenSnapshotTest.kt` (spec 0033
 * Stage A) so the JSON is exported for the same screen/theme combinations
 * as the existing 6 baseline PNGs, right after the same `captureRoboImage()`
 * render pass - not a new render, just a second read of the same
 * already-composed tree.
 */
@Serializable
data class GeometryObject(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Has a text or contentDescription semantics property - used by the "by type" weight function (project heuristic, not from the paper). */
    val isTextual: Boolean,
    /** Has an OnClick semantics action - used by the "by type" weight function. */
    val isInteractive: Boolean,
)

@Serializable
data class ScreenGeometry(
    val screenWidth: Float,
    val screenHeight: Float,
    val objects: List<GeometryObject>,
)

private val json = Json { prettyPrint = true }

/**
 * Visible = non-zero-area bounds and not marked hidden from the user
 * (`SemanticsProperties.InvisibleToUser`, e.g. a `Modifier.semantics { invisibleToUser() }`
 * node or a node collapsed to zero size by layout - neither contributes to
 * on-screen balance).
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun isVisible(node: SemanticsNode): Boolean {
    if (node.config.getOrNull(SemanticsProperties.InvisibleToUser) != null) return false
    val bounds = node.boundsInRoot
    return bounds.width > 0f && bounds.height > 0f
}

@OptIn(ExperimentalComposeUiApi::class)
private fun collectVisibleObjects(node: SemanticsNode, out: MutableList<GeometryObject>) {
    if (isVisible(node)) {
        val bounds = node.boundsInRoot
        val isTextual = node.config.getOrNull(SemanticsProperties.Text) != null ||
            node.config.getOrNull(SemanticsProperties.ContentDescription) != null
        val isInteractive = node.config.getOrNull(SemanticsActions.OnClick) != null
        out += GeometryObject(
            x = bounds.left,
            y = bounds.top,
            width = bounds.width,
            height = bounds.height,
            isTextual = isTextual,
            isInteractive = isInteractive,
        )
    }
    node.children.forEach { collectVisibleObjects(it, out) }
}

/**
 * Walks the semantics tree rooted at [root] and returns every visible node's
 * geometry (root itself included, since it carries the full-screen bounds).
 *
 * Nested wrapper nodes (the semantics root, `Surface`, a plain background
 * `Box`) routinely share the exact same bounds as one of their ancestors -
 * without deduplication each would count as a separate `a_i=1` object in
 * the BM/EM/SYM formulas, inflating the weight of "the whole screen" and
 * biasing the quadrant split purely from structural nesting depth, not
 * actual visual content (found via `peer-review-template`, spec 0034).
 * Nodes with identical bounds collapse into one, keeping isTextual/
 * isInteractive true if any of the collapsed nodes had it set.
 */
fun exportGeometry(root: SemanticsNode): ScreenGeometry {
    val objects = mutableListOf<GeometryObject>()
    collectVisibleObjects(root, objects)
    val deduped = objects
        .groupBy { Triple(it.x, it.y, it.width) to it.height }
        .map { (_, group) ->
            group.first().copy(
                isTextual = group.any { it.isTextual },
                isInteractive = group.any { it.isInteractive },
            )
        }
    val screenBounds = root.boundsInRoot
    return ScreenGeometry(
        screenWidth = screenBounds.width,
        screenHeight = screenBounds.height,
        objects = deduped,
    )
}

/**
 * Writes [geometry] next to the Roborazzi PNG for the same test, following
 * the same `<FQCN>.<method>` naming Roborazzi's auto-naming produces (see
 * `captureRoboImage()` call sites) - `outputDir` must match the Gradle
 * `roborazzi { outputDir }` config (`src/test/snapshots`, see
 * `app/build.gradle.kts`).
 */
fun writeGeometryJson(outputDir: File, testClassFqcn: String, testMethodName: String, geometry: ScreenGeometry) {
    val file = File(outputDir, "$testClassFqcn.$testMethodName.geometry.json")
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(ScreenGeometry.serializer(), geometry))
}
