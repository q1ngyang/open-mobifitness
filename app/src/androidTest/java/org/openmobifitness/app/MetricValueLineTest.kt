package org.openmobifitness.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.openmobifitness.app.ui.MetricValueLine

/** Real Android font shaping: emulated/equal-width text cannot prove proportional-font stability. */
class MetricValueLineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun proportionalDigitsKeepTheirUnitCloseAndStableAcrossLiveUpdates() {
        val reading = mutableStateOf("11.1")
        val unitText = mutableStateOf("rpm")
        val width = mutableStateOf(144.dp)
        compose.setContent {
            MaterialTheme(typography = Typography(headlineLarge = TextStyle(
                fontFamily = FontFamily.Cursive, fontFeatureSettings = "'pnum' 1, 'tnum' 0",
            ))) {
                Column(Modifier.width(width.value)) { MetricValueLine(reading.value, unitText.value, 36) }
            }
        }
        data class Geometry(val unitX: Float, val inkWidth: Float, val fontSize: Float)
        fun geometry(allowSmallerUnit: Boolean = false): Geometry {
            compose.waitForIdle()
            fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
                val results = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                return results.single()
            }
            val number = compose.onNodeWithTag("metric-number", useUnmergedTree = true)
            val unit = compose.onNodeWithTag("metric-unit", useUnmergedTree = true)
            val numberBounds = number.getUnclippedBoundsInRoot()
            val unitBounds = unit.getUnclippedBoundsInRoot()
            val n = layout(number)
            val u = layout(unit)
            val density = compose.density.density
            val gap = unitBounds.left.value - numberBounds.left.value - n.getLineRight(0) / density
            assertEquals("Unit stays six dp after the number", 6f, gap, 1.1f)
            assertEquals("Number and unit share a baseline", numberBounds.top.value + n.firstBaseline / density, unitBounds.top.value + u.firstBaseline / density, 1.1f)
            fun fits(layout: TextLayoutResult) = !layout.multiParagraph.didExceedMaxLines && (0 until layout.lineCount).all { line ->
                // Text's fractional paragraph size can exceed its integer node by < 1 px.
                !layout.isLineEllipsized(line) && layout.getLineRight(line) <= layout.size.width + 1f && layout.getLineBottom(line) <= layout.size.height + 1f
            }
            assertTrue("Complete number fits: ${n.layoutInput.text}, ${n.size}, paragraph=${n.multiParagraph.height}", fits(n))
            assertTrue("Complete unit fits: ${u.layoutInput.text}, ${u.size}, paragraph=${u.multiParagraph.height}", fits(u))
            assertTrue("Unit stays inside the cell", unitBounds.right <= width.value)
            if (!allowSmallerUnit) assertEquals("Units are more readable", 15f, u.layoutInput.style.fontSize.value, .01f)
            else assertTrue("Narrow-cell fallback stays readable", u.layoutInput.style.fontSize.value in 12f..15f)
            return Geometry(unitBounds.left.value, n.getLineRight(0) - n.getLineLeft(0), n.layoutInput.style.fontSize.value)
        }
        val first = geometry()
        assertEquals("Normal readings keep the larger size", 36f, first.fontSize, .01f)
        compose.runOnIdle { reading.value = "88.8" }
        val wide = geometry()
        assertTrue("The test really uses proportional digits", kotlin.math.abs(first.inkWidth - wide.inkWidth) > 1f)
        assertEquals("Digit shape does not move the unit", first.unitX, wide.unitX, .01f)
        listOf("17.3", "99.9", "11.1", "—", "88.8").forEach { value ->
            compose.runOnIdle { reading.value = value }
            assertEquals("Stable through $value", first.unitX, geometry().unitX, .01f)
        }
        compose.runOnIdle { reading.value = "100.0" }
        val expanded = geometry()
        assertTrue("Extra digit gets room", expanded.unitX > first.unitX)
        listOf("99.9", "100.0", "11.1", "101.1").forEach { value ->
            compose.runOnIdle { reading.value = value }
            val next = geometry()
            assertEquals("No oscillation at a digit boundary", expanded.unitX, next.unitX, .01f)
            assertEquals("No font-size oscillation", expanded.fontSize, next.fontSize, .01f)
        }
        compose.runOnIdle { reading.value = "7.2"; width.value = 118.dp }
        val resized = geometry()
        assertTrue("Window changes refit instead of keeping a stale wide slot", resized.unitX < expanded.unitX)
        compose.runOnIdle { reading.value = "123分59秒"; unitText.value = "/500 m"; width.value = 116.dp }
        geometry(allowSmallerUnit = true)
    }
}
