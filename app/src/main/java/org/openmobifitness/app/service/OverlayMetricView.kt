package org.openmobifitness.app.service

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.openmobifitness.app.MetricReading

/** A fixed-size cell: changing digits can only resize text, never the window or neighboring cells. */
internal class OverlayMetricView(context: Context,private val ink: Int,private val muted: Int,expanded: Boolean,numberSize: Int=if(expanded) 25 else 30): LinearLayout(context) {
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun textHeight(value: Int)=dp((value*resources.configuration.fontScale).toInt())
    val label=TextView(context).apply {
        setTextColor(muted); maxLines=2; includeFontPadding=false; gravity=Gravity.CENTER_VERTICAL
        setAutoSizeTextTypeUniformWithConfiguration((10*resources.configuration.fontScale).toInt(),(11*resources.configuration.fontScale).toInt().coerceAtLeast(11),1,TypedValue.COMPLEX_UNIT_DIP)
    }
    private val line=OverlayValueLine(context,ink,muted,numberSize)
    val value get()=line.value
    val unit get()=line.unit
    private var reading: MetricReading?=null
    init {
        orientation=VERTICAL; setPadding(dp(3),dp(2),dp(5),dp(2))
        addView(label,LayoutParams(LayoutParams.MATCH_PARENT,textHeight(if(resources.configuration.fontScale>=1.5f) 24 else 18)))
        val numberHeight=if(numberSize>=30) 34 else 30
        addView(line,LayoutParams(LayoutParams.MATCH_PARENT,textHeight(numberHeight)))
    }
    fun bind(next: MetricReading) {
        if(next==reading) return
        reading=next
        val title=next.label+if(next.estimated) " ≈" else ""
        label.text=SpannableString(title).apply {
            if(next.estimated) setSpan(ForegroundColorSpan(android.graphics.Color.argb(145,android.graphics.Color.red(muted),android.graphics.Color.green(muted),android.graphics.Color.blue(muted))),next.label.length,title.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        line.bind(next.value,next.unit,next.label+"|"+next.unit.filterNot(Char::isDigit))
    }
}
