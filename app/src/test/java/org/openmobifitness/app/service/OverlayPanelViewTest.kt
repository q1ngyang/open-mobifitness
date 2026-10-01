package org.openmobifitness.app.service

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.openmobifitness.app.*
import org.openmobifitness.core.MetricId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class OverlayPanelViewTest {
    private fun children(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    private fun measure(v: View,w: Int,h: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); v.layout(0,0,w,h)
    }
    private fun sample()=OverlayPresentation(
        listOf(MetricReading("Active time","99:59:59",""),MetricReading("Energy","99999.9","kcal",true),MetricReading("Distance","9999.99","km",true),MetricReading("Heart rate","128","bpm")),
        "99:59:59","Stage 12 / 24","24m 59s left",.5f,MetricReading("Resistance","12","48%"),"Auto","Demo",OverlayStatus.DEMO,false,true)

    @Test fun shortWindowsKeepControlsVisibleAndLongReadingsDoNotResizeThePanel() {
        val app=RuntimeEnvironment.getApplication()
        for(scale in listOf(1f,1.3f,2f)) for(expanded in listOf(false,true)) for(dark in listOf(false,true)) {
            val c=app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale=scale })
            val density=c.resources.displayMetrics.density
            val width=((if(expanded) 304 else 236)*density).toInt()
            for(heightDp in if(expanded) listOf(368,240) else listOf(128)) {
                val height=(heightDp*density).toInt()
                val view=OverlayPanelView(c,expanded,dark) {}
                val model=sample().let { if(expanded) it else it.copy(readings=it.readings.take(2)) }
                view.bind(model); measure(view,width,height)
                assertEquals(width,view.width); assertEquals(height,view.height)
                val elapsed=children(view).filterIsInstance<TextView>().first { it.text.toString()=="99:59:59" }
                assertEquals(1,elapsed.lineCount)
                assertTrue("Elapsed time must fit at font scale $scale",elapsed.layout.getLineWidth(0)<=elapsed.width+1)
                if(expanded) for(id in listOf(R.string.open_app,R.string.increase,R.string.decrease,R.string.pause_short,R.string.choose_metrics,R.string.collapse_panel)) {
                    val button=children(view).first { it.contentDescription==c.getString(id) }
                    val bounds=Rect(0,0,button.width,button.height); view.offsetDescendantRectToMyCoords(button,bounds)
                    assertTrue("$id remains inside a $heightDp dp panel",bounds.top>=0 && bounds.bottom<=height && bounds.left>=0 && bounds.right<=width)
                    assertTrue("Controls retain a 48 dp height",button.height>=(48*density).toInt())
                }
                view.bind(model.copy(readings=model.readings.map { it.copy(value="1") })); measure(view,width,height)
                assertEquals(width,view.width); assertEquals(height,view.height)
            }
        }
    }

    @Test fun disconnectedControlsAreDisabledAndPauseOpenAndCustomizeAreSeparateActions() {
        val c=RuntimeEnvironment.getApplication()
        val actions=mutableListOf<OverlayAction>()
        val view=OverlayPanelView(c,true,false,actions::add)
        view.bind(sample().copy(canAdjust=false,status=OverlayStatus.DISCONNECTED,statusText="Disconnected"))
        for(id in listOf(R.string.increase,R.string.decrease)) assertFalse(children(view).first { it.contentDescription==c.getString(id) }.isEnabled)
        for(id in listOf(R.string.pause_short,R.string.open_app,R.string.choose_metrics,R.string.collapse_panel)) children(view).first { it.contentDescription==c.getString(id) }.performClick()
        assertEquals(listOf(OverlayAction.PAUSE,OverlayAction.OPEN,OverlayAction.METRICS,OverlayAction.COLLAPSE),actions)
        view.bind(sample().copy(paused=true)); assertNotNull(children(view).firstOrNull { it.contentDescription==c.getString(R.string.resume) })
    }

    @Test fun usefulDefaultsDoNotReplaceSavedMetricSelections() {
        val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("overlay-defaults-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(listOf(MetricId.TIME,MetricId.RESISTANCE),DisplayPreferences(prefs).selections.value.getValue(DisplayScope.COMPACT))
        DisplayPreferences(prefs).save(DisplayScope.COMPACT,listOf(MetricId.CADENCE,MetricId.CALORIES))
        assertEquals(listOf(MetricId.CADENCE,MetricId.CALORIES),DisplayPreferences(prefs).selections.value.getValue(DisplayScope.COMPACT))
        prefs.edit().clear().commit()
    }

    @Test fun sixLanguagesKeepActionLabelsAndTheCountdownCompleteAtLargeFonts() {
        val app=RuntimeEnvironment.getApplication()
        for(tag in listOf("zh-Hans","zh-Hant","en","ja","ko","de")) {
            val c=app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)); fontScale=1.3f })
            val view=OverlayPanelView(c,true,false) {}
            val countdown=c.getString(R.string.minutes_seconds,99,59)
            view.bind(sample().copy(stage=c.getString(R.string.stage_format,12,24),remaining=c.getString(R.string.overlay_remaining,countdown),countdownValue=countdown))
            val density=c.resources.displayMetrics.density
            measure(view,(304*density).toInt(),(368*density).toInt())
            for(id in listOf(R.string.pause_short,R.string.overlay_open)) {
                val label=children(view).filterIsInstance<TextView>().first { it.text.toString()==c.getString(id) }
                assertEquals("The complete $tag action label fits",0,label.layout.getEllipsisCount(0))
            }
            val remaining=children(view).filterIsInstance<TextView>().first { it.text.toString()==c.getString(R.string.overlay_remaining,countdown) }
            assertEquals("The complete $tag countdown fits",0,remaining.layout.getEllipsisCount(0))
        }
    }

    @Test fun durationUsesMinutesUntilAnHourThenRetainsAllHours() {
        assertEquals("00:00",overlayClock(0)); assertEquals("59:59",overlayClock(3_599_000))
        assertEquals("01:00:00",overlayClock(3_600_000)); assertEquals("100:00:00",overlayClock(360_000_000))
    }
}
