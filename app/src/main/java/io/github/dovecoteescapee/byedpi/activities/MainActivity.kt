package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*

class MainActivity : Activity() {
    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var powerButton: PowerButton
    private lateinit var proxyAddress: TextView
    private var hasAutoStarted = false
    private var currentStatus: AppStatus? = null

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3

        private val OFF_START_COLOR = Color.parseColor("#121212")
        private val OFF_END_COLOR = Color.parseColor("#1E1E2C")
        private val ON_START_COLOR = Color.parseColor("#0F2027")
        private val ON_END_COLOR = Color.parseColor("#203A43")

        private val TEXT_COLOR_OFF = Color.parseColor("#757575")
        private val TEXT_COLOR_ON = Color.parseColor("#4CAF50")
        
        private val BUTTON_COLOR_OFF = Color.parseColor("#424242")
        private val BUTTON_COLOR_ON = Color.parseColor("#00E676")
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                animateStatusUpdate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        statusText = TextView(this).apply {
            textSize = 28f
            letterSpacing = 0.1f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 100)
        }

        powerButton = PowerButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(400, 400)
        }

        proxyAddress = TextView(this).apply {
            textSize = 14f
            alpha = 0.5f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }

        mainLayout.addView(statusText)
        mainLayout.addView(powerButton)
        mainLayout.addView(proxyAddress)
        
        setContentView(mainLayout)

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
            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && !hasAutoStarted && appStatus.first == AppStatus.Halted) {
            hasAutoStarted = true
            powerButton.postDelayed({ start() }, 500) 
        }
    }

    override fun onResume() {
        super.onResume()
        animateStatusUpdate(animate = false)
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
        when (getPreferences().mode()) {
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
    }

    private fun animateStatusUpdate(animate: Boolean = true) {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (currentStatus == status) return
        currentStatus = status

        val isRunning = status == AppStatus.Running
        val targetText = if (isRunning) "ACTIVATED" else "DISCONNECTED"
        val targetTextColor = if (isRunning) TEXT_COLOR_ON else TEXT_COLOR_OFF
        val targetBgStart = if (isRunning) ON_START_COLOR else OFF_START_COLOR
        val targetBgEnd = if (isRunning) ON_END_COLOR else OFF_END_COLOR
        val targetBtnColor = if (isRunning) BUTTON_COLOR_ON else BUTTON_COLOR_OFF

        if (!animate) {
            statusText.text = targetText
            statusText.setTextColor(targetTextColor)
            mainLayout.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(targetBgStart, targetBgEnd))
            powerButton.setColor(targetBtnColor)
            powerButton.setGlowing(isRunning)
            return
        }

        statusText.animate().alpha(0f).setDuration(150).withEndAction {
            statusText.text = targetText
            statusText.setTextColor(targetTextColor)
            statusText.animate().alpha(1f).setDuration(150).start()
        }.start()

        val bgAnimator = ValueAnimator.ofFloat(0f, 1f)
        val evaluator = ArgbEvaluator()
        val currentBg = mainLayout.background as? GradientDrawable
        val startColors = if (currentBg != null && currentBg.colors != null) currentBg.colors!! else intArrayOf(OFF_START_COLOR, OFF_END_COLOR)
        
        bgAnimator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction
            val newStart = evaluator.evaluate(fraction, startColors[0], targetBgStart) as Int
            val newEnd = evaluator.evaluate(fraction, startColors[1], targetBgEnd) as Int
            mainLayout.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(newStart, newEnd))
        }
        bgAnimator.duration = 600
        bgAnimator.interpolator = AccelerateDecelerateInterpolator()
        bgAnimator.start()

        powerButton.animateColor(targetBtnColor)
        powerButton.setGlowing(isRunning)
    }

    private inner class PowerButton(context: Context) : ImageButton(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var currentColor = BUTTON_COLOR_OFF
        private var isGlowing = false
        private val glowAnimator = ValueAnimator.ofFloat(0.8f, 1.2f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { invalidate() }
        }

        init {
            background = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 12f
            paint.strokeCap = Paint.Cap.ROUND
        }

        fun setColor(color: Int) {
            currentColor = color
            invalidate()
        }

        fun animateColor(targetColor: Int) {
            val animator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
            animator.duration = 400
            animator.addUpdateListener { 
                currentColor = it.animatedValue as Int
                invalidate()
            }
            animator.start()
        }

        fun setGlowing(glowing: Boolean) {
            isGlowing = glowing
            if (glowing) {
                if (!glowAnimator.isStarted) glowAnimator.start()
            } else {
                glowAnimator.cancel()
                scaleX = 1f
                scaleY = 1f
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2
            val cy = h / 2
            val r = w / 3.2f

            if (isGlowing) {
                paint.setShadowLayer(30f, 0f, 0f, currentColor)
                val scale = glowAnimator.animatedValue as Float
                this.scaleX = 1f + (scale - 1f) * 0.1f
                this.scaleY = 1f + (scale - 1f) * 0.1f
            } else {
                paint.clearShadowLayer()
            }

            paint.color = currentColor
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 30f, 300f, false, paint)
            canvas.drawLine(cx, cy - r, cx, cy - r * 0.4f, paint)
        }
    }
}
