package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
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
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3

        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#E6332E67"),
            Color.parseColor("#D92F2A5E"),
            Color.parseColor("#C92D2757"),
            Color.parseColor("#B927224A"),
            Color.parseColor("#9E1E1B3A"),
            Color.parseColor("#8818162B"),
            Color.parseColor("#66110F1C"),
            Color.parseColor("#3B0B0B11"),
            Color.parseColor("#15060807")
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#E60F4D34"),
            Color.parseColor("#D70E462F"),
            Color.parseColor("#C90C3E29"),
            Color.parseColor("#B90B3323"),
            Color.parseColor("#A008291C"),
            Color.parseColor("#8A061217"),
            Color.parseColor("#52060A0A"),
            Color.parseColor("#2A060607")
        )

        private fun collectLogs(): String? =
            try {
                Runtime.getRuntime().exec("logcat *:D -d").inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) { null }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#060807")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
            setTextColor(Color.parseColor("#A0A0A0"))
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
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(10f, 0f, 0f, Color.parseColor("#80000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3.5f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 290f, 320f, false, paint)
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.4f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setImageDrawable(icon)

            val params = LinearLayout.LayoutParams(450, 450)
            params.topMargin = 200
            layoutParams = params
        }

        proxyAddress = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#40FFFFFF"))
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        powerButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            powerButton.isClickable = false
            hasAutoStarted = true
            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            powerButton.postDelayed({ powerButton.isClickable = true }, 500)
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
        updateStatus()
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

    private fun applyBackgroundSmooth(newDrawable: Drawable) {
        val current = mainLayout.background
        if (current == null) {
            mainLayout.background = newDrawable
        } else {
            val transition = android.graphics.drawable.TransitionDrawable(arrayOf(current, newDrawable))
            transition.isCrossFadeEnabled = true
            mainLayout.background = transition
            transition.startTransition(400)
        }
    }

    private fun updateStatus() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            statusText.text = "Подключён"
            statusText.setTextColor(Color.parseColor("#98E6A0"))

            val onGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                ON_COLORS
            ).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setGradientCenter(0.5f, 0.4f)
                alpha = 230
            }
            applyBackgroundSmooth(onGradient)

            val glow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#182ECC71"))
                setStroke(6, Color.parseColor("#66E6A0"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#252ECC71"))
                setStroke(6, Color.parseColor("#66E6A0"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states

        } else {
            statusText.text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#A0A0A0"))

            val offGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                OFF_COLORS
            ).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setGradientCenter(0.5f, 0.35f)
                alpha = 220
            }
            applyBackgroundSmooth(offGradient)

            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(3, Color.parseColor("#40FFFFFF"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#20FFFFFF"))
                setStroke(3, Color.parseColor("#60FFFFFF"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            powerButton.background = states
        }
    }
}
