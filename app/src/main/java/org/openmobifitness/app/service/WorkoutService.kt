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

class WorkoutService : Service() {
    private val controller get() = (application as OpenMobiApp).controller
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var panel: LinearLayout?=null
    private var readings: TextView?=null
    private var heading: TextView?=null
    private var pause: Button?=null
    private var plus: Button?=null
    private var minus: Button?=null
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
        when(intent?.action) { SHOW -> showPanel(); HIDE -> hidePanel(); PAUSE -> controller.pauseResume() }
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
    private fun showPanel() {
        if(panel!=null || !Settings.canDrawOverlays(this)) return
        val c=localized()
        val root=LinearLayout(c).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); elevation=dp(12).toFloat()
            background=GradientDrawable().apply { setColor(Color.rgb(25,26,34)); cornerRadius=dp(24).toFloat(); setStroke(dp(1),Color.rgb(73,72,85)) }
        }
        heading=TextView(c).apply { text="OpenMobi"; textSize=16f; setTextColor(Color.rgb(255,156,192)); setTypeface(null,Typeface.BOLD); setPadding(0,0,0,dp(12)); contentDescription=c.getString(R.string.overlay_info) }
        root.addView(heading)
        readings=TextView(c).apply { textSize=19f; setTextColor(Color.WHITE); setLineSpacing(dp(5).toFloat(),1f); setPadding(0,0,0,dp(8)) }
        root.addView(readings)
        fun button(label: String,action: ()->Unit)=Button(c).apply { text=label; isAllCaps=false; minHeight=dp(48); setOnClickListener { action() } }
        val controls=LinearLayout(c).apply { orientation=LinearLayout.HORIZONTAL }
        minus=button("−") { controller.adjust(-1) }.apply { contentDescription=c.getString(R.string.decrease) }
        plus=button("+") { controller.adjust(1) }.apply { contentDescription=c.getString(R.string.increase) }
        pause=button(c.getString(R.string.pause)) { controller.pauseResume() }
        controls.addView(minus,LinearLayout.LayoutParams(0,dp(52),1f)); controls.addView(pause,LinearLayout.LayoutParams(0,dp(60),2.4f)); controls.addView(plus,LinearLayout.LayoutParams(0,dp(52),1f))
        root.addView(controls)
        root.addView(button(c.getString(R.string.open_app)) { hidePanel(); startActivity(openIntent()) })
        val width=minOf(dp(360),resources.displayMetrics.widthPixels-dp(24))
        val params=WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=dp(12); y=dp(100) }
        var startX=0f; var startY=0f; var oldX=0; var oldY=0
        heading!!.setOnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX=event.rawX; startY=event.rawY; oldX=params.x; oldY=params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x=(oldX+event.rawX-startX).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-root.width).coerceAtLeast(0))
                    params.y=(oldY+event.rawY-startY).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-root.height).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(root,params) }; true
                }
                MotionEvent.ACTION_UP -> { view.performClick(); true }
                else -> false
            }
        }
        try { wm.addView(root,params); panel=root; parameters=params; refresh() } catch(e: Exception) { controller.error(R.string.overlay_permission) }
    }
    private fun refresh() {
        val s=controller.state.value; val c=localized()
        if(s.session==null && controller.ble.state.value.phase=="disconnected" && controller.heart.state.value.phase=="disconnected" && SystemClock.elapsedRealtime()-createdAt>10_000) { stopSelf(); return }
        val needsWake=s.session!=null && !s.paused && !s.demo && controller.ble.state.value.phase=="ready"
        if(needsWake && (wakeLock?.isHeld!=true || SystemClock.elapsedRealtime()-renewedAt>600_000)) {
            wakeLock?.acquire(900_000); renewedAt=SystemClock.elapsedRealtime()
        } else if(!needsWake && wakeLock?.isHeld==true) wakeLock?.release()
        if(s.session==null) hidePanel()
        val m=s.metrics
        readings?.text="${elapsed(s.session?.elapsedMs ?: 0)}  ·  ${c.getString(if(s.paused) R.string.paused else R.string.active)}\n"+
            "${c.getString(R.string.resistance)}  ${number(m.resistance)}    ${number(m.cadence)} rpm"
        heading?.text=if(s.demo) "OpenMobi · ${c.getString(R.string.demo)}" else "OpenMobi"
        pause?.text=c.getString(if(s.paused) R.string.resume else R.string.pause)
        pause?.isEnabled=s.session!=null
        val link=controller.ble.state.value
        val can=s.demo || (link.phase=="ready" && link.writable && link.range!=null && link.metrics.resistance!=null && !link.busy)
        minus?.isEnabled=can; plus?.isEnabled=can
        if(!s.demo) getSystemService(NotificationManager::class.java).notify(7,notification())
    }
    private fun hidePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel=null; readings=null; heading=null; plus=null; minus=null; pause=null }
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
