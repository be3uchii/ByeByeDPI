package io.github.dovecoteescapee.byedpi. activities

import android. Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android. content.Intent
import android.content.IntentFilter
import android. content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics. drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os. Bundle
import android.view. Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi. utility.*
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
            Color.parseColor("#4A4268"),
            Color.parseColor("#423D5C"),
            Color.parseColor("#3B3650"),
            Color.parseColor("#342D44"),
            Color.parseColor("#2D2538"),
            Color.parseColor("#261D2C"),
            Color.parseColor("#1F1620"),
            Color.parseColor("#170D14"),
            Color.parseColor("#0D0408"),
            Color.parseColor("#070203")
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#1A6B4A"),
            Color. parseColor("#165F44"),
            Color.parseColor("#12533D"),
            Color.parseColor("#0E4736"),
            Color.parseColor("#0A3B2F"),
            Color.parseColor("#062F28"),
            Color.parseColor("#042321"),
            Color.parseColor("#021A1A"),
            Color.parseColor("#011113"),
            Color.parseColor("#000A0A")
        )

        private fun collectLogs(): String?  =
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
        window.navigationBarColor = Color.parseColor("#050302")
        
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
                        strokeCap = Paint.Cap. ROUND
                        setShadowLayer(10f, 0f, 0f, Color. parseColor("#80000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3. 5f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 20f, 320f, false, paint)
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
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            powerButton.isClickable = false
            hasAutoStarted = true
            val (status, _) = appStatus
            when (status) {
                AppStatus. Halted -> start()
                AppStatus.Running -> stop()
            }
            powerButton.postDelayed({ powerButton.isClickable = true }, 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES. TIRAMISU && 
            checkSelfPermission(Manifest. permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && ! hasAutoStarted && appStatus. first == AppStatus. Halted) {
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
                    ServiceManager.start(this, Mode. VPN)
                }
            }
            Mode. Proxy -> ServiceManager.start(this, Mode. Proxy)
        }
    }

    private fun stop() {
        ServiceManager.stop(this)
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
                setStroke(6, Color.parseColor("#2ECC71"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#352ECC71"))
                setStroke(6, Color.parseColor("#2ECC71"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R. attr.state_pressed), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
        } else {
            statusText.text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
            
            val offGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                OFF_COLORS
            )
            mainLayout. background = offGradient
            
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
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            powerButton.background = states
        }
    }
}
