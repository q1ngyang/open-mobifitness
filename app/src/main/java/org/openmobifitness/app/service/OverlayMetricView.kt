package org.openmobifitness.app.service

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.openmobifitness.app.MetricReading

/** A fixed-size cell: changing digits can only resize text, never the window or neighboring cells. */
internal class OverlayMetricView(context: Context,private val ink: Int,private val muted: Int,expanded: Boolean): LinearLayout(context) {
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    val label=TextView(context).apply {
        setTextColor(muted); maxLines=1; includeFontPadding=false; gravity=Gravity.CENTER_VERTICAL
        setAutoSizeTextTypeUniformWithConfiguration(8,(11*resources.configuration.fontScale).toInt().coerceAtLeast(11),1,TypedValue.COMPLEX_UNIT_DIP)
    }
    val value=TextView(context).apply {
        setTextColor(ink); typeface=Typeface.MONOSPACE; maxLines=1; includeFontPadding=false; gravity=Gravity.CENTER_VERTICAL
        setAutoSizeTextTypeUniformWithConfiguration(8,((if(expanded) 25 else 20)*resources.configuration.fontScale).toInt().coerceAtLeast(20),1,TypedValue.COMPLEX_UNIT_DIP)
    }
    val unit=TextView(context).apply {
        setTextColor(muted); maxLines=1; includeFontPadding=false; gravity=Gravity.CENTER_VERTICAL
        setAutoSizeTextTypeUniformWithConfiguration(8,(11*resources.configuration.fontScale).toInt().coerceAtLeast(11),1,TypedValue.COMPLEX_UNIT_DIP)
    }
    private var reading: MetricReading?=null
    init {
        orientation=VERTICAL; setPadding(dp(3),dp(2),dp(5),dp(2))
        addView(label,LayoutParams(LayoutParams.MATCH_PARENT,dp(18)))
        val row=LinearLayout(context).apply { orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(value,LayoutParams(0,dp(30),1f))
        row.addView(unit,LayoutParams(dp(32),dp(30)).apply { marginStart=dp(3) })
        addView(row,LayoutParams(LayoutParams.MATCH_PARENT,dp(30)))
    }
    fun bind(next: MetricReading) {
        if(next==reading) return
        reading=next
        val title=next.label+if(next.estimated) " ≈" else ""
        label.text=SpannableString(title).apply {
            if(next.estimated) setSpan(ForegroundColorSpan(android.graphics.Color.argb(145,android.graphics.Color.red(muted),android.graphics.Color.green(muted),android.graphics.Color.blue(muted))),next.label.length,title.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if(value.text.toString()!=next.value) value.text=next.value
        unit.text=next.unit
        unit.visibility=if(next.unit.isEmpty()) GONE else VISIBLE
        // Unit widths depend only on the unit, never on the number of digits in a reading.
        (unit.layoutParams as LayoutParams).apply { width=dp(if(next.unit=="km/h" || next.unit=="/500 m") 38 else 30) }.also { unit.layoutParams=it }
    }
}
