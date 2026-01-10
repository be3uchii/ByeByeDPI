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

    // UI Components
    private lateinit var uiContainer: FrameLayout
    private lateinit var uiStatusText: TextView
    private lateinit var uiPowerButton: PowerButtonView
    private lateinit var uiTimerValue: TextView
    private lateinit var uiTrafficValue: TextView
    private lateinit var uiProxyAddress: TextView
    private lateinit var uiTrafficGraph: TrafficGraphView
    private lateinit var uiThemeToggle: ImageButton

    // State
    private var isTvInterface = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Session Variables
    private var sessionStartTime: Long = 0
    private var sessionLastTime: Long = 0
    private var sessionStartRx: Long = 0
    private var sessionStartTx: Long = 0
    private var sessionLastRx: Long = 0
    private var sessionLastTx: Long = 0

    private var currentThemeGradient: IntArray? = null
    private var isDarkThemeActive = true

    // Theme Definitions
    interface AppTheme {
        val BG_OFF: IntArray
        val BG_ON: IntArray
        val STATUS_ON: Int
        val STATUS_OFF: Int
        val TEXT_MAIN: Int
        val TEXT_SUB: Int
        val TEXT_DIM: Int
        val BTN_ON_BG: Int
        val BTN_OFF_BG: Int
        val BTN_ICON: Int
        val PANEL_BG: Int
        val GRAPH_GRID: Int
        val GRAPH_DL: Int
        val GRAPH_UL: Int
    }

    private object Dark : AppTheme {
        override val BG_OFF = intArrayOf(Color.parseColor("#FF21212B"), Color.parseColor("#FF14141C"))
        override val BG_ON = intArrayOf(Color.parseColor("#FF002B22"), Color.parseColor("#FF001C16"))
        override val STATUS_ON = Color.parseColor("#FF30D9A9")
        override val STATUS_OFF = Color.parseColor("#99FFFFFF")
        override val TEXT_MAIN = Color.WHITE
        override val TEXT_SUB = Color.parseColor("#80FFFFFF")
        override val TEXT_DIM = Color.parseColor("#50FFFFFF")
        override val BTN_ON_BG = Color.parseColor("#FF00B894")
        override val BTN_OFF_BG = Color.parseColor("#20FFFFFF")
        override val BTN_ICON = Color.WHITE
        override val PANEL_BG = Color.parseColor("#15FFFFFF")
        override val GRAPH_GRID = Color.parseColor("#20FFFFFF")
        override val GRAPH_DL = Color.parseColor("#FF30D9A9")
        override val GRAPH_UL = Color.parseColor("#FF54A0FF")
    }

    private object Light : AppTheme {
        override val BG_OFF = intArrayOf(Color.parseColor("#FFE8EAF6"), Color.parseColor("#FFD9DBE9"))
        override val BG_ON = intArrayOf(Color.parseColor("#FFD9F5E5"), Color.parseColor("#FFC8EAD5"))
        override val STATUS_ON = Color.parseColor("#FF1E8A63")
        override val STATUS_OFF = Color.parseColor("#8A000000")
        override val TEXT_MAIN = Color.parseColor("#DE000000")
        override val TEXT_SUB = Color.parseColor("#8A000000")
        override val TEXT_DIM = Color.parseColor("#61000000")
        override val BTN_ON_BG = Color.parseColor("#FF26A69A")
        override val BTN_OFF_BG = Color.parseColor("#1A000000")
        override val BTN_ICON = Color.WHITE
        override val PANEL_BG = Color.parseColor("#08000000")
        override val GRAPH_GRID = Color.parseColor("#1A000000")
        override val GRAPH_DL = Color.parseColor("#FF00796B")
        override val GRAPH_UL = Color.parseColor("#FF4285F4")
    }

    companion object {
        private const val REQ_VPN = 1
        private const val REQ_NOTIF = 3
        private const val KEY_SESS_START = "session_start_ts"
        private const val KEY_SESS_RX = "session_rx_start"
        private const val KEY_SESS_TX = "session_tx_start"
        private const val KEY_THEME = "ui_theme_dark"
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                refreshUiState(true)
            }
        }
    }

    private val statsUpdater = object : Runnable {
        override fun run() {
            try {
                if (appStatus.first == AppStatus.Running) {
                    calculateStats()
                    mainHandler.postDelayed(this, 1000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            isDarkThemeActive = getPreferences().getBoolean(KEY_THEME, true)
            
            val uiManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
            isTvInterface = uiManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
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

            buildLayout()
            updateTheme(false)

            uiContainer.setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }

            val filter = IntentFilter().apply {
                addAction(STARTED_BROADCAST)
                addAction(STOPPED_BROADCAST)
                addAction(FAILED_BROADCAST)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(statusReceiver, filter)
            }

            uiPowerButton.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                uiPowerButton.isClickable = false

                val (status, _) = appStatus
                if (status == AppStatus.Halted) {
                    performStart()
                } else {
                    performStop()
                }

                uiPowerButton.postDelayed({ uiPowerButton.isClickable = true }, 1000)
            }

            uiThemeToggle.setOnClickListener {
                isDarkThemeActive = !isDarkThemeActive
                getPreferences().edit().putBoolean(KEY_THEME, isDarkThemeActive).apply()
                updateTheme(true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }

            if (appStatus.first == AppStatus.Running) {
                loadSession()
            } else {
                resetSessionUI()
            }

            ShortcutUtils.update(this)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback safe init if needed
        }
    }

    private fun buildLayout() {
        uiContainer = FrameLayout(this)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topPad = if (isTvInterface) 40 else 80
        uiStatusText = TextView(this).apply {
            textSize = if (isTvInterface) 16f else 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, topPad, 0, 0)
            letterSpacing = 0.15f
        }

        val btnDim = if (isTvInterface) 220 else 360
        val btnMarg = if (isTvInterface) 30 else 80

        uiPowerButton = PowerButtonView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = isTvInterface
            layoutParams = LinearLayout.LayoutParams(btnDim, btnDim).apply {
                gravity = Gravity.CENTER
                topMargin = btnMarg
                bottomMargin = btnMarg
            }
        }

        val statsW = if (isTvInterface) 400 else 600
        val statsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 40, 60, 40)
            layoutParams = LinearLayout.LayoutParams(statsW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        uiTimerValue = TextView(this).apply {
            textSize = 34f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val lblTimer = TextView(this).apply {
            text = "СЕССИЯ"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        uiTrafficValue = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val lblTraffic = TextView(this).apply {
            text = "ТРАФИК"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val rowStats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }

        val colTimer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(uiTimerValue)
            addView(lblTimer)
        }

        val colTraffic = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(uiTrafficValue)
            addView(lblTraffic)
        }

        rowStats.addView(colTimer)
        rowStats.addView(colTraffic)
        statsBox.addView(rowStats)

        uiTrafficGraph = TrafficGraphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (isTvInterface) 80 else 120).apply { topMargin = 40 }
        }
        statsBox.addView(uiTrafficGraph)

        uiProxyAddress = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 40 }
        }

        val headerFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            addView(uiStatusText)
        }

        uiThemeToggle = ImageButton(this).apply {
            val p = 32
            setPadding(p, p, p, p)
            background = null
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = topPad - 20
                rightMargin = 16
            }
        }
        headerFrame.addView(uiThemeToggle)

        mainLayout.addView(headerFrame)
        mainLayout.addView(uiPowerButton)
        mainLayout.addView(statsBox)
        mainLayout.addView(uiProxyAddress)
        uiContainer.addView(mainLayout)
        setContentView(uiContainer)
    }

    private fun updateTheme(anim: Boolean) {
        val theme: AppTheme = if (isDarkThemeActive) Dark else Light
        val bgColors = if (appStatus.first == AppStatus.Running) theme.BG_ON else theme.BG_OFF
        val isLightMode = !isDarkThemeActive

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = window.decorView.systemUiVisibility
            flags = if (isLightMode) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            flags = if (isLightMode) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            window.decorView.systemUiVisibility = flags
        } else if (isLightMode) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (anim && currentThemeGradient != null) {
            runBgAnim(currentThemeGradient!!, bgColors)
        } else {
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setDither(true)
            }
            uiContainer.background = gd
            currentThemeGradient = bgColors
        }

        uiStatusText.setTextColor(if (appStatus.first == AppStatus.Running) theme.STATUS_ON else theme.STATUS_OFF)
        uiTimerValue.setTextColor(theme.TEXT_MAIN)
        uiTrafficValue.setTextColor(theme.TEXT_MAIN)
        
        (uiTimerValue.parent as? LinearLayout)?.getChildAt(1)?.let { (it as TextView).setTextColor(theme.TEXT_SUB) }
        (uiTrafficValue.parent as? LinearLayout)?.getChildAt(1)?.let { (it as TextView).setTextColor(theme.TEXT_SUB) }
        
        uiProxyAddress.setTextColor(theme.TEXT_DIM)

        (uiPowerButton.parent.parent as LinearLayout).getChildAt(2).background = GradientDrawable().apply {
            setColor(theme.PANEL_BG)
            cornerRadius = 50f
        }

        uiPowerButton.applyColors(
            if (appStatus.first == AppStatus.Running) theme.BTN_ON_BG else theme.BTN_OFF_BG,
            theme.BTN_ICON,
            anim
        )
        uiTrafficGraph.applyColors(theme.GRAPH_GRID, theme.GRAPH_DL, theme.GRAPH_UL)
        uiThemeToggle.setImageDrawable(ThemeIcon(theme.TEXT_SUB, isDarkThemeActive))
    }

    private fun runBgAnim(from: IntArray, to: IntArray) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener {
                val f = it.animatedValue as Float
                val c = intArrayOf(
                    ArgbEvaluator().evaluate(f, from[0], to[0]) as Int,
                    ArgbEvaluator().evaluate(f, from[1], to[1]) as Int
                )
                val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, c).apply {
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    setDither(true)
                }
                uiContainer.background = gd
            }
            start()
        }
        currentThemeGradient = to
    }

    override fun onResume() {
        super.onResume()
        try {
            if (appStatus.first == AppStatus.Running) {
                loadSession()
                mainHandler.removeCallbacks(statsUpdater)
                mainHandler.post(statsUpdater)
            } else {
                resetSessionUI()
            }
            refreshUiState(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(statsUpdater)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(statusReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_VPN && res == RESULT_OK) {
            try {
                ServiceManager.start(applicationContext, Mode.VPN)
                newSession()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun performStart() {
        if (appStatus.first == AppStatus.Running) return
        try {
            newSession()
            val p = getPreferences()
            when (p.mode()) {
                Mode.VPN -> {
                    val i = VpnService.prepare(this)
                    if (i != null) {
                        startActivityForResult(i, REQ_VPN)
                    } else {
                        ServiceManager.start(applicationContext, Mode.VPN)
                    }
                }
                Mode.Proxy -> ServiceManager.start(applicationContext, Mode.Proxy)
            }
            mainHandler.removeCallbacks(statsUpdater)
            mainHandler.post(statsUpdater)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun performStop() {
        try {
            ServiceManager.stop(applicationContext)
            clearSession()
            resetSessionUI()
            mainHandler.removeCallbacks(statsUpdater)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun newSession() {
        val now = SystemClock.elapsedRealtime()
        val uid = Process.myUid()
        sessionStartTime = now
        sessionLastTime = now
        sessionStartRx = TrafficStats.getUidRxBytes(uid)
        sessionStartTx = TrafficStats.getUidTxBytes(uid)
        sessionLastRx = sessionStartRx
        sessionLastTx = sessionStartTx

        if (sessionStartRx < 0) sessionStartRx = 0
        if (sessionStartTx < 0) sessionStartTx = 0

        getPreferences().edit()
            .putLong(KEY_SESS_START, sessionStartTime)
            .putLong(KEY_SESS_RX, sessionStartRx)
            .putLong(KEY_SESS_TX, sessionStartTx)
            .apply()

        uiTimerValue.text = "00:00:00"
        uiTrafficValue.text = "↓ 0 B   ↑ 0 B"
        uiTrafficGraph.reset()
    }

    private fun loadSession() {
        val p = getPreferences()
        val now = SystemClock.elapsedRealtime()
        val uid = Process.myUid()

        if (p.contains(KEY_SESS_START)) {
            sessionStartTime = p.getLong(KEY_SESS_START, now)
            sessionStartRx = p.getLong(KEY_SESS_RX, 0)
            sessionStartTx = p.getLong(KEY_SESS_TX, 0)

            if (sessionStartTime > now || (now - sessionStartTime) > 31536000000L) {
                newSession()
            }
        } else {
            newSession()
        }
        sessionLastTime = now
        sessionLastRx = TrafficStats.getUidRxBytes(uid)
        sessionLastTx = TrafficStats.getUidTxBytes(uid)
    }

    private fun clearSession() {
        getPreferences().edit()
            .remove(KEY_SESS_START)
            .remove(KEY_SESS_RX)
            .remove(KEY_SESS_TX)
            .apply()
    }

    private fun calculateStats() {
        val now = SystemClock.elapsedRealtime()

        if (sessionStartTime == 0L || sessionStartTime > now) {
            uiTimerValue.text = "00:00:00"
            return
        }

        var dur = now - sessionStartTime
        if (dur < 0) dur = 0

        val s = (dur / 1000) % 60
        val m = (dur / (1000 * 60)) % 60
        val h = (dur / (1000 * 60 * 60))
        uiTimerValue.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

        val curRx = TrafficStats.getUidRxBytes(Process.myUid())
        val curTx = TrafficStats.getUidTxBytes(Process.myUid())

        var totRx = 0L
        var totTx = 0L
        var dSpd = 0f
        var uSpd = 0f

        if (curRx != TrafficStats.UNSUPPORTED.toLong() && curTx != TrafficStats.UNSUPPORTED.toLong()) {
            if (curRx < sessionStartRx) sessionStartRx = curRx
            if (curTx < sessionStartTx) sessionStartTx = curTx

            totRx = max(0L, curRx - sessionStartRx)
            totTx = max(0L, curTx - sessionStartTx)

            val dt = (now - sessionLastTime) / 1000.0f
            if (dt > 0) {
                dSpd = max(0f, (curRx - sessionLastRx).toFloat()) / dt
                uSpd = max(0f, (curTx - sessionLastTx).toFloat()) / dt
            }
        }

        uiTrafficValue.text = "↓ ${fmtBytes(totRx)}   ↑ ${fmtBytes(totTx)}"
        uiTrafficGraph.pushData(dSpd, uSpd)
        sessionLastTime = now
        sessionLastRx = curRx
        sessionLastTx = curTx
    }

    private fun resetSessionUI() {
        sessionStartTime = 0
        sessionStartRx = 0
        sessionStartTx = 0
        uiTimerValue.text = "00:00:00"
        uiTrafficValue.text = "↓ 0 B   ↑ 0 B"
        uiTrafficGraph.reset()
    }

    private fun fmtBytes(b: Long): String {
        if (b < 0) return "0 B"
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        val g = (Math.log10(b.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", b / Math.pow(1024.0, g.toDouble()), u[g - 1])
    }

    private fun fmtSpeed(bps: Float): String {
        if (bps < 1024) return "${bps.toInt()} B/s"
        val u = arrayOf("KB/s", "MB/s", "GB/s")
        val g = (Math.log10(bps.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bps / Math.pow(1024.0, g.toDouble()), u[g - 1])
    }

    private fun refreshUiState(anim: Boolean) {
        try {
            val (st, _) = appStatus
            val p = getPreferences()
            val (ip, port) = p.getProxyIpAndPort()
            uiProxyAddress.text = "$ip:$port"

            val running = st == AppStatus.Running
            uiPowerButton.setPowerState(running, anim)

            if (running) {
                uiStatusText.text = "АКТИВНО"
                if (sessionStartTime == 0L) loadSession()
                mainHandler.removeCallbacks(statsUpdater)
                mainHandler.post(statsUpdater)
            } else {
                uiStatusText.text = "НЕ ПОДКЛЮЧЕНО"
                resetSessionUI()
                mainHandler.removeCallbacks(statsUpdater)
            }
            updateTheme(anim)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ViewConstructor")
    inner class PowerButtonView(ctx: Context) : View(ctx) {
        private val pBg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private var curBg = 0
        private var prog = 0f
        private val pathIcon = Path()
        private val pathCheck = Path()

        fun setPowerState(active: Boolean, anim: Boolean) {
            val t = if (active) 1f else 0f
            if (anim) {
                ValueAnimator.ofFloat(prog, t).apply {
                    duration = 400
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        prog = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                prog = t
                invalidate()
            }
        }

        fun applyColors(bg: Int, icon: Int, anim: Boolean) {
            pIcon.color = icon
            if (anim) {
                ValueAnimator.ofObject(ArgbEvaluator(), curBg, bg).apply {
                    duration = 600
                    addUpdateListener {
                        curBg = it.animatedValue as Int
                        invalidate()
                    }
                    start()
                }
            } else {
                curBg = bg
                invalidate()
            }
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2
            val r = w / 2

            pBg.color = curBg
            c.drawCircle(cx, cy, r, pBg)
            pIcon.strokeWidth = w / 20f

            val ir = w / 3.2f
            val sa = 110f + 110f * prog
            val sw = 320f - 220f * prog

            pathIcon.reset()
            pathIcon.addArc(RectF(cx - ir, cy - ir, cx + ir, cy + ir), sa, sw)
            c.drawPath(pathIcon, pIcon)

            val ly1 = cy - ir
            val ly2 = cy - ir + (ir * 0.65f)
            
            pathCheck.reset()
            val cr = ir * 0.8f
            pathCheck.moveTo(cx - cr / 2, cy)
            pathCheck.lineTo(cx - cr / 6, cy + cr / 3)
            pathCheck.lineTo(cx + cr / 2, cy - cr / 4)

            if (prog < 1f) {
                c.save()
                c.rotate(360 * prog, cx, cy)
                c.drawLine(cx, ly1, cx, ly2, pIcon)
                c.restore()
            }

            if (prog > 0f) {
                pIcon.alpha = (255 * prog).toInt()
                c.drawPath(pathCheck, pIcon)
                pIcon.alpha = 255
            }
        }
    }

    @SuppressLint("ViewConstructor")
    inner class TrafficGraphView(ctx: Context) : View(ctx) {
        private val qD = ArrayDeque<Float>()
        private val qU = ArrayDeque<Float>()
        private var maxVal = 1024f
        private val count = 60
        private val pG = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val pD = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val pU = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val pT = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }

        init {
            for (i in 0 until count) { qD.addLast(0f); qU.addLast(0f) }
        }

        fun applyColors(g: Int, d: Int, u: Int) {
            pG.color = g
            pD.color = d
            pU.color = u
            pT.color = ColorUtils.setAlphaComponent(g, 128)
            invalidate()
        }

        fun pushData(d: Float, u: Float) {
            qD.removeFirst(); qD.addLast(d)
            qU.removeFirst(); qU.addLast(u)
            val m = max(qD.maxOrNull() ?: 0f, qU.maxOrNull() ?: 0f)
            maxVal = max(maxVal * 0.95f, max(m, 1024f))
            invalidate()
        }

        fun reset() {
            for (i in 0 until count) { qD[i] = 0f; qU[i] = 0f }
            maxVal = 1024f
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat()
            val h = height.toFloat()
            pD.strokeWidth = 4f
            pU.strokeWidth = 4f
            pG.strokeWidth = 1f

            val sx = w / (count - 1)
            c.drawLine(0f, 0f, w, 0f, pG)
            c.drawLine(0f, h, w, h, pG)
            c.drawText(fmtSpeed(maxVal), 10f, pT.textSize + 5, pT)
            c.drawText("0 B/s", 10f, h - 5, pT)

            val pd = Path(); val pu = Path()
            qD.forEachIndexed { i, v -> val x = i * sx; val y = h - (v / maxVal * h); if (i == 0) pd.moveTo(x, y) else pd.lineTo(x, y) }
            qU.forEachIndexed { i, v -> val x = i * sx; val y = h - (v / maxVal * h); if (i == 0) pu.moveTo(x, y) else pu.lineTo(x, y) }
            c.drawPath(pd, pD)
            c.drawPath(pu, pU)
        }
    }

    inner class ThemeIcon(c: Int, private val dark: Boolean) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE }
        private val pS = Path()
        private val pM = Path()

        override fun draw(cv: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = w / 2
            val cy = h / 2
            p.strokeWidth = w / 12f
            val r = min(w, h) / 4f

            cv.save()
            cv.rotate(if (dark) 0f else 180f, cx, cy)
            
            // Draw Sun rays or Moon shape safely using Path.Op if available, or simple shape
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                 pM.reset()
                 pM.addCircle(cx, cy, r, Path.Direction.CW)
                 val cut = Path()
                 cut.addCircle(cx - r / 2, cy - r / 2, r, Path.Direction.CW)
                 pM.op(cut, Path.Op.DIFFERENCE)
                 
                 pS.reset()
                 pS.addCircle(cx, cy, r, Path.Direction.CW)
                 
                 p.style = Paint.Style.FILL
                 cv.drawPath(if (dark) pS else pM, p)
            } else {
                 // Fallback for very old devices
                 cv.drawCircle(cx, cy, r, p)
            }
            cv.restore()
        }

        override fun setAlpha(a: Int) { p.alpha = a }
        override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
