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
            Color.parseColor("#121212"),
            Color.parseColor("#1E1E2C"),
            Color.parseColor("#252538"),
            Color.parseColor("#151520")
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#001510"),
            Color.parseColor("#002B20"),
            Color.parseColor("#00382A"),
            Color.parseColor("#001A12")
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
            textSize = if (isTvMode) 16f else 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#808080"))
            gravity = Gravity.CENTER
            setPadding(0, if (isTvMode) 40 else 160, 0, 0)
            letterSpacing = 0.15f
            text = "ГОТОВО"
        }

        val btnSize = if (isTvMode) 240 else 380
        val btnMargin = if (isTvMode) 30 else 100

        powerButton = ImageButton(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvMode
            
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 8f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(10f, 0f, 0f, Color.parseColor("#80000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3.5f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 20f, 320f, false, paint)
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.4f, paint)
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

        // Блок статистики (Glassmorphism)
        statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            // Полупрозрачный фон, сливающийся с темой
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15FFFFFF")) 
                cornerRadius = 30f
                setStroke(1, Color.parseColor("#10FFFFFF"))
            }
            setPadding(50, 30, 50, 30)
            
            layoutParams = LinearLayout.LayoutParams(
                if (isTvMode) 400 else 600, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(0, 20, 0, 0)
            }
        }

        timerLabel = TextView(this).apply {
            text = "BPЕМЯ"
            textSize = 9f
            setTextColor(Color.parseColor("#60FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        timerValue = TextView(this).apply {
            text = "00:00:00"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 15)
        }
        
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(100, 2).apply {
                setMargins(0, 0, 0, 15)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(Color.parseColor("#10FFFFFF"))
        }

        trafficLabel = TextView(this).apply {
            text = "ДАННЫЕ"
            textSize = 9f
            setTextColor(Color.parseColor("#60FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        trafficValue = TextView(this).apply {
            text = "0 B"
            textSize = 14f
            setTextColor(Color.parseColor("#D0D0D0"))
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 0)
        }

        statsContainer.addView(timerLabel)
        statsContainer.addView(timerValue)
        statsContainer.addView(divider)
        statsContainer.addView(trafficLabel)
        statsContainer.addView(trafficValue)

        proxyAddress = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#20FFFFFF"))
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 40
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
            
            powerButton.postDelayed({ powerButton.isClickable = true }, 600)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        cleanUpInvalidState()
        ShortcutUtils.update(this)
    }

    override fun onResume() {
        super.onResume()
        
        // Исправление бага: проверяем валидность сессии при входе
        if (appStatus.first == AppStatus.Running) {
            validateAndRestoreSession()
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
            startNewSession()
        }
    }

    private fun cleanUpInvalidState() {
        if (appStatus.first != AppStatus.Running) {
             getPreferences().edit()
                .remove("session_start_ts")
                .remove("session_rx_start")
                .remove("session_tx_start")
                .apply()
        }
    }

    private fun start() {
        if (appStatus.first == AppStatus.Running) return
        
        // Важно: Сначала сохраняем нули, чтобы UI не прыгнул
        startNewSession()

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

    private fun startNewSession() {
        // Захватываем текущие показания как "нулевую точку"
        startTimestamp = SystemClock.elapsedRealtime()
        startRx = TrafficStats.getUidRxBytes(Process.myUid())
        startTx = TrafficStats.getUidTxBytes(Process.myUid())

        // Защита от багов системы
        if (startRx == TrafficStats.UNSUPPORTED.toLong()) startRx = 0
        if (startTx == TrafficStats.UNSUPPORTED.toLong()) startTx = 0
        
        getPreferences().edit()
            .putLong("session_start_ts", startTimestamp)
            .putLong("session_rx_start", startRx)
            .putLong("session_tx_start", startTx)
            .apply()
            
        // Мгновенный сброс UI
        updateStats()
    }

    private fun stop() {
        ServiceManager.stop(this)
        cleanUpInvalidState()
        resetStatsUI()
    }

    private fun validateAndRestoreSession() {
        val prefs = getPreferences()
        val now = SystemClock.elapsedRealtime()
        
        if (prefs.contains("session_start_ts")) {
            val savedTs = prefs.getLong("session_start_ts", 0)
            
            // Защита от бага "60 дней": если сохраненное время неадекватно (0) или из будущего
            if (savedTs <= 0 || savedTs > now) {
                startNewSession() // Перезапускаем сессию тихо
            } else {
                startTimestamp = savedTs
                startRx = prefs.getLong("session_rx_start", 0)
                startTx = prefs.getLong("session_tx_start", 0)
            }
        } else {
            // Если статус Running, но данных нет - создаем новые
            startNewSession()
        }
    }

    private fun updateStats() {
        // Если метка времени 0 (баг), не считаем
        if (startTimestamp == 0L) {
            timerValue.text = "00:00:00"
            return
        }

        val now = SystemClock.elapsedRealtime()
        val duration = now - startTimestamp
        
        // Финальная защита от отрицательного времени
        val safeDuration = if (duration < 0) 0 else duration
        
        val seconds = (safeDuration / 1000) % 60
        val minutes = (safeDuration / (1000 * 60)) % 60
        val hours = (safeDuration / (1000 * 60 * 60))
        timerValue.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid())
        
        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
            // Считаем дельту
            var rx = currentRx - startRx
            var tx = currentTx - startTx
            
            // Защита от отрицательных значений (если система сбросила счетчики)
            if (rx < 0) rx = 0
            if (tx < 0) tx = 0
            
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
        val strokeColor = if (isRunning) Color.parseColor("#402ECC71") else Color.parseColor("#20FFFFFF")
        
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(if (isRunning) 6 else 4, strokeColor)
        }
        
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isRunning) Color.parseColor("#202ECC71") else Color.parseColor("#10FFFFFF"))
            setStroke(if (isRunning) 6 else 4, strokeColor)
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
            statusText.text = "АКТИВНО"
            statusText.setTextColor(Color.parseColor("#40E0D0")) // Бирюзовый
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            gradient.setDither(true)
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(true)
            
        } else {
            statusText.text = "ГОТОВО"
            statusText.setTextColor(Color.parseColor("#808080"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            gradient.setDither(true)
            mainContainer.background = gradient
            
            powerButton.background = createButtonBackground(false)
            resetStatsUI()
        }
    }
}
