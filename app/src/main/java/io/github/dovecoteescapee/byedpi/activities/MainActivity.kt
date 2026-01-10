package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {

    private lateinit var mainContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var powerButton: PowerButtonView
    private lateinit var timerValue: TextView
    private lateinit var trafficValue: TextView
    private lateinit var proxyAddress: TextView
    private lateinit var trafficGraphView: TrafficGraphView
    private lateinit var themeToggleButton: ImageButton

    private var isTvMode = false
    private val handler = Handler(Looper.getMainLooper())

    private var startTimestamp: Long = 0
    private var lastTimestamp: Long = 0
    private var startRx: Long = 0
    private var startTx: Long = 0
    private var lastRx: Long = 0
    private var lastTx: Long = 0

    private var currentGradientColors: IntArray? = null
    private var isDarkTheme = true

    interface ThemeColors {
        val BG_OFF: IntArray
        val BG_ON: IntArray
        val STATUS_ON: Int
        val STATUS_OFF: Int
        val TEXT_PRIMARY: Int
        val TEXT_SECONDARY: Int
        val TEXT_TERTIARY: Int
        val POWER_BUTTON_ON_BG: Int
        val POWER_BUTTON_OFF_BG: Int
        val POWER_BUTTON_ICON: Int
        val STATS_BG: Int
        val DIVIDER: Int
        val GRAPH_GRID: Int
        val GRAPH_DOWN: Int
        val GRAPH_UP: Int
    }

    private object DarkTheme : ThemeColors {
        override val BG_OFF = intArrayOf(Color.parseColor("#FF21212B"), Color.parseColor("#FF14141C"))
        override val BG_ON = intArrayOf(Color.parseColor("#FF002B22"), Color.parseColor("#FF001C16"))
        override val STATUS_ON = Color.parseColor("#FF30D9A9")
        override val STATUS_OFF = Color.parseColor("#99FFFFFF")
        override val TEXT_PRIMARY = Color.WHITE
        override val TEXT_SECONDARY = Color.parseColor("#80FFFFFF")
        override val TEXT_TERTIARY = Color.parseColor("#50FFFFFF")
        override val POWER_BUTTON_ON_BG = Color.parseColor("#FF00B894")
        override val POWER_BUTTON_OFF_BG = Color.parseColor("#20FFFFFF")
        override val POWER_BUTTON_ICON = Color.WHITE
        override val STATS_BG = Color.parseColor("#15FFFFFF")
        override val DIVIDER = Color.parseColor("#20FFFFFF")
        override val GRAPH_GRID = Color.parseColor("#20FFFFFF")
        override val GRAPH_DOWN = Color.parseColor("#FF30D9A9")
        override val GRAPH_UP = Color.parseColor("#FF54A0FF")
    }

    private object LightTheme : ThemeColors {
        override val BG_OFF = intArrayOf(Color.parseColor("#FFE8EAF6"), Color.parseColor("#FFD9DBE9"))
        override val BG_ON = intArrayOf(Color.parseColor("#FFD9F5E5"), Color.parseColor("#FFC8EAD5"))
        override val STATUS_ON = Color.parseColor("#FF1E8A63")
        override val STATUS_OFF = Color.parseColor("#8A000000")
        override val TEXT_PRIMARY = Color.parseColor("#DE000000")
        override val TEXT_SECONDARY = Color.parseColor("#8A000000")
        override val TEXT_TERTIARY = Color.parseColor("#61000000")
        override val POWER_BUTTON_ON_BG = Color.parseColor("#FF26A69A")
        override val POWER_BUTTON_OFF_BG = Color.parseColor("#1A000000")
        override val POWER_BUTTON_ICON = Color.WHITE
        override val STATS_BG = Color.parseColor("#08000000")
        override val DIVIDER = Color.parseColor("#1A000000")
        override val GRAPH_GRID = Color.parseColor("#1A000000")
        override val GRAPH_DOWN = Color.parseColor("#FF00796B")
        override val GRAPH_UP = Color.parseColor("#FF4285F4")
    }

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_NOTIFICATIONS = 3

        private const val PREF_SESSION_START = "session_start_ts"
        private const val PREF_SESSION_RX = "session_rx_start"
        private const val PREF_SESSION_TX = "session_tx_start"
        private const val PREF_UI_THEME = "ui_theme_dark"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                updateUIState(true)
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

        isDarkTheme = getPreferences().getBoolean(PREF_UI_THEME, true)

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTvMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setupUI()
        applyTheme(false)

        mainContainer.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            powerButton.isClickable = false

            val (status, _) = appStatus
            if (status == AppStatus.Halted) {
                startVpn()
            } else {
                stopVpn()
            }

            powerButton.postDelayed({ powerButton.isClickable = true }, 1000)
        }

        themeToggleButton.setOnClickListener {
            isDarkTheme = !isDarkTheme
            getPreferences().edit().putBoolean(PREF_UI_THEME, isDarkTheme).apply()
            applyTheme(true)
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

    private fun setupUI() {
        mainContainer = FrameLayout(this)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topMargin = if (isTvMode) 40 else 80
        statusText = TextView(this).apply {
            textSize = if (isTvMode) 16f else 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, topMargin, 0, 0)
            letterSpacing = 0.15f
        }

        val btnSize = if (isTvMode) 220 else 360
        val btnMargin = if (isTvMode) 30 else 80

        powerButton = PowerButtonView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvMode
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
                topMargin = btnMargin
                bottomMargin = btnMargin
            }
        }

        val statsWidth = if (isTvMode) 400 else 600
        val statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 40, 60, 40)
            layoutParams = LinearLayout.LayoutParams(statsWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        timerValue = TextView(this).apply {
            textSize = 34f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val timerLabel = TextView(this).apply {
            text = "СЕССИЯ"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        trafficValue = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val trafficLabel = TextView(this).apply {
            text = "ТРАФИК"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val topStatsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            weightSum = 2f
        }

        val timerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(timerValue)
            addView(timerLabel)
        }

        val trafficLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(trafficValue)
            addView(trafficLabel)
        }

        topStatsRow.addView(timerLayout)
        topStatsRow.addView(trafficLayout)
        statsContainer.addView(topStatsRow)

        trafficGraphView = TrafficGraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, if (isTvMode) 80 else 120
            ).apply { topMargin = 40 }
        }
        statsContainer.addView(trafficGraphView)

        proxyAddress = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 40 }
        }

        val headerLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            addView(statusText)
        }

        themeToggleButton = ImageButton(this).apply {
            val padding = 32
            setPadding(padding, padding, padding, padding)
            background = null
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = topMargin - 20
                rightMargin = 16
            }
        }
        headerLayout.addView(themeToggleButton)

        contentLayout.addView(headerLayout)
        contentLayout.addView(powerButton)
        contentLayout.addView(statsContainer)
        contentLayout.addView(proxyAddress)
        mainContainer.addView(contentLayout)
        setContentView(mainContainer)
    }

    private fun applyTheme(animated: Boolean) {
        val colors: ThemeColors = if (isDarkTheme) DarkTheme else LightTheme
        val bgColors = if (appStatus.first == AppStatus.Running) colors.BG_ON else colors.BG_OFF
        val statusBarIsLight = !isDarkTheme
        val navBarIsLight = !isDarkTheme

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = window.decorView.systemUiVisibility
            flags = if (statusBarIsLight) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            flags = if (navBarIsLight) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            window.decorView.systemUiVisibility = flags
        } else if (!isDarkTheme) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (animated && currentGradientColors != null) {
            animateBackground(currentGradientColors!!, bgColors)
        } else {
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setDither(true)
            }
            mainContainer.background = gradient
            currentGradientColors = bgColors
        }

        statusText.setTextColor(if (appStatus.first == AppStatus.Running) colors.STATUS_ON else colors.STATUS_OFF)
        timerValue.setTextColor(colors.TEXT_PRIMARY)
        trafficValue.setTextColor(colors.TEXT_PRIMARY)
        (timerValue.parent as View).findViewById<TextView>(timerValue.id - 1)?.setTextColor(colors.TEXT_SECONDARY)
        (trafficValue.parent as View).findViewById<TextView>(trafficValue.id - 1)?.setTextColor(colors.TEXT_SECONDARY)
        proxyAddress.setTextColor(colors.TEXT_TERTIARY)

        (powerButton.parent.parent as LinearLayout).getChildAt(2).background = GradientDrawable().apply {
            setColor(colors.STATS_BG)
            cornerRadius = 50f
        }

        powerButton.setColors(
            if (appStatus.first == AppStatus.Running) colors.POWER_BUTTON_ON_BG else colors.POWER_BUTTON_OFF_BG,
            colors.POWER_BUTTON_ICON,
            animated
        )
        trafficGraphView.setColors(colors.GRAPH_GRID, colors.GRAPH_DOWN, colors.GRAPH_UP)
        themeToggleButton.setImageDrawable(ThemeIconDrawable(colors.TEXT_SECONDARY, isDarkTheme))
    }

    private fun animateBackground(from: IntArray, to: IntArray) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener {
                val fraction = it.animatedValue as Float
                val newColors = intArrayOf(
                    ArgbEvaluator().evaluate(fraction, from[0], to[0]) as Int,
                    ArgbEvaluator().evaluate(fraction, from[1], to[1]) as Int
                )
                val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, newColors).apply {
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    setDither(true)
                }
                mainContainer.background = gradient
            }
            start()
        }
        currentGradientColors = to
    }

    override fun onResume() {
        super.onResume()
        if (appStatus.first == AppStatus.Running) {
            restoreSessionData()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        } else {
            resetStatsUI()
        }
        updateUIState(false)
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
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            ServiceManager.start(this, Mode.VPN)
            initSessionData()
        }
    }

    private fun startVpn() {
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

    private fun stopVpn() {
        ServiceManager.stop(this)
        clearSessionData()
        resetStatsUI()
        handler.removeCallbacks(updateRunnable)
    }

    private fun initSessionData() {
        val now = SystemClock.elapsedRealtime()
        val uid = Process.myUid()
        startTimestamp = now
        lastTimestamp = now
        startRx = TrafficStats.getUidRxBytes(uid)
        startTx = TrafficStats.getUidTxBytes(uid)
        lastRx = startRx
        lastTx = startTx

        if (startRx < 0) startRx = 0
        if (startTx < 0) startTx = 0

        getPreferences().edit()
            .putLong(PREF_SESSION_START, startTimestamp)
            .putLong(PREF_SESSION_RX, startRx)
            .putLong(PREF_SESSION_TX, startTx)
            .apply()

        timerValue.text = "00:00:00"
        trafficValue.text = "↓ 0 B   ↑ 0 B"
        trafficGraphView.clear()
    }

    private fun restoreSessionData() {
        val prefs = getPreferences()
        val now = SystemClock.elapsedRealtime()
        val uid = Process.myUid()

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
        lastTimestamp = now
        lastRx = TrafficStats.getUidRxBytes(uid)
        lastTx = TrafficStats.getUidTxBytes(uid)
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

        var totalRx = 0L
        var totalTx = 0L
        var downSpeed = 0f
        var upSpeed = 0f

        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && currentTx != TrafficStats.UNSUPPORTED.toLong()) {
            if (currentRx < startRx) startRx = currentRx
            if (currentTx < startTx) startTx = currentTx

            totalRx = max(0L, currentRx - startRx)
            totalTx = max(0L, currentTx - startTx)

            val timeDelta = (now - lastTimestamp) / 1000.0f
            if (timeDelta > 0) {
                downSpeed = max(0f, (currentRx - lastRx).toFloat()) / timeDelta
                upSpeed = max(0f, (currentTx - lastTx).toFloat()) / timeDelta
            }
        }

        trafficValue.text = "↓ ${formatBytes(totalRx)}   ↑ ${formatBytes(totalTx)}"
        trafficGraphView.addTraffics(downSpeed, upSpeed)
        lastTimestamp = now
        lastRx = currentRx
        lastTx = currentTx
    }

    private fun resetStatsUI() {
        startTimestamp = 0
        startRx = 0
        startTx = 0
        timerValue.text = "00:00:00"
        trafficValue.text = "↓ 0 B   ↑ 0 B"
        trafficGraphView.clear()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups - 1])
    }

    private fun formatSpeed(bytesPerSecond: Float): String {
        if (bytesPerSecond < 1024) return "${bytesPerSecond.toInt()} B/s"
        val units = arrayOf("KB/s", "MB/s", "GB/s")
        val digitGroups = (Math.log10(bytesPerSecond.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytesPerSecond / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups - 1])
    }

    private fun updateUIState(animated: Boolean) {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()
        proxyAddress.text = "$ip:$port"

        val isRunning = status == AppStatus.Running
        powerButton.setActive(isRunning, animated)

        if (isRunning) {
            statusText.text = "АКТИВНО"
            if (startTimestamp == 0L) restoreSessionData()
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        } else {
            statusText.text = "НЕ ПОДКЛЮЧЕНО"
            resetStatsUI()
            handler.removeCallbacks(updateRunnable)
        }
        applyTheme(animated)
    }

    @SuppressLint("ViewConstructor")
    inner class PowerButtonView(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private var iconPath = Path()
        private var currentBgColor: Int = 0
        private var isActive = false
        private var iconProgress = 0f

        fun setActive(active: Boolean, animated: Boolean) {
            isActive = active
            val targetProgress = if (active) 1f else 0f
            if (animated) {
                ValueAnimator.ofFloat(iconProgress, targetProgress).apply {
                    duration = 400
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        iconProgress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                iconProgress = targetProgress
                invalidate()
            }
        }

        fun setColors(bgColor: Int, iconColor: Int, animated: Boolean) {
            iconPaint.color = iconColor
            if (animated) {
                ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, bgColor).apply {
                    duration = 600
                    addUpdateListener {
                        currentBgColor = it.animatedValue as Int
                        invalidate()
                    }
                    start()
                }
            } else {
                currentBgColor = bgColor
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2
            val radius = w / 2

            bgPaint.color = currentBgColor
            canvas.drawCircle(cx, cy, radius, bgPaint)
            iconPaint.strokeWidth = w / 20f

            val r = w / 3.2f
            val lineLength = r * 0.65f
            val startAngle = 110f + 110f * iconProgress
            val sweepAngle = 320f - 220f * iconProgress

            iconPath.reset()
            iconPath.addArc(RectF(cx - r, cy - r, cx + r, cy + r), startAngle, sweepAngle)
            canvas.drawPath(iconPath, iconPaint)

            val lineYStart = cy - r
            val lineYEnd = cy - r + lineLength
            val checkMarkR = r * 0.8f
            val checkMarkPath = Path()
            checkMarkPath.moveTo(cx - checkMarkR / 2, cy)
            checkMarkPath.lineTo(cx - checkMarkR / 6, cy + checkMarkR / 3)
            checkMarkPath.lineTo(cx + checkMarkR / 2, cy - checkMarkR / 4)

            if (iconProgress < 1f) {
                canvas.save()
                canvas.rotate(360 * iconProgress, cx, cy)
                canvas.drawLine(cx, lineYStart, cx, lineYEnd, iconPaint)
                canvas.restore()
            }

            if (iconProgress > 0f) {
                iconPaint.alpha = (255 * iconProgress).toInt()
                canvas.drawPath(checkMarkPath, iconPaint)
                iconPaint.alpha = 255
            }
        }
    }

    @SuppressLint("ViewConstructor")
    inner class TrafficGraphView(context: Context) : View(context) {
        private val downTraffics = ArrayDeque<Float>()
        private val upTraffics = ArrayDeque<Float>()
        private var maxTraffic = 1024f
        private val maxPoints = 60

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val upPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }

        init {
            for (i in 0 until maxPoints) {
                downTraffics.addLast(0f)
                upTraffics.addLast(0f)
            }
        }

        fun setColors(grid: Int, down: Int, up: Int) {
            gridPaint.color = grid
            downPaint.color = down
            upPaint.color = up
            textPaint.color = ColorUtils.setAlphaComponent(grid, 128)
            invalidate()
        }

        fun addTraffics(down: Float, up: Float) {
            downTraffics.removeFirst()
            downTraffics.addLast(down)
            upTraffics.removeFirst()
            upTraffics.addLast(up)

            val currentMax = max(downTraffics.maxOrNull() ?: 0f, upTraffics.maxOrNull() ?: 0f)
            maxTraffic = max(maxTraffic * 0.95f, max(currentMax, 1024f))
            invalidate()
        }

        fun clear() {
            for (i in 0 until maxPoints) {
                downTraffics[i] = 0f
                upTraffics[i] = 0f
            }
            maxTraffic = 1024f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val strokeW = 4f
            downPaint.strokeWidth = strokeW
            upPaint.strokeWidth = strokeW
            gridPaint.strokeWidth = 1f

            val stepX = w / (maxPoints - 1)

            canvas.drawLine(0f, 0f, w, 0f, gridPaint)
            canvas.drawLine(0f, h, w, h, gridPaint)
            canvas.drawText(formatSpeed(maxTraffic), 10f, textPaint.textSize + 5, textPaint)
            canvas.drawText("0 B/s", 10f, h - 5, textPaint)

            val downPath = Path()
            val upPath = Path()
            downTraffics.forEachIndexed { i, value ->
                val x = i * stepX
                val y = h - (value / maxTraffic * h)
                if (i == 0) downPath.moveTo(x, y) else downPath.lineTo(x, y)
            }
            upTraffics.forEachIndexed { i, value ->
                val x = i * stepX
                val y = h - (value / maxTraffic * h)
                if (i == 0) upPath.moveTo(x, y) else upPath.lineTo(x, y)
            }
            canvas.drawPath(downPath, downPaint)
            canvas.drawPath(upPath, upPaint)
        }
    }

    inner class ThemeIconDrawable(color: Int, private val isDark: Boolean) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE }
        private val sunPath = Path()
        private val moonPath = Path()

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = w / 2
            val cy = h / 2
            val strokeWidth = w / 12f
            paint.strokeWidth = strokeWidth

            val radius = min(w, h) / 4f

            canvas.save()
            val rotation = if (isDark) 0f else 180f
            canvas.rotate(rotation, cx, cy)

            moonPath.reset()
            moonPath.addCircle(cx, cy, radius, Path.Direction.CW)
            moonPath.addCircle(cx - radius / 2, cy - radius / 2, radius, Path.Direction.CW)
            canvas.clipPath(moonPath)

            sunPath.reset()
            sunPath.addCircle(cx, cy, radius, Path.Direction.CW)

            paint.style = Paint.Style.FILL
            canvas.drawPath(sunPath, paint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
