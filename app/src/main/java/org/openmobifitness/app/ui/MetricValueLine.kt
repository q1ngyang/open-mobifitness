package org.openmobifitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Keep the unit beside the reading, rather than at the cell's trailing edge.
 * Reserve the widest digits for this reading's format, including proportional fonts.
 * Right-align inside that slot so the visible number-to-unit gap stays small.
 * The slot only grows during this composition's lifetime: 99.9 / 100.0, missing
 * samples and recovery must not repeatedly move the unit or change the font size.
 */
@Composable internal fun MetricValueLine(value: String, unit: String, maxSp: Int, unitSp: Int = 15) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val typography = MaterialTheme.typography
    val numberStyle = typography.headlineLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = maxSp.sp,
        lineHeight = (maxSp * 1.15f).sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = typography.headlineLarge.fontFeatureSettings ?: "tnum",
        letterSpacing = (-.5).sp,
        textAlign = TextAlign.End,
    )
    val unitStyle = typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = unitSp.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Start,
    )
    // Preserve punctuation, signs and localized digit alphabets; don't assume that
    // '8' is the widest glyph or that the user's font implements tabular digits.
    val shape = remember(value) { value.map { if (it.isDigit()) it - it.digitToInt() else it }.joinToString("") }
    val template = remember(shape, numberStyle, measurer) {
        (0..9).map { digit -> shape.map { if (it.isDigit()) it + digit else it }.joinToString("") }
            .maxBy { measurer.textWidth(it, numberStyle) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Refit after a window/font change. Each metric gets its own composition key
        // at the call site, so a reordered grid cannot inherit another metric's slot.
        var heldNumber by remember(maxWidth, numberStyle, measurer) { mutableStateOf(template) }
        var heldUnit by remember(maxWidth, unitStyle, measurer) { mutableStateOf(unit) }
        val numberSample = listOf(heldNumber, template, value).maxBy { measurer.textWidth(it, numberStyle) }
        val unitSample = if (measurer.textWidth(unit, unitStyle) > measurer.textWidth(heldUnit, unitStyle)) unit else heldUnit
        SideEffect {
            heldNumber = numberSample
            heldUnit = unitSample
        }
        val available = with(density) { maxWidth.toPx() }
        val gap = if (unit.isEmpty()) 0.dp else 6.dp
        val gapPx = with(density) { gap.toPx() }
        val fit = remember(numberSample, unitSample, available, gapPx, numberStyle, unitStyle, measurer) {
            // Fit against the reserved sample, never the instantaneous reading.
            // Keep units readable; only long readings in narrow cells shrink.
            (unitSp downTo 12).firstNotNullOfOrNull { unitSize ->
                (maxSp downTo 12).firstOrNull { size ->
                    measurer.textWidth(numberSample, numberStyle.copy(fontSize = size.sp)) +
                        measurer.textWidth(unitSample, unitStyle.copy(fontSize = unitSize.sp)) + gapPx <= available
                }?.let { size -> size to unitSize }
            } ?: (12 to 12)
        }
        val fittedStyle = numberStyle.copy(fontSize = fit.first.sp, lineHeight = (fit.first * 1.15f).sp)
        val numberWidth = measurer.textWidth(numberSample, fittedStyle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            BasicText(
                value,
                Modifier.width(with(density) { numberWidth.toDp() }).alignByBaseline().testTag("metric-number"),
                style = fittedStyle, maxLines = 1, softWrap = false,
            )
            if (unit.isNotEmpty()) BasicText(
                unit, Modifier.alignByBaseline().testTag("metric-unit"),
                style = unitStyle.copy(fontSize = fit.second.sp), maxLines = 1, softWrap = false,
            )
        }
    }
}

private fun TextMeasurer.textWidth(value: String, style: TextStyle): Int =
    if (value.isEmpty()) 0 else measure(value, style, maxLines = 1, softWrap = false).size.width
