package org.openmobifitness.app.service

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil

/** Adjacent, baseline-aligned values. Reserve the widest digit shape seen for this metric,
 * not the cell's entire width. The reservation never shrinks as live readings fluctuate. */
internal class OverlayValueLine(context: Context,ink: Int,muted: Int,private val numberSp: Int,
    private val centered: Boolean=false): ViewGroup(context) {
    val value=TextView(context).apply {
        setTextColor(ink); typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)
        maxLines=1; includeFontPadding=false; gravity=Gravity.END; fontFeatureSettings="tnum"
    }
    val unit=TextView(context).apply {
        setTextColor(muted); maxLines=1; includeFontPadding=false; gravity=Gravity.START; fontFeatureSettings="tnum"
    }
    private var identity=""
    private var heldNumber=""
    private var heldUnit=""
    private var contentWidth=0
    private var gap=0

    init {
        addView(value,LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT))
        addView(unit,LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT))
    }

    fun bind(number: String,suffix: String,key: String) {
        if(identity!=key) { identity=key; heldNumber=""; heldUnit="" }
        value.text=number; unit.text=suffix
        unit.visibility=if(suffix.isEmpty()) GONE else VISIBLE
        requestLayout()
    }

    private fun sp(size: Int)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size.toFloat(),resources.displayMetrics)
    private fun widest(text: String,paint: Paint): String {
        // Do not assume monospace, tabular-digit support, or that '8' is the widest digit.
        val shape=text.map { if(it.isDigit()) it-it.digitToInt() else it }.joinToString("")
        return ((0..9).map { digit -> shape.map { if(it.isDigit()) it+digit else it }.joinToString("") }+text).maxBy { paint.measureText(it) }
    }
    private fun textHeight(paint: Paint)=ceil((paint.fontMetrics.descent-paint.fontMetrics.ascent).toDouble()).toInt()+1

    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val numberPaint=TextPaint(value.paint).apply { textSize=sp(numberSp) }
        val unitPaint=TextPaint(unit.paint).apply { textSize=sp(12) }
        heldNumber=listOf(heldNumber,widest(value.text.toString(),numberPaint)).maxBy { numberPaint.measureText(it) }
        heldUnit=listOf(heldUnit,widest(unit.text.toString(),unitPaint)).maxBy { unitPaint.measureText(it) }
        val showUnit=unit.visibility!=GONE
        gap=if(showUnit) ceil(4*resources.displayMetrics.density.toDouble()).toInt() else 0
        val naturalWidth=ceil(numberPaint.measureText(heldNumber)+(if(showUnit) unitPaint.measureText(heldUnit)+gap else 0f)).toInt()+2
        val width=resolveSize(naturalWidth,widthMeasureSpec)
        val height=resolveSize(maxOf(textHeight(numberPaint),textHeight(unitPaint)),heightMeasureSpec)
        val usableHeight=(height-1).coerceAtLeast(1)
        if(showUnit) {
            // Prefer 12 sp units; only unusually long readings in narrow cells need 10 sp.
            val minNumber=TextPaint(numberPaint).apply { textSize=sp(12) }
            val unitSize=(12 downTo 10).firstOrNull {
                unitPaint.textSize=sp(it)
                minNumber.measureText(heldNumber)+unitPaint.measureText(heldUnit)+gap+2<=width
            } ?: 10
            unitPaint.textSize=sp(unitSize)
            val ratio=minOf(1f,usableHeight.toFloat()/textHeight(unitPaint),((width-gap).coerceAtLeast(1)*.6f)/unitPaint.measureText(heldUnit).coerceAtLeast(1f))
            unitPaint.textSize*=ratio
        }
        val unitWidth=if(showUnit) ceil(unitPaint.measureText(heldUnit).toDouble()).toInt()+1 else 0
        val room=(width-unitWidth-gap-1).coerceAtLeast(1)
        numberPaint.textSize*=minOf(1f,room/numberPaint.measureText(heldNumber).coerceAtLeast(1f),usableHeight.toFloat()/textHeight(numberPaint))
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX,numberPaint.textSize)
        unit.setTextSize(TypedValue.COMPLEX_UNIT_PX,unitPaint.textSize)
        val numberWidth=ceil(numberPaint.measureText(heldNumber).toDouble()).toInt().coerceAtMost(room)
        value.measure(MeasureSpec.makeMeasureSpec(numberWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST))
        unit.measure(MeasureSpec.makeMeasureSpec(unitWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST))
        contentWidth=numberWidth+gap+unitWidth
        setMeasuredDimension(width,height)
    }

    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        val baseline=maxOf(value.baseline,if(unit.visibility==VISIBLE) unit.baseline else 0)
        val below=maxOf(value.measuredHeight-value.baseline,if(unit.visibility==VISIBLE) unit.measuredHeight-unit.baseline else 0)
        val top=((height-baseline-below)/2).coerceAtLeast(0)
        val left=if(centered) ((width-contentWidth)/2).coerceAtLeast(0) else 0
        val valueTop=top+baseline-value.baseline
        value.layout(left,valueTop,left+value.measuredWidth,valueTop+value.measuredHeight)
        val unitLeft=left+value.measuredWidth+gap
        val unitTop=top+baseline-unit.baseline
        unit.layout(unitLeft,unitTop,unitLeft+unit.measuredWidth,unitTop+unit.measuredHeight)
    }
}
