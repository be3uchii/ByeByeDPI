package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var mainContainer: FrameLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var powerButton: ImageButton
    
    private lateinit var statsContainer: LinearLayout
    private lateinit var timerLabel: TextView
    private lateinit var timerValue: TextView
    private lateinit var trafficLabel: TextView
    private lateinit var trafficValue: TextView
    private lateinit var proxyAddress: TextView
    
    private var isTvMode = false
    private val handler = Handler(Looper.getMainLooper())
    
    private var startTimestamp: Long = 0
    private var startRx: Long = 0
    private var startTx: Long = 0

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_NOTIFICATIONS = 3

        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#1A1A2E"),
            Color.parseColor("#16213E"),
            Color.parseColor("#0F3460"),
            Color.BLACK
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#051e13"),
            Color.parseColor("#0a3d2e"),
            Color.parseColor("#0f5c45"),
            Color.parseColor("#000000")
        )
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
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || 
                   !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        mainContainer = FrameLayout(this)
        
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            textSize = if (isTvMode) 18f else 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            setPadding(0, if (isTvMode) 40 else 180, 0, 0)
            letterSpacing = 0.1f
        }

        val btnSize = if (isTvMode) 260 else 420
        val btnMargin = if (isTvMode) 30 else 120

        powerButton = ImageButton(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvMode
            
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = if (isTvMode) 8f else 10f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(12f, 0f, 0f, Color.parseColor("#60000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3.2f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 20f, 320f, false, paint)
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.35f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setImageDrawable(icon)
            
            background = createButtonBackground(false)
            
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
                topMargin = btnMargin
                bottomMargin = btnMargin
            }
        }

        statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20FFFFFF"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#15FFFFFF"))
            }
            setPadding(60, 40, 60, 40)
            
            layoutParams = LinearLayout.LayoutParams(
                if (isTvMode) 500 else 800, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        timerLabel = TextView(this).apply {
            text = "ВРЕМЯ СЕССИИ"
            textSize = 10f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        timerValue = TextView(this).apply {
            text = "00:00:00"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 5, 0, 25)
        }
        
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(20, 0, 20, 25)
            }
            setBackgroundColor(Color.parseColor("#15FFFFFF"))
        }

        trafficLabel = TextView(this).apply {
            text = "ТРАФИК"
            textSize = 10f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        trafficValue = TextView(this).apply {
            text = "↓ 0 MB   ↑ 0 MB"
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 0)
        }

        statsContainer.addView(timerLabel)
        statsContainer.addView(timerValue)
        statsContainer.addView(divider)
        statsContainer.addView(trafficLabel)
        statsContainer.addView(trafficValue)

        proxyAddress = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#40FFFFFF"))
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 50
            layoutParams = params
        }

        contentLayout.addView(statusText)
        contentLayout.addView(powerButton)
        contentLayout.addView(statsContainer)
        contentLayout.addView(proxyAddress)

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

        powerButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            powerButton.isClickable = false
            
            val (status, _) = appStatus
            if (status == AppStatus.Halted) start() else stop()
            
            powerButton.postDelayed({ powerButton.isClickable = true }, 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        if (appStatus.first == AppStatus.Halted) {
            getPreferences().edit()
                .remove("session_start_ts")
                .remove("session_rx_start")
                .remove("session_tx_start")
                .apply()
        }

        ShortcutUtils.update(this)
    }

    override fun onResume() {
        super.onResume()
        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
        } else {
            resetStatsUI()
        }
        updateUIState()
        
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
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

    private fun start() {
        if (appStatus.first == AppStatus.Running) return
        
        initSessionData()

        val prefs = getPreferences()
        when (prefs.mode()) {
            Mode.VPN -> {
                val intentPrepare = VpnService.prepare(this)
                if (intentPrepare != null) {
                    startActivityForResult(intentPrepare, REQUEST_VPN)
                } else {
                    ServiceManager.start(this, Mode.VPN)
                }
            }
            Mode.Proxy -> ServiceManager.start(this, Mode.Proxy)
        }
    }

    private fun initSessionData() {
        startTimestamp = SystemClock.elapsedRealtime()
        startRx = TrafficStats.getUidRxBytes(Process.myUid())
        startTx = TrafficStats.getUidTxBytes(Process.myUid())

        if (startRx == TrafficStats.UNSUPPORTED.toLong()) startRx = 0
        if (startTx == TrafficStats.UNSUPPORTED.toLong()) startTx = 0
        
        getPreferences().edit()
            .putLong("session_start_ts", startTimestamp)
            .putLong("session_rx_start", startRx)
            .putLong("session_tx_start", startTx)
            .apply()
    }

    private fun stop() {
        ServiceManager.stop(this)
        getPreferences().edit()
            .remove("session_start_ts")
            .remove("session_rx_start")
            .remove("session_tx_start")
            .apply()
        
        resetStatsUI()
    }

    private fun restoreSessionData() {
        val prefs = getPreferences()
        val now = SystemClock.elapsedRealtime()
        
        if (prefs.contains("session_start_ts")) {
            startTimestamp = prefs.getLong("session_start_ts", now)
            startRx = prefs.getLong("session_rx_start", 0)
            startTx = prefs.getLong("session_tx_start", 0)
        } else {
            initSessionData()
        }
    }

    private fun updateStats() {
        val now = SystemClock.elapsedRealtime()
        val duration = now - startTimestamp
        val safeDuration = if (duration < 0) 0 else duration
        
        val seconds = (safeDuration / 1000) % 60
        val minutes = (safeDuration / (1000 * 60)) % 60
        val hours = (safeDuration / (1000 * 60 * 60))
        timerValue.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid())
        
        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
            val rx = if (currentRx >= startRx) currentRx - startRx else currentRx
            val tx = if (currentTx >= startTx) currentTx - startTx else currentTx
            trafficValue.text = "↓ ${formatBytes(rx)}   ↑ ${formatBytes(tx)}"
        }
    }
    
    private fun resetStatsUI() {
        startTimestamp = 0
        startRx = 0
        startTx = 0
        timerValue.text = "00:00:00"
        trafficValue.text = "↓ 0 B   ↑ 0 B"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun createButtonBackground(isRunning: Boolean): Drawable {
        val color = if (isRunning) Color.parseColor("#2ECC71") else Color.parseColor("#40FFFFFF")
        val glowColor = if (isRunning) Color.parseColor("#302ECC71") else Color.TRANSPARENT
        
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(glowColor)
            setStroke(if (isRunning) 6 else 3, color)
        }
        
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isRunning) Color.parseColor("#502ECC71") else Color.parseColor("#20FFFFFF"))
            setStroke(if (isRunning) 6 else 3, color)
        }
        
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun updateUIState() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            statusText.text = "ЗАЩИЩЕНО"
            statusText.setTextColor(Color.parseColor("#2ECC71"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(true)
            
        } else {
            statusText.text = "ОТКЛЮЧЕНО"
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(false)
            resetStatsUI()
        }
    }
}
