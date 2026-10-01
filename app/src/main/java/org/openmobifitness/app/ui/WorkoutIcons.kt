package org.openmobifitness.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

internal enum class WorkoutGlyph { DISPLAY, PAUSE, PLAY, STOP, FLOAT, LIST }

/** Small original vector glyphs share a 24-unit grid and follow the current content color. */
@Composable internal fun WorkoutIcon(glyph: WorkoutGlyph, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            when (glyph) {
                WorkoutGlyph.DISPLAY -> {
                    drawRoundRect(color, Offset(1.8f, 3f), Size(20.4f, 15f), CornerRadius(1.3f), style = stroke)
                    drawLine(color, Offset(12f, 18f), Offset(12f, 21f), 1.7f)
                    drawLine(color, Offset(7f, 21f), Offset(17f, 21f), 1.7f, StrokeCap.Round)
                }
                WorkoutGlyph.PAUSE -> {
                    drawRoundRect(color, Offset(5f, 3f), Size(5f, 18f), CornerRadius(1f))
                    drawRoundRect(color, Offset(14f, 3f), Size(5f, 18f), CornerRadius(1f))
                }
                WorkoutGlyph.PLAY -> drawPath(Path().apply { moveTo(6f, 3f); lineTo(21f, 12f); lineTo(6f, 21f); close() }, color)
                WorkoutGlyph.STOP -> drawRoundRect(color, Offset(4f, 4f), Size(16f, 16f), CornerRadius(1.5f))
                WorkoutGlyph.FLOAT -> {
                    drawPath(Path().apply { moveTo(8f, 17f); lineTo(5f, 17f); quadraticTo(2f, 17f, 2f, 14f); lineTo(2f, 5f); quadraticTo(2f, 2f, 5f, 2f); lineTo(14f, 2f); quadraticTo(17f, 2f, 17f, 5f); lineTo(17f, 8f) }, color, style = stroke)
                    drawRoundRect(color, Offset(8f, 8f), Size(14f, 14f), CornerRadius(2.5f), style = stroke)
                }
                WorkoutGlyph.LIST -> repeat(3) { index ->
                    val y = 5f + index * 7f
                    drawCircle(color, 1.3f, Offset(3f, y))
                    drawLine(color, Offset(8f, y), Offset(22f, y), 1.7f, StrokeCap.Round)
                }
            }
        }
    }
}
