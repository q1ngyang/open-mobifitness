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

internal enum class OverlayAction { EXPAND, COLLAPSE, METRICS, OPEN, PAUSE, RECONNECT, MINUS, PLUS }

internal data class OverlayPalette(val surface: Int,val ink: Int,val muted: Int,val line: Int,val track: Int,val primary: Int,val onPrimary: Int,val soft: Int) {
    companion object {
        fun create(dark: Boolean)=if(dark) OverlayPalette(0xFF1C2129.toInt(),0xFFF1F4FA.toInt(),0xFFB8C2D1.toInt(),0xFF3C4552.toInt(),0xFF394250.toInt(),0xFFA9C7FF.toInt(),0xFF092D60.toInt(),0xFF2C3441.toInt())
        else OverlayPalette(Color.WHITE,0xFF192538.toInt(),0xFF536279.toInt(),0xFFDCE3ED.toInt(),0xFFE1E8F1.toInt(),0xFF0062CC.toInt(),Color.WHITE,0xFFEDF1F7.toInt())
    }
}

/** Native overlay, independent of the Activity. Only the central information area can scroll. */
internal class OverlayPanelView(context: Context,val expanded: Boolean,dark: Boolean,private val action: (OverlayAction)->Unit): LinearLayout(context) {
    private val palette=OverlayPalette.create(dark)
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    private fun textHeight(n: Int)=dp((n*resources.configuration.fontScale).toInt())
    private val largeFont get()=resources.configuration.fontScale>=1.5f
    private val footerHeight get()=textHeight(if(largeFont) 28 else 24)
    private val cells=mutableListOf<OverlayMetricView>()
    val dragHandle: View
    private val statusLabel=text(10,8)
    private val userName=text(11,9)
    private val recordingState=text(10,8)
    private val metricRows=mutableListOf<View>()
    private val metricDividers=mutableListOf<Pair<Int,View>>()
    private val separators=mutableListOf<View>()
    private val pendingLabel=text(10,9)
    private val stageLabel=text(11,8,bold=true)
    private val remaining=text(if(expanded) 21 else 11,8,numeric=true,bold=expanded).apply { if(expanded) setTextColor(palette.ink) }
    private val elapsed=if(expanded) OverlayMetricView(context,palette.ink,palette.muted,true,32) else null
    private val progress=OverlayProgress(context,palette.track,palette.primary)
    private var resistanceReading: OverlayValueLine?=null
    private var resistanceMode: TextView?=null
    private var resistanceTitle: TextView?=null
    private var resistanceBlock: View?=null
    private var minus: View?=null
    private var plus: View?=null
    private var pause: ActionButton?=null
    private var lastPresentation: OverlayPresentation?=null
    private var hero: View?=null

