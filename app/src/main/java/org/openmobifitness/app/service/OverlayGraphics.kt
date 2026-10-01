package org.openmobifitness.app.service

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View

internal enum class OverlayGlyph { EXPAND, COLLAPSE, TUNE, OPEN, PAUSE, PLAY, MINUS, PLUS }

/** Small native vectors: consistent strokes, no font-dependent Unicode button symbols. */
internal class OverlayIcon(var glyph: OverlayGlyph,private val ink: Int): Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        fun line(x: Float,y: Float,xx: Float,yy: Float)=canvas.drawLine(x,y,xx,yy,paint)
        when(glyph) {
            OverlayGlyph.EXPAND -> { line(6f,9f,12f,15f); line(12f,15f,18f,9f) }
            OverlayGlyph.COLLAPSE -> { line(6f,15f,12f,9f); line(12f,9f,18f,15f) }
            OverlayGlyph.MINUS -> line(5f,12f,19f,12f)
            OverlayGlyph.PLUS -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
            OverlayGlyph.PAUSE -> { paint.style=Paint.Style.FILL; canvas.drawRoundRect(5f,4f,9f,20f,1f,1f,paint); canvas.drawRoundRect(15f,4f,19f,20f,1f,1f,paint) }
            OverlayGlyph.PLAY -> { paint.style=Paint.Style.FILL; canvas.drawPath(Path().apply { moveTo(7f,4f); lineTo(20f,12f); lineTo(7f,20f); close() },paint) }
            OverlayGlyph.OPEN -> {
                canvas.drawPath(Path().apply { moveTo(20f,14f); lineTo(20f,20f); lineTo(4f,20f); lineTo(4f,4f); lineTo(10f,4f) },paint)
                line(12f,12f,21f,3f); line(14f,3f,21f,3f); line(21f,3f,21f,10f)
            }
            OverlayGlyph.TUNE -> {
                for((y,x) in listOf(5f to 9f,12f to 16f,19f to 7f)) {
                    line(3f,y,x-2.5f,y); line(x+2.5f,y,21f,y); canvas.drawCircle(x,y,2.5f,paint)
                }
            }
        }
        paint.style=Paint.Style.STROKE; canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter; invalidateSelf() }
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

internal class OverlayBrand(context: Context): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors=intArrayOf(0xFFECA2C5.toInt(),0xFFDF535D.toInt(),0xFFF0C84B.toInt(),0xFF5888DB.toInt())
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val unit=width/4f
        colors.forEachIndexed { index,color ->
            paint.color=color
            val x=unit*index+unit/2f
            canvas.save(); canvas.rotate(18f,x,height/2f)
            canvas.drawRoundRect(x-unit*.3f,height*.18f,x+unit*.3f,height*.82f,unit*.3f,unit*.3f,paint)
            canvas.restore()
        }
    }
}

internal class OverlayProgress(context: Context,private val track: Int,private val active: Int): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var progress: Float?=null
        set(value) { if(field!=value) { field=value; invalidate() } }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val radius=height/2f
        paint.color=track; canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),radius,radius,paint)
        progress?.takeIf { it>0 }?.let {
            paint.color=active; canvas.drawRoundRect(0f,0f,width*it.coerceIn(0f,1f),height.toFloat(),radius,radius,paint)
        }
    }
}
