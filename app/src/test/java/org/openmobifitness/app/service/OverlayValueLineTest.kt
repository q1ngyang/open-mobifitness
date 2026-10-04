package org.openmobifitness.app.service

import android.app.Application
import android.graphics.Typeface
import android.view.View
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.openmobifitness.app.MetricReading

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayValueLineTest {
    private fun measure(view: View,width: Int,height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
    }
    @Test fun unitsStayCloseAndDoNotOscillateWithProportionalDigitsOrMissingSamples() {
        val app=RuntimeEnvironment.getApplication()
        for(expanded in listOf(false,true)) for(scale in listOf(1f,1.3f,2f)) {
            val c=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply { fontScale=scale })
            val d=c.resources.displayMetrics.density
            val cell=OverlayMetricView(c,android.graphics.Color.BLACK,android.graphics.Color.GRAY,expanded)
            cell.value.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            val width=((if(scale<1.5f) 128 else 260)*d).toInt(); val height=(64*scale*d).toInt()
            var anchor=-1; var size=0f
            for(number in listOf("111.1","188.8","199.9","100.0","99.9","—","100.0","11.1")) {
                cell.bind(MetricReading("Frequency",number,"rpm")); measure(cell,width,height)
                if(anchor<0) { anchor=cell.unit.left; size=cell.value.textSize }
                assertEquals("Stable unit position at $scale",anchor,cell.unit.left)
                assertEquals("Stable number size",size,cell.value.textSize,0f)
                assertEquals(4*d, (cell.unit.left-cell.value.right).toFloat(),1f)
                assertEquals(cell.value.top+cell.value.baseline,cell.unit.top+cell.unit.baseline)
                assertTrue(cell.unit.right<=cell.width)
                assertTrue(cell.value.layout.getLineWidth(0)<=cell.value.width+1)
            }
        }
    }
    @Test fun growingThenShrinkingReadingsKeepTheNewReservationAndFitTheCell() {
        val c=RuntimeEnvironment.getApplication(); val d=c.resources.displayMetrics.density
        val cell=OverlayMetricView(c,android.graphics.Color.BLACK,android.graphics.Color.GRAY,false)
        fun show(number: String) { cell.bind(MetricReading("Energy",number,"kcal",true)); measure(cell,(110*d).toInt(),(56*d).toInt()) }
        show("9.9"); val first=cell.unit.left
        show("99999.9"); val grown=cell.unit.left; val size=cell.value.textSize
        assertTrue(grown>=first)
        for(number in listOf("99.9","99999.9","—","9.9")) {
            show(number)
            assertEquals(grown,cell.unit.left); assertEquals(size,cell.value.textSize,0f)
            assertEquals("Energy ≈",cell.label.text.toString()); assertFalse(cell.value.text.contains("≈"))
            assertEquals(1,cell.value.lineCount)
            assertTrue(cell.value.layout.getLineWidth(0)<=cell.value.width+1)
        }
    }
    @Test fun resistancePercentagesUseTheSameStableAdjacentBaseline() {
        val c=RuntimeEnvironment.getApplication(); val d=c.resources.displayMetrics.density
        val line=OverlayValueLine(c,android.graphics.Color.BLACK,android.graphics.Color.GRAY,25,centered=true)
        line.value.typeface=Typeface.create("serif",Typeface.NORMAL)
        var left=-1
        for((level,percent) in listOf("24" to "100%","11" to "46%","9" to "38%","24" to "100%","1" to "4%")) {
            line.bind(level,percent,"resistance"); measure(line,(100*d).toInt(),(36*d).toInt())
            if(left<0) left=line.unit.left
            assertEquals(left,line.unit.left)
            assertEquals(4*d,(line.unit.left-line.value.right).toFloat(),1f)
            assertEquals(line.value.top+line.value.baseline,line.unit.top+line.unit.baseline)
            assertTrue(line.unit.right<=line.width)
        }
    }
}
