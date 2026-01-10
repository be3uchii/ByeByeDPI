package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {
    private lateinit var mainContainer: FrameLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var powerBtnView: PowerButtonView
    private lateinit var statsCard: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var trafficDownText: TextView
    private lateinit var trafficUpText: TextView
    private lateinit var ipText: TextView
    private lateinit var statusLabel: TextView
    private lateinit var bottomActionBtn: TextView 
    
    private val handler = Handler(Looper.getMainLooper())
    private var startTimestamp: Long = 0
    private var startRx: Long = 0
    private var startTx: Long = 0
    private var isTvMode = false

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_NOTIFICATIONS = 3
        private const val PREF_SESSION_START = "session_start_ts"
        private const val PREF_SESSION_RX = "session_rx_start"
        private const val PREF_SESSION_TX = "session_tx_start"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                updateUIState()
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (appStatus.first == AppStatus.Running) {
                updateStats()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        hideSystemUI()

        mainContainer = FrameLayout(this)
        
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 120, 60, 60)
        }

        powerBtnView = PowerButtonView(this).apply {
            layoutParams = LinearLayout.LayoutParams(600, 600).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 80
            }
        }

        statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1AFFFFFF"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#30FFFFFF"))
            }
            setPadding(50, 40, 50, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 60
            }
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
        }
        val labelSession = TextView(this).apply {
            text = "СЕССИЯ"
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.5f)
        }
        timerText = TextView(this).apply {
            text = "00:00:00"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.5f)
            typeface = Typeface.MONOSPACE
        }
        row1.addView(labelSession)
        row1.addView(timerText)

        val labelTraffic = TextView(this).apply {
            text = "ТРАФИК"
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            setPadding(0, 30, 0, 10)
        }

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
        }
        trafficDownText = TextView(this).apply {
            text = "↓ 0 B"
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.5f)
        }
        trafficUpText = TextView(this).apply {
            text = "↑ 0 B"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.5f)
        }
        row2.addView(trafficDownText)
        row2.addView(trafficUpText)

        val labelIp = TextView(this).apply {
            text = "IP"
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            setPadding(0, 30, 0, 5)
        }
        ipText = TextView(this).apply {
            text = "127.0.0.1:1080"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        statsCard.addView(row1)
        statsCard.addView(labelTraffic)
        statsCard.addView(row2)
        statsCard.addView(labelIp)
        statsCard.addView(ipText)

        statusLabel = TextView(this).apply {
            text = "ГОТОВ К РАБОТЕ"
            textSize = 18f
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            isAllCaps = true
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                bottomMargin = 40
            }
        }

        bottomActionBtn = TextView(this).apply {
            text = "Connect"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20FFFFFF"))
                cornerRadius = 100f
                setStroke(2, Color.parseColor("#40FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 160)
            isClickable = true
            isFocusable = true
        }

        contentLayout.addView(powerBtnView)
        contentLayout.addView(statsCard)
        contentLayout.addView(statusLabel)
        contentLayout.addView(bottomActionBtn)

        mainContainer.addView(contentLayout)
        setContentView(mainContainer)

        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        val clickListener = View.OnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val (status, _) = appStatus
            if (status == AppStatus.Halted) start() else stop()
        }
        
        bottomActionBtn.setOnClickListener(clickListener)
        powerBtnView.setOnClickListener(clickListener)

        if (appStatus.first == AppStatus.Running) restoreSessionData()
        
        ShortcutUtils.update(this)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
            updateUIState()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        } else {
            updateUIState()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            ServiceManager.start(this, Mode.VPN)
            initSessionData()
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun start() {
        if (appStatus.first == AppStatus.Running) return
        initSessionData()
        val prefs = getPreferences()
        when (prefs.mode()) {
            Mode.VPN -> {
                val intentPrepare = VpnService.prepare(this)
                if (intentPrepare != null) startActivityForResult(intentPrepare, REQUEST_VPN)
                else ServiceManager.start(this, Mode.VPN)
            }
            Mode.Proxy -> ServiceManager.start(this, Mode.Proxy)
        }
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    private fun stop() {
        ServiceManager.stop(this)
        getPreferences().edit().remove(PREF_SESSION_START).remove(PREF_SESSION_RX).remove(PREF_SESSION_TX).apply()
        handler.removeCallbacks(updateRunnable)
        resetStatsUI()
    }

    private fun initSessionData() {
        startTimestamp = SystemClock.elapsedRealtime()
        startRx = TrafficStats.getUidRxBytes(Process.myUid())
        startTx = TrafficStats.getUidTxBytes(Process.myUid())
        if (startRx < 0) startRx = 0
        if (startTx < 0) startTx = 0
        getPreferences().edit()
            .putLong(PREF_SESSION_START, startTimestamp)
            .putLong(PREF_SESSION_RX, startRx)
            .putLong(PREF_SESSION_TX, startTx)
            .apply()
        timerText.text = "00:00:00"
        trafficDownText.text = "↓ 0 B"
        trafficUpText.text = "↑ 0 B"
    }

    private fun restoreSessionData() {
        val prefs = getPreferences()
        startTimestamp = prefs.getLong(PREF_SESSION_START, SystemClock.elapsedRealtime())
        startRx = prefs.getLong(PREF_SESSION_RX, TrafficStats.getUidRxBytes(Process.myUid()))
        startTx = prefs.getLong(PREF_SESSION_TX, TrafficStats.getUidTxBytes(Process.myUid()))
    }

    private fun resetStatsUI() {
        timerText.text = "00:00:00"
        trafficDownText.text = "↓ 0 B"
        trafficUpText.text = "↑ 0 B"
    }

    private fun updateStats() {
        val now = SystemClock.elapsedRealtime()
        val duration = max(0L, now - startTimestamp)
        val s = (duration / 1000) % 60
        val m = (duration / (1000 * 60)) % 60
        val h = (duration / (1000 * 60 * 60))
        timerText.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

        val cRx = TrafficStats.getUidRxBytes(Process.myUid())
        val cTx = TrafficStats.getUidTxBytes(Process.myUid())
        val rx = if (cRx >= startRx) cRx - startRx else 0
        val tx = if (cTx >= startTx) cTx - startTx else 0
        trafficDownText.text = "↓ ${formatBytes(rx)}"
        trafficUpText.text = "↑ ${formatBytes(tx)}"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val unit = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), unit)
    }

    private fun updateUIState() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()
        ipText.text = "$ip:$port"

        if (status == AppStatus.Running) {
            animateGradient(false)
            statusLabel.text = "АКТИВНО"
            statusLabel.setTextColor(Color.parseColor("#2ECC71"))
            bottomActionBtn.text = "Disconnect"
            powerBtnView.setState(true)
            if (!handler.hasMessages(0)) handler.post(updateRunnable)
        } else {
            animateGradient(true)
            statusLabel.text = "ГОТОВ К РАБОТЕ"
            statusLabel.setTextColor(Color.parseColor("#A0A0A0"))
            bottomActionBtn.text = "Connect"
            powerBtnView.setState(false)
            resetStatsUI()
            handler.removeCallbacks(updateRunnable)
        }
    }

    private fun animateGradient(isOff: Boolean) {
        val colors = if (isOff) intArrayOf(Color.parseColor("#1A1A2E"), Color.BLACK) 
                     else intArrayOf(Color.parseColor("#051E13"), Color.BLACK)
        val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
        mainContainer.background = gd
    }

    class PowerButtonView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
    ) : View(context, attrs) {
        private var isOn = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = 12f
            iconPaint.strokeCap = Paint.Cap.ROUND
            iconPaint.color = Color.WHITE
        }

        fun setState(on: Boolean) {
            isOn = on
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2
            
            // Outer Glow
            val glowColor = if (isOn) Color.parseColor("#2000FF00") else Color.parseColor("#2000BFFF")
            paint.color = glowColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, w/2, paint)

            // Main Circle
            paint.shader = if (isOn) 
                LinearGradient(0f, 0f, 0f, h, Color.parseColor("#2ECC71"), Color.parseColor("#0B3D2C"), Shader.TileMode.CLAMP)
            else 
                LinearGradient(0f, 0f, 0f, h, Color.WHITE, Color.parseColor("#E0E0E0"), Shader.TileMode.CLAMP)
            
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, w/2 - 40, paint)

            // Inner Concave
            paint.shader = null
            paint.color = if (isOn) Color.parseColor("#05221D") else Color.parseColor("#F5F5F5")
            canvas.drawCircle(cx, cy, w/3.5f, paint)

            // Icon
            iconPaint.color = if (isOn) Color.WHITE else Color.parseColor("#505050")
            if (isOn) iconPaint.setShadowLayer(15f, 0f, 0f, Color.WHITE) else iconPaint.clearShadowLayer()
            
            val r = w / 8f
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 30f, 300f, false, iconPaint)
            canvas.drawLine(cx, cy - r, cx, cy - r * 0.2f, iconPaint)
        }
    }
}
