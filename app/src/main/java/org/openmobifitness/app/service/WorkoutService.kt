package org.openmobifitness.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import org.openmobifitness.app.*
import org.openmobifitness.core.MetricId
import org.openmobifitness.app.data.AppLog
import kotlin.math.abs

class WorkoutService : Service() {
    private val controller get() = (application as OpenMobiApp).controller
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var panel: View?=null
    private val metricViews=mutableListOf<Pair<MetricId,TextView>>()
    private var expanded=false
    private var status: TextView?=null
    private var savedX=12; private var savedY=100
    private var heading: TextView?=null
    private var pause: Button?=null
    private var plus: Button?=null
    private var minus: Button?=null
    private var panelText=Color.WHITE
    private var panelMuted=Color.LTGRAY
    private val wm get() = getSystemService(WindowManager::class.java)
    private var parameters: WindowManager.LayoutParams?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private var renewedAt=0L
    private val createdAt=SystemClock.elapsedRealtime()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); controller.serviceStarted=true
        val localized=localized()
        wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OpenMobi:training").apply { setReferenceCounted(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("training",localized.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW))
        serviceScope.launch { while(isActive) { refresh(); delay(1000) } }
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(!controller.state.value.demo) {
            try { ServiceCompat.startForeground(this,7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
            catch(e: Exception) { controller.error(R.string.permission_help); stopSelf(); return START_NOT_STICKY }
        }
        when(intent?.action) { SHOW -> { expanded=false; showPanel() }; HIDE -> hidePanel(); PAUSE -> controller.pauseResume() }
        return START_NOT_STICKY // Never reconnect or replay a control after process death.
    }
    private fun openIntent() = Intent(this,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("training",true)
    private fun notification(): Notification {
        val c=localized(); val s=controller.state.value
        val content=PendingIntent.getActivity(this,1,openIntent(),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggle=PendingIntent.getService(this,2,Intent(this,WorkoutService::class.java).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text=if(s.session!=null) "${elapsed(s.session.elapsedMs)} · ${c.getString(if(s.paused) R.string.paused else R.string.active)}" else c.getString(R.string.connected)
        return NotificationCompat.Builder(this,"training").setSmallIcon(R.drawable.ic_mark).setContentTitle(c.getString(R.string.notification_title))
            .setContentText(text).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .apply { if(s.session!=null) addAction(0,c.getString(if(s.paused) R.string.resume else R.string.pause),toggle) }.build()
    }
    private fun dp(n: Int) = (n*resources.displayMetrics.density).toInt()
    private fun bounds(): android.graphics.Rect = if(android.os.Build.VERSION.SDK_INT>=30) {
        val metrics=wm.currentWindowMetrics
        val insets=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        android.graphics.Rect(insets.left,insets.top,metrics.bounds.width()-insets.right,metrics.bounds.height()-insets.bottom)
    } else android.graphics.Rect(0,dp(24),resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels-dp(24))
    private fun clamp(root: View,params: WindowManager.LayoutParams) {
        val area=bounds()
        // TOP/START overlay coordinates already exclude the status bar on Android 10+.
        params.x=params.x.coerceIn(area.left,(area.right-root.width).coerceAtLeast(area.left))
        params.y=params.y.coerceIn(0,(area.height()-root.height).coerceAtLeast(0))
        savedX=params.x; savedY=params.y
    }
    private fun showPanel() {
        if(panel!=null || !Settings.canDrawOverlays(this)) return
        val c=localized(); val area=bounds()
        val dark=controller.theme.value=="dark" || (controller.theme.value=="system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)
        panelText=if(dark) Color.rgb(240,243,249) else Color.rgb(29,34,43)
        panelMuted=if(dark) Color.rgb(190,202,218) else Color.rgb(89,99,115)
        val surface=if(dark) Color.rgb(25,28,34) else Color.rgb(250,251,253)
        val buttonColor=if(dark) Color.rgb(43,52,67) else Color.rgb(231,237,246)
        val content=LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)); elevation=dp(8).toFloat()
            background=GradientDrawable().apply { setColor(surface); cornerRadius=dp(20).toFloat(); setStroke(dp(1),if(dark) Color.rgb(66,74,85) else Color.rgb(215,220,229)) }
        }
        val root=object: ScrollView(c) {
            override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(bounds().height()-dp(16),View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport=false; isVerticalScrollBarEnabled=false; addView(content) }
        val top=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        heading=TextView(c).apply { text="OpenMobi"; textSize=12f; setTextColor(panelMuted); setTypeface(null,Typeface.BOLD); maxLines=2 }
        top.addView(heading,LinearLayout.LayoutParams(0,dp(if(expanded) 48 else 24),1f))
        fun button(label: String,action: ()->Unit)=Button(c).apply {
            text=label; isAllCaps=false; textSize=12f; minHeight=0; minimumHeight=0; minWidth=0; minimumWidth=0
            setPadding(dp(4),0,dp(4),0); setTextColor(panelText)
            val shape=GradientDrawable().apply { setColor(buttonColor); cornerRadius=dp(12).toFloat() }
            background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(if(dark) Color.rgb(68,90,122) else Color.rgb(202,218,242)),android.graphics.drawable.InsetDrawable(shape,dp(3)),null)
            setOnClickListener { action() }
        }
        if(expanded) {
            top.addView(button(c.getString(R.string.customize_panel)) { hidePanel(); startActivity(openIntent().putExtra("metrics_scope",DisplayScope.EXPANDED.name)) }.apply { contentDescription=c.getString(R.string.choose_metrics) },LinearLayout.LayoutParams(dp(48),dp(48)))
            top.addView(button("↗") { hidePanel(); startActivity(openIntent()) }.apply { textSize=22f; contentDescription=c.getString(R.string.open_app) },LinearLayout.LayoutParams(dp(48),dp(48)))
            top.addView(button("⌄") { expanded=false; rebuild() }.apply { textSize=22f; contentDescription=c.getString(R.string.collapse_panel) },LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        else {
            val signature=LinearLayout(c)
            listOf(0xFFECA2C5,0xFFDF535D,0xFFF0C84B,0xFF5888DB).forEach { color ->
                signature.addView(View(c).apply { background=GradientDrawable().apply { setColor(color.toInt()); cornerRadius=dp(2).toFloat() } },LinearLayout.LayoutParams(dp(9),dp(4)).apply { marginStart=dp(3) })
            }; top.addView(signature)
        }
        content.addView(top)
        val fields=controller.display.selections.value.getValue(if(expanded) DisplayScope.EXPANDED else DisplayScope.COMPACT)
        fields.chunked(2).forEach { pair ->
            val row=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL }
            pair.forEach { id ->
                val text=TextView(c).apply { textSize=18f; setTextColor(panelText); setPadding(dp(2),dp(4),dp(5),dp(4)); setLineSpacing(dp(2).toFloat(),1f) }
                metricViews.add(id to text); row.addView(text,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            }; content.addView(row)
        }
        if(expanded) {
            status=TextView(c).apply { textSize=11f; setTextColor(panelMuted); setPadding(dp(3),dp(4),dp(3),dp(4)) }; content.addView(status)
            val controls=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
            minus=button("−") { controller.adjust(-1) }.apply { contentDescription=c.getString(R.string.decrease) }
            plus=button("+") { controller.adjust(1) }.apply { contentDescription=c.getString(R.string.increase) }
            pause=button(c.getString(R.string.pause)) { controller.pauseResume() }
            controls.addView(minus,LinearLayout.LayoutParams(dp(48),dp(48)))
            controls.addView(pause,LinearLayout.LayoutParams(0,dp(48),1f))
            controls.addView(plus,LinearLayout.LayoutParams(dp(48),dp(48))); content.addView(controls)
        }
        val width=minOf(dp(if(expanded) 280 else 204),area.width()-dp(16))
        val params=WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=savedX; y=savedY }
        var startX=0f; var startY=0f; var oldX=0; var oldY=0; var dragged=false
        val slop=ViewConfiguration.get(c).scaledTouchSlop
        val touch=View.OnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX=event.rawX; startY=event.rawY; oldX=params.x; oldY=params.y; dragged=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(event.rawX-startX)>slop || abs(event.rawY-startY)>slop) dragged=true
                    if(dragged) { params.x=(oldX+event.rawX-startX).toInt(); params.y=(oldY+event.rawY-startY).toInt(); clamp(root,params); runCatching { wm.updateViewLayout(root,params) } }; true
                }
                MotionEvent.ACTION_UP -> { if(!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { dragged=false; true }
                else -> false
            }
        }
        heading!!.setOnTouchListener(touch)
        if(!expanded) {
            root.isClickable=true; root.contentDescription=c.getString(R.string.expand_panel)
            root.setOnClickListener { expanded=true; rebuild() }; root.setOnTouchListener(touch)
            heading!!.setOnClickListener { expanded=true; rebuild() }
        }
        try {
            wm.addView(root,params); panel=root; parameters=params; refresh()
            root.post { if(panel===root) { clamp(root,params); runCatching { wm.updateViewLayout(root,params) } } }
        } catch(e: Exception) { controller.error(R.string.overlay_permission); AppLog.exception("overlay",e) }
    }
    private fun rebuild() { hidePanel(); showPanel() }
    private fun refresh() {
        val s=controller.state.value; val c=localized()
        if(s.session==null && controller.ble.state.value.phase=="disconnected" && controller.heart.state.value.phase=="disconnected" && SystemClock.elapsedRealtime()-createdAt>10_000) { stopSelf(); return }
        val needsWake=s.session!=null && !s.paused && !s.demo && controller.ble.state.value.phase=="ready"
        if(needsWake && (wakeLock?.isHeld!=true || SystemClock.elapsedRealtime()-renewedAt>600_000)) {
            wakeLock?.acquire(900_000); renewedAt=SystemClock.elapsedRealtime()
        } else if(!needsWake && wakeLock?.isHeld==true) wakeLock?.release()
        if(s.session==null) hidePanel()
        metricViews.forEach { (id,view) ->
            val r=c.reading(id,controller)
            val text=android.text.SpannableString("${r.label}\n${r.value}${if(r.unit.isEmpty()) "" else " ${r.unit}"}")
            text.setSpan(android.text.style.RelativeSizeSpan(.68f),0,r.label.length,0)
            text.setSpan(android.text.style.ForegroundColorSpan(panelMuted),0,r.label.length,0)
            view.text=text
        }
        val resistance=c.reading(MetricId.RESISTANCE,controller)
        status?.text="${resistance.label}  ${resistance.value} · ${resistance.unit}"+s.remainingMs?.let { "  ·  ${c.minutesSeconds(it)}" }.orEmpty()
        heading?.text=if(s.paused) "OpenMobi · ${c.getString(R.string.paused)}" else if(s.demo) "OpenMobi · ${c.getString(R.string.demo)}" else "OpenMobi"
        pause?.text=c.getString(if(s.paused) R.string.resume else R.string.pause)
        pause?.isEnabled=s.session!=null
        val link=controller.ble.state.value
        val can=s.demo || (link.phase=="ready" && link.writable && link.range!=null && link.metrics.resistance!=null && !link.busy)
        minus?.isEnabled=can; plus?.isEnabled=can
        minus?.alpha=if(can) 1f else .38f; plus?.alpha=if(can) 1f else .38f
        if(!s.demo) getSystemService(NotificationManager::class.java).notify(7,notification())
    }
    private fun hidePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel=null; metricViews.clear(); status=null; heading=null; plus=null; minus=null; pause=null }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); if(panel!=null) { hidePanel(); showPanel() } }
    override fun onDestroy() {
        hidePanel(); serviceScope.cancel(); if(wakeLock?.isHeld==true) wakeLock?.release()
        controller.serviceStarted=false
        if(controller.state.value.session!=null && !controller.state.value.paused) controller.pauseResume()
        super.onDestroy()
    }
    companion object {
        const val SHOW="org.openmobifitness.app.SHOW_PANEL"
        const val HIDE="org.openmobifitness.app.HIDE_PANEL"
        const val PAUSE="org.openmobifitness.app.PAUSE_TIMER"
        fun elapsed(ms: Long): String { val sec=ms/1000; return "%02d:%02d:%02d".format(sec/3600,sec/60%60,sec%60) }
        fun number(value: Double?)=value?.let { if(it%1.0==0.0) it.toInt().toString() else "%.1f".format(it) } ?: "—"
    }
}
