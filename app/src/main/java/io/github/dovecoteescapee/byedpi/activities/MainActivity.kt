package io.github.dovecoteescapee.byedpi. activities

import android. Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android. content.Intent
import android.content.IntentFilter
import android. content.pm.PackageManager
import android. graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics. drawable. Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os. Bundle
import android.view. Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var mainLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var powerButton: ImageButton
    private lateinit var proxyAddress: TextView
    private var hasAutoStarted = false
    private var isAnimating = false

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3
        private const val ANIMATION_DURATION = 1200L

        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#5A5280"),
            Color.parseColor("#514A75"),
            Color.parseColor("#48426A"),
            Color.parseColor("#3F3A5F"),
            Color.parseColor("#363254"),
            Color.parseColor("#2D2A49"),
            Color.parseColor("#24223E"),
            Color.parseColor("#1B1933"),
            Color.parseColor("#121128"),
            Color.parseColor("#0A091D"),
            Color.parseColor("#050412"),
            Color.parseColor("#020101")
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#22845C"),
            Color.parseColor("#1E7856"),
            Color.parseColor("#1A6C50"),
            Color.parseColor("#16604A"),
            Color.parseColor("#125444"),
            Color.parseColor("#0E483E"),
            Color.parseColor("#0A3C38"),
            Color.parseColor("#063032"),
            Color.parseColor("#04242C"),
            Color.parseColor("#021826"),
            Color.parseColor("#010C20"),
            Color.parseColor("#000505")
        )

        private fun collectLogs(): String?  =
            try {
                Runtime.getRuntime().exec("logcat *:D -d").inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) { null }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                if (! isAnimating) updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager. LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#020101")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES. P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        statusText = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#B8B8B8"))
            gravity = Gravity.CENTER
            setPadding(0, 250, 0, 0) 
        }

        powerButton = ImageButton(this).apply {
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 8f
                        isAntiAlias = true
                        strokeCap = Paint.Cap. ROUND
                        setShadowLayer(15f, 0f, 0f, Color.parseColor("#A0000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3. 5f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 20f, 320f, false, paint)
                    canvas. drawLine(cx, cy - r, cx, cy - r * 0.4f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat. TRANSLUCENT
            }
            setImageDrawable(icon)
            
            val params = LinearLayout. LayoutParams(480, 480)
            params.topMargin = 180
            layoutParams = params
        }

        proxyAddress = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#38FFFFFF"))
            gravity = Gravity. CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout. LayoutParams.WRAP_CONTENT)
            params.topMargin = 150
            layoutParams = params
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES. TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        powerButton.setOnClickListener {
            if (!isAnimating) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                powerButton.isClickable = false
                hasAutoStarted = true
                val (status, _) = appStatus
                when (status) {
                    AppStatus. Halted -> {
                        isAnimating = true
                        animateTransition(toEnabled = true) {
                            start()
                        }
                    }
                    AppStatus.Running -> {
                        isAnimating = true
                        animateTransition(toEnabled = false) {
                            stop()
                        }
                    }
                }
                powerButton.postDelayed({ powerButton.isClickable = true }, ANIMATION_DURATION + 500)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES. TIRAMISU && 
            checkSelfPermission(Manifest. permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest. permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && !hasAutoStarted && appStatus. first == AppStatus. Halted) {
            hasAutoStarted = true
            powerButton.postDelayed({ start() }, 300) 
        }
    }

    override fun onResume() {
        super.onResume()
        if (! isAnimating) updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            ServiceManager.start(this, Mode. VPN)
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
                    ServiceManager.start(this, Mode. VPN)
                }
            }
            Mode.Proxy -> ServiceManager.start(this, Mode.Proxy)
        }
    }

    private fun stop() {
        ServiceManager.stop(this)
    }

    private fun animateTransition(toEnabled: Boolean, onComplete: () -> Unit) {
        val startTime = System.currentTimeMillis()
        val animator = object :  Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed. toFloat() / ANIMATION_DURATION).coerceIn(0f, 1f)

                val easeProgress = if (toEnabled) {
                    DecelerateInterpolator().getInterpolation(progress)
                } else {
                    AccelerateInterpolator().getInterpolation(progress)
                }

                updateStatusWithProgress(toEnabled, easeProgress)

                if (progress < 1f) {
                    mainLayout.postDelayed(this, 16)
                } else {
                    isAnimating = false
                    updateStatus()
                    onComplete()
                }
            }
        }
        animator.run()
    }

    private fun updateStatusWithProgress(toEnabled: Boolean, progress: Float) {
        val targetColors = if (toEnabled) ON_COLORS else OFF_COLORS
        val sourceColors = if (toEnabled) OFF_COLORS else ON_COLORS

        val interpolatedColors = IntArray(targetColors.size) { index ->
            val sourceColor = sourceColors. getOrElse(index) { Color.parseColor("#020101") }
            val targetColor = targetColors.getOrElse(index) { Color.parseColor("#020101") }
            
            val sA = (sourceColor shr 24) and 0xFF
            val sR = (sourceColor shr 16) and 0xFF
            val sG = (sourceColor shr 8) and 0xFF
            val sB = sourceColor and 0xFF

            val tA = (targetColor shr 24) and 0xFF
            val tR = (targetColor shr 16) and 0xFF
            val tG = (targetColor shr 8) and 0xFF
            val tB = targetColor and 0xFF

            val iA = (sA + (tA - sA) * progress).toInt()
            val iR = (sR + (tR - sR) * progress).toInt()
            val iG = (sG + (tG - sG) * progress).toInt()
            val iB = (sB + (tB - sB) * progress).toInt()

            (iA shl 24) or (iR shl 16) or (iG shl 8) or iB
        }

        val gradient = GradientDrawable(
            GradientDrawable. Orientation.TOP_BOTTOM,
            interpolatedColors
        )
        mainLayout.background = gradient

        val statusTextColor = if (toEnabled) {
            interpolateColor(Color.parseColor("#B8B8B8"), Color.parseColor("#2ECC71"), progress)
        } else {
            interpolateColor(Color.parseColor("#2ECC71"), Color.parseColor("#B8B8B8"), progress)
        }
        statusText.setTextColor(statusTextColor)

        val statusTextVal = if (toEnabled) "Подключён" else "Нет связи"
        statusText.text = statusTextVal

        val buttonColor = if (toEnabled) {
            interpolateColor(Color. parseColor("#40FFFFFF"), Color.parseColor("#2ECC71"), progress)
        } else {
            interpolateColor(Color.parseColor("#2ECC71"), Color.parseColor("#40FFFFFF"), progress)
        }

        val buttonGlowColor = if (toEnabled) {
            interpolateColor(Color.parseColor("#35FFFFFF"), Color.parseColor("#1A2ECC71"), progress)
        } else {
            interpolateColor(Color.parseColor("#1A2ECC71"), Color.parseColor("#35FFFFFF"), progress)
        }

        val glow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(buttonGlowColor)
            setStroke(6, buttonColor)
        }

        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val pressedColor = if (toEnabled) {
                interpolateColor(Color. parseColor("#25FFFFFF"), Color.parseColor("#352ECC71"), progress)
            } else {
                interpolateColor(Color.parseColor("#352ECC71"), Color.parseColor("#25FFFFFF"), progress)
            }
            setColor(pressedColor)
            setStroke(6, buttonColor)
        }

        val states = StateListDrawable()
        states.addState(intArrayOf(android.R.attr. state_pressed), pressed)
        states.addState(intArrayOf(), glow)
        powerButton.background = states
    }

    private fun interpolateColor(colorFrom: Int, colorTo: Int, progress:  Float): Int {
        val a1 = (colorFrom shr 24) and 0xff
        val r1 = (colorFrom shr 16) and 0xff
        val g1 = (colorFrom shr 8) and 0xff
        val b1 = colorFrom and 0xff

        val a2 = (colorTo shr 24) and 0xff
        val r2 = (colorTo shr 16) and 0xff
        val g2 = (colorTo shr 8) and 0xff
        val b2 = colorTo and 0xff

        return (((a1 + (a2 - a1) * progress).toInt() shl 24) or
                ((r1 + (r2 - r1) * progress).toInt() shl 16) or
                ((g1 + (g2 - g1) * progress).toInt() shl 8) or
                (b1 + (b2 - b1) * progress).toInt())
    }

    private fun updateStatus() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            statusText.text = "Подключён"
            statusText.setTextColor(Color.parseColor("#2ECC71"))
            
            val onGradient = GradientDrawable(
                GradientDrawable. Orientation.TOP_BOTTOM,
                ON_COLORS
            )
            mainLayout.background = onGradient
            
            val glow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A2ECC71"))
                setStroke(6, Color. parseColor("#2ECC71"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#352ECC71"))
                setStroke(6, Color.parseColor("#2ECC71"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
        } else {
            statusText. text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#B8B8B8"))
            
            val offGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                OFF_COLORS
            )
            mainLayout.background = offGradient
            
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color. TRANSPARENT)
                setStroke(3, Color.parseColor("#35FFFFFF"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#25FFFFFF"))
                setStroke(3, Color. parseColor("#55FFFFFF"))
            }
            val states = StateListDrawable()
            states. addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            powerButton.background = states
        }
    }
}
