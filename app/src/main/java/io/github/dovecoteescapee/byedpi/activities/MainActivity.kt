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
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.util.Locale
import kotlin.math.max

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
    private lateinit var versionText: TextView
    private lateinit var connectionType: TextView
    
    private var isTvMode = false
    private val handler = Handler(Looper.getMainLooper())
    
    private var startTimestamp: Long = 0
    private var startRx: Long = 0
    private var startTx: Long = 0

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_NOTIFICATIONS = 3

        private val OFF_GRADIENT = intArrayOf(
            Color.parseColor("#0A0A15"),
            Color.parseColor("#050510"),
            Color.parseColor("#020208"),
            Color.BLACK
        )

        private val ON_GRADIENT = intArrayOf(
            Color.parseColor("#001A12"),
            Color.parseColor("#00110C"),
            Color.parseColor("#000906"),
            Color.BLACK
        )
        
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
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || 
                   !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        } else {
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        mainContainer = FrameLayout(this)
        
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        versionText = TextView(this).apply {
            text = "v1.0"
            textSize = 10f
            setTextColor(Color.parseColor("#30FFFFFF"))
            gravity = Gravity.TOP or Gravity.END
            setPadding(0, 50, 40, 0)
            alpha = 0.7f
        }

        statusText = TextView(this).apply {
            textSize = if (isTvMode) 22f else 28f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Color.parseColor("#D0D0D0"))
            gravity = Gravity.CENTER
            setPadding(0, if (isTvMode) 80 else 240, 0, 0)
            letterSpacing = 0.02f
        }

        connectionType = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#60FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
            alpha = 0.8f
        }

        val btnSize = if (isTvMode) 260 else 460
        val btnMargin = if (isTvMode) 50 else 140

        powerButton = ImageButton(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvMode
            
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = if (isTvMode) 12f else 18f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    
                    val glowPaint = Paint().apply {
                        color = Color.parseColor("#80FFFFFF")
                        style = Paint.Style.STROKE
                        strokeWidth = if (isTvMode) 24f else 36f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        alpha = 40
                    }
                    
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 4f
                    
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 285f, 330f, false, glowPaint)
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 285f, 330f, false, paint)
                    
                    val linePaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = if (isTvMode) 12f else 18f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                    }
                    
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.25f, linePaint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setImageDrawable(icon)
            
            background = createButtonBackground(false)
            elevation = 40f
            translationZ = 20f
            stateListAnimator = null
            
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
                setColor(Color.parseColor("#0AFFFFFF"))
                cornerRadius = 56f
                setStroke(1, Color.parseColor("#15FFFFFF"))
            }
            
            setPadding(70, 50, 70, 50)
            elevation = 24f
            translationZ = 12f
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                width = if (isTvMode) 500 else 720
            }
        }

        timerLabel = TextView(this).apply {
            text = "ДЛИТЕЛЬНОСТЬ СЕССИИ"
            textSize = 11f
            setTextColor(Color.parseColor("#90FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
            setPadding(0,0,0,12)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }

        timerValue = TextView(this).apply {
            text = "00:00:00"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 28)
            letterSpacing = 1.2f
        }
        
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(140, 4).apply {
                gravity = Gravity.CENTER
                setMargins(0, 0, 0, 28)
            }
            setBackgroundColor(Color.parseColor("#35FFFFFF"))
        }

        trafficLabel = TextView(this).apply {
            text = "ОБЪЁМ ТРАФИКА"
            textSize = 11f
            setTextColor(Color.parseColor("#90FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
            setPadding(0,0,0,12)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }

        trafficValue = TextView(this).apply {
            text = "↓ 0 MB   ↑ 0 MB"
            textSize = 20f
            setTextColor(Color.parseColor("#F8F8F8"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.3f
        }

        statsContainer.addView(timerLabel)
        statsContainer.addView(timerValue)
        statsContainer.addView(divider)
        statsContainer.addView(trafficLabel)
        statsContainer.addView(trafficValue)

        proxyAddress = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#70FFFFFF"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 60
            layoutParams = params
        }

        contentLayout.addView(statusText)
        contentLayout.addView(connectionType)
        contentLayout.addView(powerButton)
        contentLayout.addView(statsContainer)
        contentLayout.addView(proxyAddress)

        mainContainer.addView(versionText)
        mainContainer.addView(contentLayout)
        setContentView(mainContainer)

        mainContainer.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, 0)
            
            val bottomInset = insets.systemWindowInsetBottom
            if (bottomInset > 0) {
                val layoutParams = contentLayout.layoutParams as FrameLayout.LayoutParams
                layoutParams.bottomMargin = bottomInset + 20
                contentLayout.layoutParams = layoutParams
            }
            
            insets
        }

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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            powerButton.isClickable = false
            
            val scaleAnim = android.view.animation.ScaleAnimation(
                0.95f, 1f, 0.95f, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
            }
            
            powerButton.startAnimation(scaleAnim)
            
            val (status, _) = appStatus
            if (status == AppStatus.Halted) {
                start() 
            } else {
                stop()
            }
            
            powerButton.postDelayed({ powerButton.isClickable = true }, 800)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
        } else {
            resetStatsUI()
        }

        ShortcutUtils.update(this)
    }

    override fun onResume() {
        super.onResume()
        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
            updateUIState()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        } else {
            resetStatsUI()
            updateUIState()
        }
        
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        contentLayout.startAnimation(fadeIn)
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
        
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    private fun stop() {
        ServiceManager.stop(this)
        clearSessionData()
        resetStatsUI()
        handler.removeCallbacks(updateRunnable)
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
            
        timerValue.text = "00:00:00"
        trafficValue.text = "↓ 0 B   ↑ 0 B"
    }

    private fun restoreSessionData() {
        val prefs = getPreferences()
        val now = SystemClock.elapsedRealtime()
        
        if (prefs.contains(PREF_SESSION_START)) {
            startTimestamp = prefs.getLong(PREF_SESSION_START, now)
            startRx = prefs.getLong(PREF_SESSION_RX, 0)
            startTx = prefs.getLong(PREF_SESSION_TX, 0)
            
            if (startTimestamp > now || (now - startTimestamp) > 31536000000L) {
                 initSessionData()
            }
        } else {
            initSessionData()
        }
    }

    private fun clearSessionData() {
        getPreferences().edit()
            .remove(PREF_SESSION_START)
            .remove(PREF_SESSION_RX)
            .remove(PREF_SESSION_TX)
            .apply()
    }

    private fun updateStats() {
        val now = SystemClock.elapsedRealtime()
        
        if (startTimestamp == 0L || startTimestamp > now) {
            timerValue.text = "00:00:00"
            return
        }

        var duration = now - startTimestamp
        if (duration < 0) duration = 0
        
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60))
        
        timerValue.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid())
        
        var rx = 0L
        var tx = 0L

        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
            if (currentRx < startRx) startRx = currentRx
            if (currentTx < startTx) startTx = currentTx
            
            rx = max(0L, currentRx - startRx)
            tx = max(0L, currentTx - startTx)
        }
        
        trafficValue.text = "↓ ${formatBytes(rx)}   ↑ ${formatBytes(tx)}"
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
        if (digitGroups < 0) return "0 B"
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, if (digitGroups == 0) "%.0f %s" else "%.1f %s", formatted, units[digitGroups])
    }

    private fun createButtonBackground(isRunning: Boolean): Drawable {
        val color = if (isRunning) Color.parseColor("#00FFAA") else Color.parseColor("#50FFFFFF")
        val glowColor = if (isRunning) Color.parseColor("#1500FFAA") else Color.TRANSPARENT
        
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(glowColor)
            setStroke(if (isRunning) 12 else 8, color)
        }
        
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isRunning) Color.parseColor("#3000FFAA") else Color.parseColor("#20FFFFFF"))
            setStroke(if (isRunning) 12 else 8, color)
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
        val mode = prefs.mode()

        proxyAddress.text = "$ip:$port"
        connectionType.text = when (mode) {
            Mode.VPN -> "VPN РЕЖИМ"
            Mode.Proxy -> "ПРОКСИ РЕЖИМ"
        }

        if (status == AppStatus.Running) {
            statusText.text = "ЗАЩИТА АКТИВНА"
            statusText.setTextColor(Color.parseColor("#00FFAA"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_GRADIENT)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            gradient.setDither(true)
            gradient.alpha = 230
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(true)
            powerButton.elevation = 60f
            powerButton.translationZ = 30f
            
            if (startTimestamp == 0L) restoreSessionData()

            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)

        } else {
            statusText.text = "ГОТОВ К ИСПОЛЬЗОВАНИЮ"
            statusText.setTextColor(Color.parseColor("#B0B0B0"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_GRADIENT)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            gradient.setDither(true)
            gradient.alpha = 230
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(false)
            powerButton.elevation = 40f
            powerButton.translationZ = 20f
            resetStatsUI()
            
            handler.removeCallbacks(updateRunnable)
        }
        
        val fadeAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeAnim.duration = 300
        statusText.startAnimation(fadeAnim)
        connectionType.startAnimation(fadeAnim)
        
        val scaleAnim = android.view.animation.ScaleAnimation(
            0.98f, 1f, 0.98f, 1f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
        }
        powerButton.startAnimation(scaleAnim)
    }
}