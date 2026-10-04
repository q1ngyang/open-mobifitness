package org.openmobifitness.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openmobifitness.core.Machine

@Composable internal fun FilterGlyph(label: String) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp).semantics { contentDescription=label }) {
        val sx=size.width/24; val sy=size.height/24
        val path=Path().apply { moveTo(3*sx,5*sy); lineTo(21*sx,5*sy); lineTo(14*sx,13*sy); lineTo(14*sx,20*sy); lineTo(10*sx,18*sy); lineTo(10*sx,13*sy); close() }
        drawPath(path,color,style=Stroke(1.7.dp.toPx(),cap=StrokeCap.Round))
    }
}
@Composable internal fun MachineGlyph(machine: Machine,modifier: Modifier=Modifier) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(modifier.size(24.dp)) {
        val scale=size.minDimension/24
        fun p(x: Float,y: Float)=Offset(x*scale,y*scale)
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(color,p(x,y),p(x2,y2),1.7f*scale,StrokeCap.Round)
        fun circle(x: Float,y: Float,r: Float)=drawCircle(color,r*scale,p(x,y),style=Stroke(1.6f*scale))
        when(machine) {
            Machine.BIKE -> { circle(5f,17f,4f); circle(19f,17f,4f); line(5f,17f,10f,8f); line(10f,8f,15f,17f); line(5f,17f,15f,17f); line(10f,8f,18f,8f); line(18f,8f,19f,17f); line(16f,4f,18f,4f); line(18f,4f,18f,8f); line(8f,7f,12f,7f) }
            Machine.ROWER -> { line(2f,20f,22f,20f); circle(19f,14f,4f); line(5f,16f,15f,16f); line(9f,16f,9f,12f); line(6f,12f,11f,12f); line(10f,12f,16f,8f); line(14f,7f,18f,9f) }
            Machine.TREADMILL -> { line(2f,20f,21f,20f); line(3f,17f,20f,17f); line(18f,17f,16f,5f); line(12f,5f,19f,5f); line(12f,5f,10f,9f); line(10f,9f,16f,9f); line(4f,17f,3f,20f) }
            Machine.DUMBBELL -> { line(8f,12f,16f,12f); line(5f,5f,5f,19f); line(8f,7f,8f,17f); line(16f,7f,16f,17f); line(19f,5f,19f,19f); line(2f,9f,2f,15f); line(22f,9f,22f,15f) }
            Machine.JUMP_ROPE -> { line(4f,4f,7f,8f); line(20f,4f,17f,8f); val path=Path().apply { moveTo(4*scale,8*scale); cubicTo(-2*scale,24*scale,26*scale,24*scale,20*scale,8*scale) }; drawPath(path,color,style=Stroke(1.7f*scale)) }
            Machine.HEART -> { val path=Path().apply { moveTo(12*scale,21*scale); cubicTo(-6*scale,9*scale,5*scale,-1*scale,12*scale,7*scale); cubicTo(19*scale,-1*scale,30*scale,9*scale,12*scale,21*scale) }; drawPath(path,color,style=Stroke(1.7f*scale)) }
            Machine.UNKNOWN -> { circle(12f,12f,9f); line(8f,12f,16f,12f); line(12f,8f,12f,16f) }
            else -> { line(2f,21f,22f,21f); circle(9f,16f,4f); line(5f,17f,16f,15f); line(16f,15f,19f,4f); line(19f,4f,17f,2f); line(9f,16f,12f,4f); line(12f,4f,10f,2f); line(4f,18f,10f,18f); line(13f,15f,19f,15f) }
        }
    }
}
