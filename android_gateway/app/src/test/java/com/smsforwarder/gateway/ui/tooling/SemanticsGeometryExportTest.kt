package com.smsforwarder.gateway.ui.tooling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

/**
 * Spec 0034 (Milestone 29): unit test for the semantics-tree walk itself
 * (SemanticsGeometryExport.kt), not the BM/EM/SYM formulas (those live in
 * tools/ui-metrics/tests/test_apb_formulas.py). Verifies the exported JSON
 * geometry matches a known, hand-placed layout.
 */
@RunWith(RobolectricTestRunner::class)
class SemanticsGeometryExportTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    private fun KnownLayout() {
        MaterialTheme {
            Box(Modifier.width(200.dp).height(100.dp)) {
                // Textual leaf, top-left corner.
                Text("Hello", modifier = Modifier.offset(x = 0.dp, y = 0.dp))
                // Interactive, non-textual leaf, offset into the box.
                Box(
                    Modifier
                        .offset(x = 50.dp, y = 50.dp)
                        .width(20.dp)
                        .height(20.dp)
                        .clickable {},
                )
                // Explicitly hidden - must not appear in the export.
                Box(
                    Modifier
                        .width(20.dp)
                        .height(20.dp)
                        .semantics { invisibleToUser() },
                )
            }
        }
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test
    fun exportedGeometryMatchesKnownLayout() {
        composeRule.setContent { KnownLayout() }
        val geometry = exportGeometry(composeRule.onRoot().fetchSemanticsNode())

        // The hidden Box must be excluded entirely.
        assertTrue(geometry.objects.none { it.width == 20f && it.height == 20f && !it.isInteractive && !it.isTextual })

        val textObject = geometry.objects.single { it.isTextual }
        assertEquals(0f, textObject.x, 0.1f)
        assertEquals(0f, textObject.y, 0.1f)

        val interactiveObject = geometry.objects.single { it.isInteractive }
        assertTrue(interactiveObject.x > 0f)
        assertTrue(interactiveObject.y > 0f)
        assertEquals(20f, interactiveObject.width, 3f)
        assertEquals(20f, interactiveObject.height, 3f)
    }
}
