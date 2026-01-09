package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
    private lateinit var timerText: TextView
    private lateinit var trafficText: TextView
    private lateinit var proxyAddress: TextView
    
    private var hasAutoStarted = false
    private var isTvMode = false
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var startRx = 0L
    private var startTx = 0L

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_NOTIFICATIONS = 3

        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#332E67"),
            Color.parseColor("#312B63"),
            Color.parseColor("#2F285F"),
            Color.parseColor("#2D255A"),
            Color.parseColor("#2A2256"),
            Color.parseColor("#261F50"),
            Color.parseColor("#221C4A"),
            Color.parseColor("#1E1944"),
            Color.parseColor("#19163D"),
            Color.parseColor("#141235"),
            Color.parseColor("#100F2D"),
            Color.parseColor("#0C0C24"),
            Color.parseColor("#08091A"),
            Color.parseColor("#050610"),
            Color.parseColor("#020308"),
            Color.BLACK
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#0F4D34"),
            Color.parseColor("#0E4932"),
            Color.parseColor("#0D4530"),
            Color.parseColor("#0C412E"),
            Color.parseColor("#0B3D2C"),
            Color.parseColor("#0A392A"),
            Color.parseColor("#093528"),
            Color.parseColor("#083025"),
            Color.parseColor("#072C23"),
            Color.parseColor("#062720"),
            Color.parseColor("#05221D"),
            Color.parseColor("#041D1A"),
            Color.parseColor("#031716"),
            Color.parseColor("#021112"),
            Color.parseColor("#010B0C"),
            Color.BLACK
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
                updateTimerAndTraffic()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || 
                   !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        // Делаем навигацию черной, чтобы сливалась с фоном
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        mainContainer = FrameLayout(this)
        
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            textSize = if (isTvMode) 18f else 22f
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            setPadding(0, if (isTvMode) 40 else 200, 0, 0) 
        }

        val btnSize = if (isTvMode) 280 else 400
        val btnMargin = if (isTvMode) 20 else 150

        powerButton = ImageButton(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvMode
            
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = if (isTvMode) 6f else 8f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(8f, 0f, 0f, Color.parseColor("#40000000"))
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
            
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10FFFFFF"))
                setStroke(3, Color.parseColor("#40FFFFFF"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#30FFFFFF"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(android.R.attr.state_focused), pressed)
            states.addState(intArrayOf(), normal)
            background = states
            
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
                topMargin = btnMargin
            }
        }

        timerText = TextView(this).apply {
            textSize = if (isTvMode) 24f else 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = "00:00:00"
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = if (isTvMode) 20 else 80
            layoutParams = params
        }

        trafficText = TextView(this).apply {
            textSize = if (isTvMode) 12f else 14f
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            text = "↓ 0 B   ↑ 0 B"
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 20
            layoutParams = params
        }

        proxyAddress = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#30FFFFFF"))
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            // Уменьшили отступ, чтобы было ближе к трафику
            params.topMargin = 40 
            layoutParams = params
        }

        contentLayout.addView(statusText)
        contentLayout.addView(powerButton)
        contentLayout.addView(timerText)
        contentLayout.addView(trafficText)
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
            
            powerButton.postDelayed({ powerButton.isClickable = true }, 300)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && !hasAutoStarted && appStatus.first == AppStatus.Halted) {
            hasAutoStarted = true
            powerButton.postDelayed({ start() }, 300) 
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
            handler.post(updateRunnable)
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
        }
    }

    private fun start() {
        if (appStatus.first == AppStatus.Running) return
        
        startTime = System.currentTimeMillis()
        startRx = TrafficStats.getUidRxBytes(Process.myUid())
        startTx = TrafficStats.getUidTxBytes(Process.myUid())
        
        val prefs = getPreferences()
        prefs.edit()
            .putLong("session_start", startTime)
            .putLong("session_rx_start", startRx)
            .putLong("session_tx_start", startTx)
            .apply()

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

    private fun stop() {
        ServiceManager.stop(this)
        getPreferences().edit()
            .remove("session_start")
            .remove("session_rx_start")
            .remove("session_tx_start")
            .apply()
        handler.removeCallbacks(updateRunnable)
        updateTimerAndTraffic(true) 
    }

    private fun restoreSessionData() {
        val prefs = getPreferences()
        startTime = prefs.getLong("session_start", System.currentTimeMillis())
        startRx = prefs.getLong("session_rx_start", TrafficStats.getUidRxBytes(Process.myUid()))
        startTx = prefs.getLong("session_tx_start", TrafficStats.getUidTxBytes(Process.myUid()))
    }

    private fun updateTimerAndTraffic(reset: Boolean = false) {
        if (reset) {
            timerText.text = "00:00:00"
            trafficText.text = "↓ 0 B   ↑ 0 B"
            return
        }
        
        val duration = System.currentTimeMillis() - startTime
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60))
        timerText.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid())
        
        // Считаем от момента запуска
        val rx = if (currentRx > startRx) currentRx - startRx else 0
        val tx = if (currentTx > startTx) currentTx - startTx else 0
        
        trafficText.text = "↓ ${formatBytes(rx)}   ↑ ${formatBytes(tx)}"
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

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            statusText.text = "Подключён"
            statusText.setTextColor(Color.parseColor("#2ECC71"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            val glow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#152ECC71"))
                setStroke(5, Color.parseColor("#2ECC71"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#302ECC71"))
                setStroke(5, Color.parseColor("#2ECC71"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(android.R.attr.state_focused), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
            if (!handler.hasMessages(0)) handler.post(updateRunnable)
            
        } else {
            statusText.text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(2, Color.parseColor("#30FFFFFF"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#15FFFFFF"))
                setStroke(2, Color.parseColor("#50FFFFFF"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(android.R.attr.state_focused), pressed)
            states.addState(intArrayOf(), normal)
            powerButton.background = states
            
            updateTimerAndTraffic(true)
        }
    }
}
