package org.openmobifitness.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import org.openmobifitness.app.*
import org.openmobifitness.app.data.AppLog
import kotlin.math.abs

class WorkoutService : Service() {
    private val controller get() = (application as OpenMobiApp).controller
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var panel: OverlayPanelView?=null
    private var expanded=false
    private var savedX=0; private var savedY=0
    private val wm get() = getSystemService(WindowManager::class.java)
    private var parameters: WindowManager.LayoutParams?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private var renewedAt=0L
    private val createdAt=SystemClock.elapsedRealtime()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); controller.serviceStarted=true
        savedX=dp(12); savedY=dp(64)
        val localized=localized()
        wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OpenMOBI:training").apply { setReferenceCounted(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("training",localized.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW))
        serviceScope.launch { while(isActive) { refresh(); delay(1000) } }
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(!controller.state.value.demo) {
            try { ServiceCompat.startForeground(this,7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }
            catch(e: Exception) { controller.error(R.string.permission_help); stopSelf(); return START_NOT_STICKY }
        }
        when(intent?.action) { SHOW -> { if(panel==null) { expanded=false; showPanel() } }; HIDE -> hidePanel(); PAUSE -> controller.pauseResume() }
        return START_NOT_STICKY // Never reconnect or replay a control after process death.
    }
    private fun openIntent() = Intent(this,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("training",true)
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
        if(panel!=null || controller.state.value.session==null || !controller.display.floatingEnabled.value || !Settings.canDrawOverlays(this)) return
        val c=localized(); val area=bounds()
        val dark=controller.theme.value=="dark" || (controller.theme.value=="system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val root=OverlayPanelView(c,expanded,dark) { command ->
            when(command) {
                OverlayAction.EXPAND -> { expanded=true; rebuild() }
                OverlayAction.COLLAPSE -> { expanded=false; rebuild() }
                OverlayAction.METRICS -> { hidePanel(); startActivity(openIntent().putExtra("metrics_scope",DisplayScope.EXPANDED.name)) }
                OverlayAction.OPEN -> { hidePanel(); startActivity(openIntent()) }
                OverlayAction.PAUSE -> { controller.pauseResume(); refresh() }
                OverlayAction.MINUS -> { controller.adjust(-1); refresh() }
                OverlayAction.PLUS -> { controller.adjust(1); refresh() }
            }
        }
        val (width,height)=panelSize()
        val params=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=savedX.coerceIn(area.left,(area.right-width).coerceAtLeast(area.left)); y=savedY.coerceIn(0,(area.height()-height).coerceAtLeast(0)) }
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
        root.dragHandle.setOnTouchListener(touch)
        if(!expanded) {
            root.setOnTouchListener(touch)
            root.dragHandle.setOnClickListener { root.performClick() }
        }
        try {
            wm.addView(root,params); panel=root; parameters=params; refresh()
            root.post { if(panel===root) { clamp(root,params); runCatching { wm.updateViewLayout(root,params) } } }
        } catch(e: Exception) { controller.error(R.string.overlay_permission); AppLog.exception("overlay",e) }
    }
    private fun panelSize(): Pair<Int,Int> {
        val area=bounds()
        return minOf(dp(if(expanded) OverlayPanelView.EXPANDED_WIDTH else OverlayPanelView.COMPACT_WIDTH),(area.width()-dp(16)).coerceAtLeast(1)) to
            minOf(dp(if(expanded) OverlayPanelView.EXPANDED_HEIGHT else OverlayPanelView.COMPACT_HEIGHT),(area.height()-dp(16)).coerceAtLeast(1))
    }
    private fun rebuild() {
        val old=panel; val position=parameters
        if(old!=null && position!=null) {
            val area=bounds(); val (width,height)=panelSize()
            // Keep the nearer edge anchored when toggling beside a video or at a screen corner.
            savedX=position.x + if(position.x+old.width/2>area.centerX()) old.width-width else 0
            savedY=position.y + if(position.y+old.height/2>area.height()/2) old.height-height else 0
        }
        hidePanel(); showPanel()
    }
    private fun refresh() {
        val s=controller.state.value; val c=localized()
        if(s.session==null && controller.ble.state.value.phase=="disconnected" && controller.heart.state.value.phase=="disconnected" && SystemClock.elapsedRealtime()-createdAt>10_000) { stopSelf(); return }
        val needsWake=s.session!=null && !s.paused && !s.demo && controller.ble.state.value.phase=="ready"
        if(needsWake && (wakeLock?.isHeld!=true || SystemClock.elapsedRealtime()-renewedAt>600_000)) {
            wakeLock?.acquire(900_000); renewedAt=SystemClock.elapsedRealtime()
        } else if(!needsWake && wakeLock?.isHeld==true) wakeLock?.release()
        if(s.session==null || !controller.display.floatingEnabled.value) hidePanel()
        panel?.bind(c.overlayPresentation(controller,expanded))
        if(!s.demo) getSystemService(NotificationManager::class.java).notify(7,notification())
    }
    private fun hidePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel=null; parameters=null }
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
