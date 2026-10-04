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
    private var lastHintEvent=0L
    private var tone: android.media.ToneGenerator?=null
    private var restorePosition=true
    private val createdAt=SystemClock.elapsedRealtime()
    private var lastNotificationKey: List<String>?=null
    private var idleStopRequested=false
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
            try {
                val current=notification()
                ServiceCompat.startForeground(this,7,current,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                lastNotificationKey=notificationKey(current)
            }
            catch(e: Exception) { controller.error(R.string.permission_help); stopSelf(); return START_NOT_STICKY }
        }
        when(intent?.action) {
            SHOW -> { if(panel==null) { expanded=false; showPanel() } }
            HIDE -> hidePanel()
            PAUSE -> if(controller.state.value.controlOnly) startFromService() else controller.pauseResume()
            DISCONNECT -> { controller.exitControl(); stopIfIdle() }
            STOP_IF_IDLE -> stopIfIdle()
            RECONNECT -> controller.prefs.getString("last_address",null)?.let { address -> controller.connect(org.openmobifitness.app.ble.FoundDevice(address,controller.prefs.getString("last_name","").orEmpty(),0)) }
            RESET_POSITION -> { savedX=dp(12); savedY=dp(64); restorePosition=true; if(panel!=null) { hidePanel(); showPanel() } }
        }
        return START_NOT_STICKY // Never reconnect or replay a control after process death.
    }
    private fun startFromService() = controller.scope.launch {
        controller.start().join()
        if(controller.pendingStart.value!=null) { hidePanel(); startActivity(openIntent().putExtra("training",true).putExtra("devices",false)) }
    }
    private fun openIntent() = Intent(this,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("training",controller.state.value.inUse).putExtra("devices",!controller.state.value.inUse)
    private val notificationBrand by lazy {
        android.graphics.BitmapFactory.decodeResource(resources,R.drawable.brand_light,android.graphics.BitmapFactory.Options().apply { inSampleSize=8 })
    }
    private fun notification(): Notification {
        val c=localized(); val s=controller.state.value
        val content=PendingIntent.getActivity(this,1,openIntent(),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggle=PendingIntent.getService(this,2,Intent(this,WorkoutService::class.java).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val link=controller.ble.state.value
        val accessory=controller.heart.state.value
        val connection=if(link.phase!="disconnected") link else accessory
        val title=when {
            s.inUse && !s.demo && link.phase=="disconnected" -> R.string.notification_disconnected
            s.controlOnly -> R.string.control_only
            s.session!=null -> if(s.paused) R.string.notification_paused else R.string.notification_active
            connection.phase=="ready" -> R.string.notification_ready
            connection.phase=="disconnected" -> R.string.notification_disconnected
            else -> R.string.notification_connecting
        }
        val ticking=s.session!=null && !s.paused
        val text=if(s.session!=null) {
            if(ticking) s.session.device else "${elapsed(s.session.elapsedMs)} · ${s.session.device}"
        } else if(s.controlOnly) "${c.getString(R.string.not_recording)} · ${link.name}" else connection.name.ifBlank { c.getString(R.string.devices) }
        return NotificationCompat.Builder(this,"training").setSmallIcon(R.drawable.ic_openmobi_status).setLargeIcon(notificationBrand).setContentTitle(c.getString(title))
            .setContentText(listOfNotNull((s.session?.startedUserName ?: controller.currentUser.value?.name)?.takeIf { it.isNotBlank() },text).joinToString(" · ")).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            // SystemUI owns the running clock so its action buttons are not rebound
            // every second. Paused/disconnected records retain a fixed elapsed value.
            .setUsesChronometer(ticking).setWhen(if(ticking) System.currentTimeMillis()-(s.session?.elapsedMs ?: 0L) else System.currentTimeMillis())
            .apply {
                if(s.inUse && !s.demo && link.phase=="disconnected") addAction(0,c.getString(R.string.reconnect),PendingIntent.getService(this@WorkoutService,3,Intent(this@WorkoutService,WorkoutService::class.java).setAction(RECONNECT),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                else if(s.session!=null && (s.demo || link.phase=="ready")) addAction(0,c.getString(if(s.paused) R.string.notification_resume else R.string.pause_short),toggle)
                if(s.controlOnly) addAction(0,c.getString(R.string.disconnect),PendingIntent.getService(this@WorkoutService,4,Intent(this@WorkoutService,WorkoutService::class.java).setAction(DISCONNECT),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                addAction(0,c.getString(R.string.notification_open),content)
            }.build()
    }
    private fun notificationKey(notification: Notification)=listOf(
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        controller.state.value.inUse.toString(),
        controller.state.value.session?.id.orEmpty()
    )+notification.actions.orEmpty().map { it.title.toString() }
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
        if(panel!=null || !controller.state.value.inUse || !controller.display.floatingEnabled.value || !Settings.canDrawOverlays(this)) return
        val c=localized(); val area=bounds()
        val dark=controller.theme.value=="dark" || (controller.theme.value=="system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val root=OverlayPanelView(c,expanded,dark) { command ->
            when(command) {
                OverlayAction.EXPAND -> { expanded=true; rebuild() }
                OverlayAction.COLLAPSE -> { expanded=false; rebuild() }
                OverlayAction.METRICS -> { hidePanel(); startActivity(openIntent().putExtra("metrics_scope",DisplayScope.EXPANDED.name)) }
                OverlayAction.OPEN -> { hidePanel(); startActivity(openIntent()) }
                OverlayAction.PAUSE -> { if(controller.state.value.controlOnly) startFromService() else controller.pauseResume(); refresh() }
                OverlayAction.RECONNECT -> { hidePanel(); startActivity(openIntent().putExtra("reconnect",true)) }
                OverlayAction.MINUS -> { controller.adjust(-1); refresh() }
                OverlayAction.PLUS -> { controller.adjust(1); refresh() }
            }
        }
        val (width,height)=panelSize()
        if(restorePosition) {
            val p=controller.local.position(area.width()>area.height())
            savedX=p?.let { area.left+((area.width()-width).coerceAtLeast(0)*it.x).toInt() } ?: dp(12)
            savedY=p?.let { ((area.height()-height).coerceAtLeast(0)*it.y).toInt() } ?: dp(64)
            restorePosition=false
        }
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
                MotionEvent.ACTION_UP -> {
                    if(!dragged) view.performClick() else {
                        val available=bounds()
                        controller.local.position(available.width()>available.height(),OverlayPosition(
                            ((params.x-available.left).toFloat()/(available.width()-root.width).coerceAtLeast(1)).coerceIn(0f,1f),
                            (params.y.toFloat()/(available.height()-root.height).coerceAtLeast(1)).coerceIn(0f,1f)))
                    }; true
                }
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
    private fun panelSize(model: OverlayPresentation=localized().overlayPresentation(controller,expanded)): Pair<Int,Int> {
        val area=bounds()
        val extra=(resources.configuration.fontScale-1f).coerceAtLeast(0f)
        val height=overlayContentHeight(expanded,model,resources.configuration.fontScale)
        return minOf(dp((if(expanded) OverlayPanelView.EXPANDED_WIDTH else OverlayPanelView.COMPACT_WIDTH)+(extra*120).toInt()),(area.width()-dp(16)).coerceAtLeast(1)) to
            minOf(dp(height),(area.height()-dp(16)).coerceAtLeast(1))
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
        if(s.hintEvent!=lastHintEvent) {
            lastHintEvent=s.hintEvent
            if(s.inUse && !s.paused && !s.hintsMuted) {
                val hints=controller.activeHints.value
                if(hints.sound) runCatching { if(tone==null) tone=android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,50); tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,180) }
                if(hints.vibration) getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(180,android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        if(!s.inUse && !s.starting && controller.ble.state.value.phase=="disconnected" && controller.heart.state.value.phase=="disconnected" && SystemClock.elapsedRealtime()-createdAt>10_000) { stopIfIdle(); return }
        val needsWake=s.inUse && !s.demo && controller.ble.state.value.phase=="ready"
        if(needsWake && (wakeLock?.isHeld!=true || SystemClock.elapsedRealtime()-renewedAt>600_000)) {
            wakeLock?.acquire(900_000); renewedAt=SystemClock.elapsedRealtime()
        } else if(!needsWake && wakeLock?.isHeld==true) wakeLock?.release()
        if(!s.inUse || !controller.display.floatingEnabled.value) hidePanel()
        panel?.let { view ->
            val model=c.overlayPresentation(controller,expanded)
            view.bind(model)
            parameters?.let { params ->
                val (width,height)=panelSize(model)
                if(params.width!=width || params.height!=height) {
                    val area=bounds()
                    if(params.x+params.width/2>area.centerX()) params.x+=params.width-width
                    if(params.y+params.height/2>area.height()/2) params.y+=params.height-height
                    params.width=width; params.height=height
                    params.x=params.x.coerceIn(area.left,(area.right-width).coerceAtLeast(area.left))
                    params.y=params.y.coerceIn(0,(area.height()-height).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(view,params) }
                }
            }
        }
        if(!s.demo) {
            val current=notification(); val key=notificationKey(current)
            // Keep native controls stable for touch and accessibility. SystemUI
            // advances the active chronometer; state/action changes still update.
            if(key!=lastNotificationKey) {
                getSystemService(NotificationManager::class.java).notify(7,current)
                lastNotificationKey=key
            }
        }
    }
    private fun hidePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel=null; parameters=null }
    private fun stopIfIdle() {
        // Android can deliver destruction after the user starts the next record.
        // An intentional idle shutdown must never pause or disconnect that new use.
        if(controller.state.value.inUse || controller.state.value.starting) return
        idleStopRequested=true
        stopSelf()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) { super.onConfigurationChanged(newConfig); restorePosition=true; if(panel!=null) { hidePanel(); showPanel() } }
    override fun onDestroy() {
        hidePanel(); serviceScope.cancel(); if(wakeLock?.isHeld==true) wakeLock?.release()
        controller.serviceStarted=false
        tone?.release(); tone=null
        if(!idleStopRequested) {
            if(controller.state.value.controlOnly) controller.exitControl()
            controller.state.value.session?.id?.let(controller::pauseForServiceLoss)
        }
        super.onDestroy()
    }
    companion object {
        const val SHOW="org.openmobifitness.app.SHOW_PANEL"
        const val HIDE="org.openmobifitness.app.HIDE_PANEL"
        const val PAUSE="org.openmobifitness.app.PAUSE_TIMER"
        const val RECONNECT="org.openmobifitness.app.RECONNECT"
        const val DISCONNECT="org.openmobifitness.app.DISCONNECT"
        const val STOP_IF_IDLE="org.openmobifitness.app.STOP_IF_IDLE"
        const val RESET_POSITION="org.openmobifitness.app.RESET_POSITION"
        fun elapsed(ms: Long): String { val sec=ms/1000; return "%02d:%02d:%02d".format(sec/3600,sec/60%60,sec%60) }
        fun number(value: Double?)=value?.let { if(it%1.0==0.0) it.toInt().toString() else "%.1f".format(it) } ?: "—"
    }
}
