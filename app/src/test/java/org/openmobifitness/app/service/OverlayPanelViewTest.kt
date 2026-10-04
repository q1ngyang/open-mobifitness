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
import org.robolectric.annotation.GraphicsMode
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
            for(heightDp in if(expanded) listOf(416,240) else listOf(192+(100*(scale-1)).toInt())) {
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
                    // In short/large-font windows the resistance area scrolls; the dock stays fixed.
                    val parent=generateSequence(button.parent) { it.parent }.filterIsInstance<android.widget.ScrollView>().firstOrNull()
                    if(parent!=null) {
                        val target=Rect(0,0,button.width,button.height); parent.offsetDescendantRectToMyCoords(button,target)
                        parent.scrollTo(0,(target.bottom-parent.height).coerceAtLeast(0))
                    }
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

    @Test fun contentHeightTracksMetricRowsAndDoesNotTrackChangingValues() {
        val model=sample().copy(controlOnly=true,stageProgress=null,userName="林",readings=List(6) { MetricReading("Power","123","W") })
        val heights=listOf(2,4,6).map { overlayContentHeight(true,model.copy(readings=model.readings.take(it)),1f) }
        assertTrue(heights.zipWithNext().all { (a,b) -> b-a==57 })
        assertEquals(heights.last(),overlayContentHeight(true,model.copy(readings=model.readings.map { it.copy(value="99999") }),1f))
        assertTrue(overlayContentHeight(true,model.copy(controlOnly=false),1f)>heights.last())
        assertTrue(overlayContentHeight(true,model.copy(showResistance=false),1f)<heights.last())
        assertTrue(overlayContentHeight(true,model,2f)>heights.last())
        val c=RuntimeEnvironment.getApplication()
        val view=OverlayPanelView(c,true,false) {}
        view.bind(model); val d=c.resources.displayMetrics.density
        measure(view,(312*d).toInt(),(heights.last()*d).toInt())
        assertEquals(6,children(view).count { it.tag?.toString()?.startsWith("overlay-metric-")==true && it.visibility==View.VISIBLE })
        val name=children(view).filterIsInstance<TextView>().single { it.text.toString()=="林" }
        val plus=children(view).first { it.contentDescription==c.getString(R.string.increase) }
        assertEquals((48*d).toInt(),plus.height)
        assertTrue(name.width>0)
        val large=c.createConfigurationContext(Configuration(c.resources.configuration).apply { fontScale=2f })
        val stacked=OverlayPanelView(large,true,false) {}
        stacked.bind(model.copy(readings=model.readings.take(3)))
        assertEquals("An odd metric count does not reserve an empty large-font row",View.GONE,stacked.findViewWithTag<View>("overlay-metric-3").visibility)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun brandAndUnrecordedStateRemainReadableAtLargeFonts() {
        val app=RuntimeEnvironment.getApplication()
        for(tag in listOf("en","de")) for(scale in listOf(1f,2f)) for(expanded in listOf(false,true)) {
            val c=app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)); fontScale=scale })
            val model=sample().copy(readings=sample().readings.take(2),controlOnly=true,stageProgress=null,
                userName="林 Overlay",stage=c.getString(R.string.control_only),remaining=c.getString(R.string.not_recording),
                resistance=MetricReading(c.getString(R.string.resistance),"24","100%"),controlMode=c.getString(R.string.overlay_manual))
            val view=OverlayPanelView(c,expanded,false) {};view.bind(model)
            val density=c.resources.displayMetrics.density
            val width=((if(expanded) 312 else 236)+(120*(scale-1)).toInt()).coerceAtMost(374)
            measure(view,(width*density).toInt(),(overlayContentHeight(expanded,model,scale)*density).toInt())
            val expected=mutableListOf("OpenMOBI",c.getString(R.string.control_only),(if(expanded) "● " else "")+c.getString(R.string.not_recording))
            if(expanded) expected+=listOf(c.getString(R.string.resistance),c.getString(R.string.overlay_manual),"24","100%")
            for(value in expected) {
                val label=children(view).filterIsInstance<TextView>().first { candidate -> candidate.text.toString()==value && generateSequence<View>(candidate) { it.parent as? View }.all { it.visibility==View.VISIBLE } }
                assertTrue("$tag at $scale, expanded=$expanded: $value has a visible line",label.lineCount>0)
                for(line in 0 until label.lineCount) assertEquals("$tag at $scale must preserve $value",0,label.layout.getEllipsisCount(line))
                if(value==c.getString(R.string.overlay_manual)) assertEquals("A mode word stays on one line",1,label.lineCount)
                assertTrue("$tag at $scale: $value fits vertically (layout=${label.layout.height}, height=${label.height}, padding=${label.paddingTop+label.paddingBottom}, text=${label.textSize})",label.layout.height<=label.height-label.paddingTop-label.paddingBottom)
            }
        }
    }

    @Test fun durationUsesMinutesUntilAnHourThenRetainsAllHours() {
        assertEquals("00:00",overlayClock(0)); assertEquals("59:59",overlayClock(3_599_000))
        assertEquals("01:00:00",overlayClock(3_600_000)); assertEquals("100:00:00",overlayClock(360_000_000))
    }
}