    init {
        orientation=VERTICAL
        setPadding(dp(12),dp(if(expanded) 12 else 8),dp(12),dp(if(expanded) 12 else 8))
        background=shape(palette.surface,16,palette.line)
        clipToOutline=true
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription=context.getString(if(expanded) R.string.expanded_panel else R.string.expand_panel)
        addView(View(context).apply { background=shape(palette.line,2); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(dp(28),dp(3)).apply { gravity=Gravity.CENTER_HORIZONTAL; bottomMargin=dp(4) })
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        val brand=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        brand.addView(OverlayBrand(context),LayoutParams(dp(20),dp(18)))
        brand.addView(text(12,10,bold=true).apply {
            text="OpenMOBI"; setTextColor(palette.ink)
            // The wordmark is a bounded brand asset; reserve room for its full name.
            setAutoSizeTextTypeUniformWithConfiguration(if(largeFont) 12 else 10,if(largeFont) 16 else 12,1,TypedValue.COMPLEX_UNIT_DIP)
        },LayoutParams(0,MATCH,1f).apply { marginStart=dp(5) })
        header.addView(brand,LayoutParams(dp(if(largeFont) 110 else 94),MATCH))
        userName.gravity=Gravity.END or Gravity.CENTER_VERTICAL
        header.addView(userName,LayoutParams(0,MATCH,1f).apply { marginStart=dp(3) })
        dragHandle=brand
        if(expanded) {
            header.addView(iconButton(OverlayGlyph.TUNE,R.string.choose_metrics,OverlayAction.METRICS),LayoutParams(dp(48),dp(48)))
            header.addView(iconButton(OverlayGlyph.COLLAPSE,R.string.collapse_panel,OverlayAction.COLLAPSE),LayoutParams(dp(48),dp(48)))
        } else {
            header.addView(iconButton(OverlayGlyph.EXPAND,R.string.expand_panel,OverlayAction.EXPAND),LayoutParams(dp(48),dp(48)))
            isClickable=true; setOnClickListener { action(OverlayAction.EXPAND) }
        }
        addView(header,LayoutParams(MATCH,dp(48)))
        if(expanded) buildExpanded() else buildCompact()
    }

    private fun buildCompact() {
        val metrics=metricRow(0,false)
        if(resources.configuration.fontScale>=1.5f) {
            val scroll=ScrollView(context).apply { addView(metrics,FrameLayout.LayoutParams(MATCH,WRAP)); isVerticalScrollBarEnabled=true }
            addView(scroll,LayoutParams(MATCH,0,1f))
        } else addView(metrics,LayoutParams(MATCH,0,1f))
        addView(progress,LayoutParams(MATCH,dp(3)).apply { topMargin=dp(4); bottomMargin=dp(1) })
        val footer=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        footer.addView(stageLabel,LayoutParams(WRAP,MATCH))
        footer.addView(View(context),LayoutParams(0,1,1f))
        remaining.gravity=Gravity.END or Gravity.CENTER_VERTICAL
        footer.addView(remaining,LayoutParams(WRAP,MATCH).apply { marginStart=dp(5) })
        addView(footer,LayoutParams(MATCH,footerHeight).apply { topMargin=dp(5) })
    }

    private fun buildExpanded() {
        val information=LinearLayout(context).apply { orientation=VERTICAL }
        val hero=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        hero.addView(elapsed,LayoutParams(0,textHeight(56),1.06f))
        hero.addView(divider(),LayoutParams(dp(1),dp(42)).apply { marginStart=dp(6); marginEnd=dp(12) })
        val stage=LinearLayout(context).apply { orientation=VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        stage.addView(stageLabel,LayoutParams(MATCH,textHeight(18)))
        stage.addView(remaining,LayoutParams(MATCH,textHeight(34)))
        hero.addView(stage,LayoutParams(0,MATCH,1f))
        information.addView(hero,LayoutParams(MATCH,textHeight(60)))
        this.hero=hero
        information.addView(progress,LayoutParams(MATCH,dp(4)).apply { topMargin=dp(7); bottomMargin=dp(7) })
        repeat(3) { index ->
            val line=divider(); separators.add(line)
            information.addView(line,LayoutParams(MATCH,dp(1)))
            val row=metricRow(index*2,true); metricRows.add(row)
            information.addView(row,LayoutParams(MATCH,WRAP))
        }
        val scroll=ScrollView(context).apply {
            isFillViewport=false; isVerticalScrollBarEnabled=true; isScrollbarFadingEnabled=false
            scrollBarSize=dp(2).coerceAtLeast(1); verticalScrollbarThumbDrawable=shape(palette.muted,2)
            overScrollMode=OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(information,FrameLayout.LayoutParams(MATCH,WRAP))
        }
        addView(scroll,LayoutParams(MATCH,0,1f))

        // The action dock stays fixed. Readings and resistance can scroll in short or large-font windows.
        val resistance=LinearLayout(context).apply { orientation=VERTICAL }
        resistanceBlock=resistance
        resistance.addView(divider(),LayoutParams(MATCH,dp(1)))
        val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        minus=iconButton(OverlayGlyph.MINUS,R.string.decrease,OverlayAction.MINUS,true)
        plus=iconButton(OverlayGlyph.PLUS,R.string.increase,OverlayAction.PLUS,true)
        resistanceTitle=text(11,9).apply { setTextColor(palette.ink) }
        resistanceMode=text(10,7).apply { gravity=Gravity.CENTER; background=shape(palette.soft,14); setTextColor(palette.primary); setPadding(dp(5),0,dp(5),0) }
        resistanceReading=OverlayValueLine(context,palette.ink,palette.muted,25,centered=true)
        row.addView(resistanceTitle,LayoutParams(0,textHeight(30),1.6f))
        row.addView(resistanceMode,LayoutParams(0,textHeight(26),1.1f).apply { marginEnd=dp(3) })
        row.addView(minus,LayoutParams(dp(48),dp(48)))
        row.addView(resistanceReading,LayoutParams(0,textHeight(36),1.55f))
        row.addView(plus,LayoutParams(dp(48),dp(48)))
        resistance.addView(row,LayoutParams(MATCH,maxOf(dp(52),textHeight(36))))
        pendingLabel.maxLines=2
        resistance.addView(pendingLabel,LayoutParams(MATCH,textHeight(28)))
        information.addView(resistance,LayoutParams(MATCH,WRAP))
        val footer=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        footer.addView(statusLabel,LayoutParams(WRAP,MATCH))
        footer.addView(View(context),LayoutParams(0,1,1f))
        footer.addView(recordingState,LayoutParams(WRAP,MATCH))
        addView(footer,LayoutParams(MATCH,footerHeight).apply { topMargin=dp(4) })
        val dock=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        pause=ActionButton(OverlayGlyph.PAUSE,context.getString(R.string.pause_short),true).apply { setOnClickListener { action(if(lastPresentation?.canResume==false) OverlayAction.RECONNECT else OverlayAction.PAUSE) } }
        dock.addView(pause,LayoutParams(0,MATCH,1f))
        dock.addView(ActionButton(OverlayGlyph.OPEN,context.getString(R.string.overlay_open),false).apply {
            contentDescription=context.getString(R.string.open_app); setOnClickListener { action(OverlayAction.OPEN) }
        },LayoutParams(0,MATCH,1f).apply { marginStart=dp(8) })
        addView(dock,LayoutParams(MATCH,dp(48)).apply { topMargin=dp(6) })
    }

    private fun metricRow(start: Int,large: Boolean)=LinearLayout(context).apply {
        val stacked=resources.configuration.fontScale>=1.5f
        orientation=if(stacked) VERTICAL else HORIZONTAL
        gravity=Gravity.CENTER_VERTICAL
        repeat(2) { index ->
            if(index==1) {
                val line=divider();metricDividers.add(start to line)
                addView(line,if(stacked) LayoutParams(MATCH,dp(1)) else LayoutParams(dp(1),textHeight(38)).apply { marginStart=dp(5); marginEnd=dp(7) })
            }
            val cell=OverlayMetricView(context,palette.ink,palette.muted,large)
            cell.tag="overlay-metric-${start+index}"
            cells.add(cell)
            addView(cell,if(stacked) LayoutParams(MATCH,textHeight(64)) else LayoutParams(0,textHeight(56),if(!large && index==0) 1.15f else 1f))
        }
    }

    fun bind(model: OverlayPresentation) {
        if(model==lastPresentation) return
        lastPresentation=model
        cells.forEachIndexed { index,cell ->
            val reading=model.readings.getOrNull(index)
            cell.visibility=if(reading!=null) VISIBLE else if(resources.configuration.fontScale>=1.5f) GONE else INVISIBLE
            if(reading!=null) cell.bind(reading)
        }
        metricDividers.forEach { (start,line) -> line.visibility=if(model.readings.size>start+1) VISIBLE else GONE }
        userName.text=model.userName
        metricRows.forEachIndexed { index,row ->
            row.visibility=if(model.readings.size>index*2) VISIBLE else GONE
            separators[index].visibility=row.visibility
        }
        elapsed?.bind(MetricReading(context.getString(R.string.duration),model.elapsed,""))
        hero?.visibility=if(model.controlOnly) GONE else VISIBLE
        stageLabel.text=if(!expanded && model.status==OverlayStatus.DISCONNECTED) model.statusText else model.stage
        val countdown=model.countdownValue
        remaining.text=if(expanded && countdown!=null && model.remaining.contains(countdown)) SpannableString(model.remaining).apply {
            val begin=model.remaining.indexOf(countdown); val end=begin+countdown.length
            for((from,to) in listOf(0 to begin,end to length)) if(to>from) {
                setSpan(RelativeSizeSpan(.6f),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(palette.muted),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else model.remaining
        progress.progress=model.stageProgress
        progress.visibility=if(model.stageProgress==null) GONE else VISIBLE
        statusLabel.text=if(model.controlOnly && model.status!=OverlayStatus.DISCONNECTED) context.getString(R.string.control_only) else model.statusText
        recordingState.text=if(model.controlOnly) "● "+context.getString(R.string.not_recording) else ""
        recordingState.visibility=if(model.controlOnly) VISIBLE else GONE
        stylePill(statusLabel,false)
        stylePill(recordingState,true)
        if(!expanded) {
            stylePill(stageLabel,false)
            stylePill(remaining,model.controlOnly || model.status==OverlayStatus.DISCONNECTED)
        }
        resistanceBlock?.visibility=if(model.showResistance) VISIBLE else GONE
        resistanceTitle?.text=model.resistance.label
        resistanceMode?.maxLines=if(model.controlMode in setOf(context.getString(R.string.overlay_manual),context.getString(R.string.overlay_auto))) 1 else 2
        resistanceMode?.text=model.controlMode
        resistanceReading?.bind(model.resistance.value,model.resistance.unit,"resistance")
        pendingLabel.text=model.pending
        pendingLabel.visibility=if(model.pending.isEmpty()) GONE else VISIBLE
        listOfNotNull(minus,plus).forEach { it.isEnabled=model.canAdjust; it.alpha=if(model.canAdjust) 1f else .35f }
        pause?.setState(if(model.paused || model.controlOnly || !model.canResume) OverlayGlyph.PLAY else OverlayGlyph.PAUSE,context.getString(if(!model.canResume) R.string.reconnect else if(model.controlOnly) R.string.start_recording else if(model.paused) R.string.resume else R.string.pause_short))
        // A non-focusable overlay can keep stale accessibility descendants even when the
        // pixels update. Invalidate that subtree without making ticking readings a live region.
        if(isAttachedToWindow && context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isEnabled==true) {
            val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            event.contentChangeTypes=AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
            sendAccessibilityEventUnchecked(event)
        }
    }

    private fun stylePill(label: TextView,warning: Boolean) {
        val dark=palette.surface!=Color.WHITE
        label.setTextColor(if(warning) (if(dark) 0xFFFFD08A.toInt() else 0xFF995800.toInt()) else palette.primary)
        label.background=shape(if(warning) (if(dark) 0xFF49351C.toInt() else 0xFFFFF2DB.toInt()) else palette.soft,14)
        label.setPadding(dp(7),dp(2),dp(7),dp(2))
        // WRAP_CONTENT views must have a stable text size before measuring.
        label.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP,10f)
        label.maxWidth=Int.MAX_VALUE
        label.maxLines=if(largeFont) 2 else 1
        if(warning) label.typeface=Typeface.DEFAULT
    }

    private fun text(size: Int,min: Int,numeric: Boolean=false,bold: Boolean=false)=TextView(context).apply {
        setTextColor(palette.muted); maxLines=1; includeFontPadding=false; ellipsize=TextUtils.TruncateAt.END
        gravity=Gravity.CENTER_VERTICAL
        typeface=Typeface.create(if(numeric) Typeface.MONOSPACE else Typeface.DEFAULT,if(bold) Typeface.BOLD else Typeface.NORMAL)
        setAutoSizeTextTypeUniformWithConfiguration((min*resources.configuration.fontScale).toInt().coerceAtLeast(min),(size*resources.configuration.fontScale).toInt().coerceAtLeast(size),1,TypedValue.COMPLEX_UNIT_DIP)
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
        private val caption=text(12,10,bold=true).apply { maxLines=2; setTextColor(ink); gravity=Gravity.CENTER; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
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
        const val COMPACT_HEIGHT=156
        const val EXPANDED_WIDTH=312
        const val EXPANDED_HEIGHT=416
    }
}
