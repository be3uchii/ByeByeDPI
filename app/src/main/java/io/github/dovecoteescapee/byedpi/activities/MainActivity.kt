package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var mainContainer: FrameLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var powerButton: ImageButton
    private lateinit var orbitSpinner: ProgressBar
    private lateinit var proxyAddress: TextView
    
    private var isProcessing = false
    private var hasAutoStarted = false

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3

        // Ваши цвета + больше черного внизу для плавности
        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#332E67"),
            Color.parseColor("#2F2A5E"),
            Color.parseColor("#2D2757"),
            Color.parseColor("#27224A"),
            Color.parseColor("#1E1B3A"),
            Color.parseColor("#18162B"),
            Color.parseColor("#110F1C"),
            Color.parseColor("#0B0B11"),
            Color.parseColor("#060807"),
            Color.parseColor("#060807"), 
            Color.parseColor("#060807")  
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#0F4D34"),
            Color.parseColor("#0D462F"),
            Color.parseColor("#0C3E29"),
            Color.parseColor("#0B3323"),
            Color.parseColor("#09291C"),
            Color.parseColor("#060807"),
            Color.parseColor("#060807"),
            Color.parseColor("#060807")
        )

        private fun collectLogs(): String? =
            try {
                Runtime.getRuntime().exec("logcat *:D -d").inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) { null }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isProcessing && intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                updateUIState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.navigationBarColor = Color.parseColor("#060807")

        // 1. Главный контейнер (фон и центровка)
        mainContainer = FrameLayout(this)
        
        // 2. Вертикальный слой для Текста и Адреса
        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ТЕКСТ СТАТУСА (Сверху)
        statusText = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            setPadding(0, 200, 0, 0) 
        }

        // КОНТЕЙНЕР КНОПКИ (По центру)
        val centerBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(550, 550).apply {
                topMargin = 250 
            }
        }

        // СПИННЕР (Крутится ВОКРУГ кнопки, поэтому он больше)
        orbitSpinner = ProgressBar(this).apply {
            visibility = View.INVISIBLE
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            // Размер больше кнопки
            layoutParams = FrameLayout.LayoutParams(550, 550).apply {
                gravity = Gravity.CENTER
            }
        }

        // КНОПКА ПИТАНИЯ
        powerButton = ImageButton(this).apply {
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 8f
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
            states.addState(intArrayOf(), normal)
            background = states
            
            // Размер кнопки меньше спиннера
            layoutParams = FrameLayout.LayoutParams(400, 400).apply {
                gravity = Gravity.CENTER
            }
        }

        // АДРЕС (Снизу)
        proxyAddress = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#40FFFFFF"))
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 150
            layoutParams = params
        }

        // Сборка матрешки
        centerBox.addView(orbitSpinner)
        centerBox.addView(powerButton)

        contentLayout.addView(statusText)
        contentLayout.addView(centerBox)
        contentLayout.addView(proxyAddress)

        mainContainer.addView(contentLayout)
        setContentView(mainContainer)

        // --- ЛОГИКА ---

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
            handleUserClick()
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
        if (!isProcessing) updateUIState()
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

    private fun handleUserClick() {
        if (isProcessing) return
        isProcessing = true
        powerButton.isEnabled = false // Блокируем спам
        
        powerButton.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        
        // Показываем спиннер вокруг
        orbitSpinner.visibility = View.VISIBLE
        
        // Определяем цвет спиннера (если сейчас выкл -> включим -> значит будет зеленый)
        val futureColor = if (appStatus.first == AppStatus.Halted) "#2ECC71" else "#A0A0A0"
        orbitSpinner.indeterminateTintList = ColorStateList.valueOf(Color.parseColor(futureColor))

        // Задержка 2 секунды (как просили)
        mainContainer.postDelayed({
            val (status, _) = appStatus
            if (status == AppStatus.Halted) start() else stop()
            
            updateUIState()
            
            orbitSpinner.visibility = View.INVISIBLE
            isProcessing = false
            powerButton.isEnabled = true
        }, 2000)
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

    private fun updateUIState() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            // ВКЛЮЧЕНО
            statusText.text = "Подключён"
            statusText.setTextColor(Color.parseColor("#2ECC71"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            // Кнопка светится
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
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
        } else {
            // ВЫКЛЮЧЕНО
            statusText.text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
            
            val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_COLORS)
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT)
            mainContainer.background = gradient
            
            // Кнопка спокойная
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
