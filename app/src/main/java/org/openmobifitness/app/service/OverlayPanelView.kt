package org.openmobifitness.app.service

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import org.openmobifitness.app.MetricReading
import org.openmobifitness.app.R

internal enum class OverlayAction { EXPAND, COLLAPSE, METRICS, OPEN, PAUSE, MINUS, PLUS }

internal data class OverlayPalette(val surface: Int,val ink: Int,val muted: Int,val line: Int,val track: Int,val primary: Int,val onPrimary: Int,val soft: Int) {
    companion object {
        fun create(dark: Boolean)=if(dark) OverlayPalette(0xFF1C2129.toInt(),0xFFF1F4FA.toInt(),0xFFB8C2D1.toInt(),0xFF3C4552.toInt(),0xFF394250.toInt(),0xFFA9C7FF.toInt(),0xFF092D60.toInt(),0xFF2C3441.toInt())
        else OverlayPalette(Color.WHITE,0xFF192538.toInt(),0xFF536279.toInt(),0xFFDCE3ED.toInt(),0xFFE1E8F1.toInt(),0xFF0073E6.toInt(),Color.WHITE,0xFFEDF1F7.toInt())
    }
}

/** Native overlay, independent of the Activity. Only the central information area can scroll. */
internal class OverlayPanelView(context: Context,val expanded: Boolean,dark: Boolean,private val action: (OverlayAction)->Unit): LinearLayout(context) {
    private val palette=OverlayPalette.create(dark)
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private val cells=mutableListOf<OverlayMetricView>()
    val dragHandle: View
    private val statusDot=View(context)
    private val statusLabel=text(10,8)
    private val stageLabel=text(11,8,bold=true)
    private val remaining=text(if(expanded) 21 else 11,8,numeric=true,bold=expanded).apply { if(expanded) setTextColor(palette.ink) }
    private val elapsed=if(expanded) OverlayMetricView(context,palette.ink,palette.muted,true,32) else null
    private val progress=OverlayProgress(context,palette.track,palette.primary)
    private var resistanceValue: TextView?=null
    private var resistancePercent: TextView?=null
    private var resistanceMode: TextView?=null
    private var minus: View?=null
    private var plus: View?=null
    private var pause: ActionButton?=null
    private var lastPresentation: OverlayPresentation?=null

    init {
        orientation=VERTICAL
        setPadding(dp(12),dp(if(expanded) 12 else 8),dp(12),dp(if(expanded) 12 else 8))
        background=shape(palette.surface,16,palette.line)
        clipToOutline=true
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription=context.getString(if(expanded) R.string.expanded_panel else R.string.expand_panel)
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val brand=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        brand.addView(OverlayBrand(context),LayoutParams(dp(20),dp(18)))
        brand.addView(text(12,10,bold=true).apply { text="OpenMOBI"; setTextColor(palette.ink) },LayoutParams(dp(75),MATCH).apply { marginStart=dp(5) })
        statusDot.background=shape(palette.primary,20)
        brand.addView(statusDot,LayoutParams(dp(5),dp(5)).apply { marginStart=dp(2); marginEnd=dp(4) })
        brand.addView(statusLabel,LayoutParams(0,MATCH,1f))
        header.addView(brand,LayoutParams(0,MATCH,1f))
        dragHandle=brand
        if(expanded) {
            header.addView(iconButton(OverlayGlyph.TUNE,R.string.choose_metrics,OverlayAction.METRICS),LayoutParams(dp(48),dp(48)))
            header.addView(iconButton(OverlayGlyph.COLLAPSE,R.string.collapse_panel,OverlayAction.COLLAPSE),LayoutParams(dp(48),dp(48)))
        } else {
            header.addView(ImageView(context).apply { setImageDrawable(OverlayIcon(OverlayGlyph.EXPAND,palette.ink)); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; setPadding(dp(2),dp(2),dp(2),dp(2)) },LayoutParams(dp(20),dp(20)))
            isClickable=true; setOnClickListener { action(OverlayAction.EXPAND) }
        }
        addView(header,LayoutParams(MATCH,dp(if(expanded) 48 else 24)))
        if(expanded) buildExpanded() else buildCompact()
    }

    private fun buildCompact() {
        addView(metricRow(0,false),LayoutParams(MATCH,dp(56)))
        addView(progress,LayoutParams(MATCH,dp(3)).apply { topMargin=dp(4); bottomMargin=dp(1) })
        val footer=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        footer.addView(stageLabel,LayoutParams(0,MATCH,1f))
        remaining.gravity=Gravity.END or Gravity.CENTER_VERTICAL
        footer.addView(remaining,LayoutParams(0,MATCH,1.12f).apply { marginStart=dp(5) })
        addView(footer,LayoutParams(MATCH,dp(24)))
    }

