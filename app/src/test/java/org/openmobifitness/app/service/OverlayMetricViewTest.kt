package org.openmobifitness.app.service

import android.app.Application
import android.view.View
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.app.MetricReading

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class OverlayMetricViewTest {
    @Test fun growingReadingsStaySingleLineWithoutMovingUnitsOrChangingBounds() {
        val c=RuntimeEnvironment.getApplication()
        for(scale in listOf(1f,1.3f,2f)) {
            val configuration=android.content.res.Configuration(c.resources.configuration).apply { fontScale=scale }
            val localized=c.createConfigurationContext(configuration)
            val cell=OverlayMetricView(localized,android.graphics.Color.BLACK,android.graphics.Color.GRAY,false)
            val density=localized.resources.displayMetrics.density
            val width=(100*density).toInt(); val height=(56*density).toInt()
            var unitLeft=-1
            for(number in listOf("0.0","999.9","99999.9")) {
                cell.bind(MetricReading("Energy",number,"kcal",true))
                cell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                cell.layout(0,0,width,height)
                assertEquals(width,cell.width); assertEquals(height,cell.height)
                assertEquals("Energy ≈",cell.label.text.toString()); assertFalse(cell.value.text.contains("≈"))
                assertEquals(1,cell.value.lineCount)
                if(unitLeft<0) unitLeft=cell.unit.left else assertEquals(unitLeft,cell.unit.left)
                assertTrue("$number at font scale $scale must fit",cell.value.layout.getLineWidth(0)<=cell.value.width+1)
            }
        }
    }
}