    private fun buildExpanded() {
        val information=LinearLayout(context).apply { orientation=VERTICAL }
        val hero=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        hero.addView(elapsed,LayoutParams(0,dp(56),1.06f))
        hero.addView(divider(),LayoutParams(dp(1),dp(42)).apply { marginStart=dp(6); marginEnd=dp(12) })
        val stage=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        stage.addView(stageLabel,LayoutParams(MATCH,dp(18)))
        stage.addView(remaining,LayoutParams(MATCH,dp(34)))
        hero.addView(stage,LayoutParams(0,MATCH,1f))
        information.addView(hero,LayoutParams(MATCH,dp(60)))
        information.addView(progress,LayoutParams(MATCH,dp(4)).apply { topMargin=dp(7); bottomMargin=dp(7) })
        information.addView(divider(),LayoutParams(MATCH,dp(1)))
        information.addView(metricRow(0,true),LayoutParams(MATCH,dp(53)))
        information.addView(divider(),LayoutParams(MATCH,dp(1)))
        information.addView(metricRow(2,true),LayoutParams(MATCH,dp(53)))
        val scroll=ScrollView(context).apply {
            isFillViewport=false; isVerticalScrollBarEnabled=true; isScrollbarFadingEnabled=false
            scrollBarSize=dp(2).coerceAtLeast(1); verticalScrollbarThumbDrawable=shape(palette.muted,2)
            overScrollMode=OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(information,FrameLayout.LayoutParams(MATCH,WRAP))
        }
        addView(scroll,LayoutParams(MATCH,0,1f))

        // These controls stay outside the scrolling region, including short landscape windows.
        val resistance=LinearLayout(context).apply { orientation=VERTICAL }
        resistance.addView(divider(),LayoutParams(MATCH,dp(1)))
        val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        minus=iconButton(OverlayGlyph.MINUS,R.string.decrease,OverlayAction.MINUS,true)
        plus=iconButton(OverlayGlyph.PLUS,R.string.increase,OverlayAction.PLUS,true)
        row.addView(minus,LayoutParams(dp(48),dp(48)))
        val level=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER }
        resistanceMode=text(10,8).apply { gravity=Gravity.CENTER }
        resistanceValue=text(24,12,true,true).apply { gravity=Gravity.CENTER; setTextColor(palette.ink) }
        resistancePercent=text(10,8,true).apply { gravity=Gravity.CENTER }
        level.addView(resistanceMode,LayoutParams(MATCH,dp(14)))
        level.addView(resistanceValue,LayoutParams(MATCH,dp(27)))
        level.addView(resistancePercent,LayoutParams(MATCH,dp(13)))
        row.addView(level,LayoutParams(0,dp(54),1f))
        row.addView(plus,LayoutParams(dp(48),dp(48)))
        resistance.addView(row,LayoutParams(MATCH,dp(55)))
        addView(resistance,LayoutParams(MATCH,dp(56)))
        val dock=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        pause=ActionButton(OverlayGlyph.PAUSE,context.getString(R.string.pause_short),true).apply { setOnClickListener { action(OverlayAction.PAUSE) } }
        dock.addView(pause,LayoutParams(0,dp(48),1f))
        dock.addView(ActionButton(OverlayGlyph.OPEN,context.getString(R.string.overlay_open),false).apply {
            contentDescription=context.getString(R.string.open_app); setOnClickListener { action(OverlayAction.OPEN) }
        },LayoutParams(0,dp(48),1f).apply { marginStart=dp(8) })
        addView(dock,LayoutParams(MATCH,dp(48)).apply { topMargin=dp(6) })
    }

    private fun metricRow(start: Int,large: Boolean)=LinearLayout(context).apply {
        gravity=Gravity.CENTER_VERTICAL
        repeat(2) { index ->
            if(index==1) addView(divider(),LayoutParams(dp(1),dp(38)).apply { marginStart=dp(5); marginEnd=dp(7) })
            val cell=OverlayMetricView(context,palette.ink,palette.muted,large)
            cell.tag="overlay-metric-${start+index}"
            cells.add(cell)
            addView(cell,LayoutParams(0,MATCH,if(!large && index==0) 1.15f else 1f))
        }
    }

    fun bind(model: OverlayPresentation) {
        if(model==lastPresentation) return
        lastPresentation=model
        cells.forEachIndexed { index,cell ->
            val reading=model.readings.getOrNull(index)
            cell.visibility=if(reading!=null) VISIBLE else INVISIBLE
            if(reading!=null) cell.bind(reading)
        }
        elapsed?.bind(MetricReading(context.getString(R.string.duration),model.elapsed,""))
        stageLabel.text=model.stage
        val countdown=model.countdownValue
        remaining.text=if(expanded && countdown!=null && model.remaining.contains(countdown)) SpannableString(model.remaining).apply {
            val begin=model.remaining.indexOf(countdown); val end=begin+countdown.length
            for((from,to) in listOf(0 to begin,end to length)) if(to>from) {
                setSpan(RelativeSizeSpan(.6f),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(palette.muted),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else model.remaining
        progress.progress=model.stageProgress
        statusLabel.text=model.statusText
        (statusDot.background as GradientDrawable).setColor(when(model.status) {
            OverlayStatus.ACTIVE -> 0xFF23945A.toInt()
            OverlayStatus.DEMO -> 0xFFC48E18.toInt()
            OverlayStatus.PAUSED -> palette.muted
            OverlayStatus.DISCONNECTED -> 0xFFDE535E.toInt()
        })
        resistanceMode?.text="${model.resistance.label} · ${model.controlMode}"
        resistanceValue?.text=model.resistance.value
        resistancePercent?.text=model.resistance.unit
        listOfNotNull(minus,plus).forEach { it.isEnabled=model.canAdjust; it.alpha=if(model.canAdjust) 1f else .35f }
        pause?.setState(if(model.paused) OverlayGlyph.PLAY else OverlayGlyph.PAUSE,context.getString(if(model.paused) R.string.resume else R.string.pause_short))
        // A non-focusable overlay can keep stale accessibility descendants even when the
        // pixels update. Invalidate that subtree without making ticking readings a live region.
        if(isAttachedToWindow) sendAccessibilityEventUnchecked(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            contentChangeTypes=AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        })
    }

    private fun text(size: Int,min: Int,numeric: Boolean=false,bold: Boolean=false)=TextView(context).apply {
        setTextColor(palette.muted); maxLines=1; includeFontPadding=false; ellipsize=TextUtils.TruncateAt.END
        gravity=Gravity.CENTER_VERTICAL
        typeface=Typeface.create(if(numeric) Typeface.MONOSPACE else Typeface.DEFAULT,if(bold) Typeface.BOLD else Typeface.NORMAL)
        setAutoSizeTextTypeUniformWithConfiguration(min,(size*resources.configuration.fontScale).toInt().coerceAtLeast(size),1,TypedValue.COMPLEX_UNIT_DIP)
    }
    private fun shape(fill: Int,radius: Int,border: Int?=null)=GradientDrawable().apply {
        setColor(fill); cornerRadius=dp(radius).toFloat(); if(border!=null) setStroke(dp(1).coerceAtLeast(1),border)
    }
    private fun divider()=View(context).apply { setBackgroundColor(palette.line); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun iconButton(glyph: OverlayGlyph,label: Int,command: OverlayAction,circle: Boolean=false)=ImageButton(context).apply {
        contentDescription=context.getString(label)
        setImageDrawable(OverlayIcon(glyph,palette.ink)); scaleType=ImageView.ScaleType.FIT_CENTER
        minimumWidth=0; minimumHeight=0
        val base=shape(if(circle) palette.soft else Color.TRANSPARENT,if(circle) 24 else 10)
        background=RippleDrawable(ColorStateList.valueOf(palette.track),if(circle) InsetDrawable(base,dp(4)) else base,null)
        // InsetDrawable installs its own padding; apply icon insets after the background.
        setPadding(dp(14),dp(14),dp(14),dp(14))
        setOnClickListener { action(command) }
    }
    private inner class ActionButton(glyph: OverlayGlyph,label: String,primary: Boolean): LinearLayout(context) {
        private val ink=if(primary) palette.onPrimary else palette.ink
        private val icon=ImageView(context).apply { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val caption=text(12,9,bold=true).apply { setTextColor(ink); gravity=Gravity.CENTER; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        init {
            gravity=Gravity.CENTER; isClickable=true; isFocusable=true
            setPadding(dp(8),0,dp(8),0)
            background=RippleDrawable(ColorStateList.valueOf(palette.track),shape(if(primary) palette.primary else palette.surface,10,if(primary) null else palette.line),null)
            addView(icon,LayoutParams(dp(16),dp(16)))
            addView(caption,LayoutParams(0,MATCH,1f).apply { marginStart=dp(6) })
            setState(glyph,label)
        }
        fun setState(glyph: OverlayGlyph,label: String) {
            if(caption.text.toString()==label) return
            icon.setImageDrawable(OverlayIcon(glyph,ink)); caption.text=label
            if(glyph==OverlayGlyph.PAUSE || glyph==OverlayGlyph.PLAY) contentDescription=label
        }
        override fun getAccessibilityClassName(): CharSequence=Button::class.java.name
    }
    companion object {
        private const val MATCH=LayoutParams.MATCH_PARENT
        private const val WRAP=LayoutParams.WRAP_CONTENT
        const val COMPACT_WIDTH=236
        const val COMPACT_HEIGHT=128
        const val EXPANDED_WIDTH=312
        const val EXPANDED_HEIGHT=368
    }
}
