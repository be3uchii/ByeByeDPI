package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.animation.ObjectAnimator
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var proxyAddress: TextView
    private var hasAutoStarted = false
    
    // Флаг, чтобы анимация не прерывалась спамом нажатий
    private var isAnimating = false

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3

        // --- ГРАДИЕНТЫ (Подкручены для идеального перехода в черный) ---
        
        // Фиолетовый (Выкл)
        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#332E67"), // Верх
            Color.parseColor("#2F2A5E"),
            Color.parseColor("#27224A"),
            Color.parseColor("#1E1B3A"),
            Color.parseColor("#18162B"),
            Color.parseColor("#110F1C"),
            Color.parseColor("#060807"), // Начало черного
            Color.parseColor("#060807"), // Усиление низа
            Color.parseColor("#060807")  // Еще больше черного внизу
        )

        // Зеленый (Вкл)
        private val ON_COLORS = intArrayOf(
            Color.parseColor("#0F4D34"), // Верх
            Color.parseColor("#0D462F"),
            Color.parseColor("#0C3E29"),
            Color.parseColor("#0B3323"),
            Color.parseColor("#09291C"),
            Color.parseColor("#060807"), // Плавный уход в черный
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
            // Обновляем статус только если мы не в процессе красивой анимации ручного нажатия
            if (!isAnimating && intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                updateUIState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Системные бары
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#060807") // Идеальный черный низ
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        // --- СТРУКТУРА UI ---
        
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        // 1. Текст статуса
        statusText = TextView(this).apply {
            textSize = 24f // Чуть крупнее и мягче
            setTextColor(Color.parseColor("#A0A0A0"))
            gravity = Gravity.CENTER
            alpha = 0.9f
            // Большой отступ сверху для баланса
            setPadding(0, 300, 0, 0) 
        }

        // 2. Контейнер для кнопки и спиннера (чтобы они были друг над другом)
        val buttonContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(450, 450).apply {
                topMargin = 150 // Отступ от текста до кнопки
            }
        }

        // Сама кнопка
        powerButton = ImageButton(this).apply {
            // Рисуем иконку
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 9f // Чуть жирнее
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(12f, 0f, 0f, Color.parseColor("#60000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3.6f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 25f, 310f, false, paint)
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.35f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setImageDrawable(icon)
            
            // Фон кнопки
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#15FFFFFF")) // Очень мягкий фон
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
            
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        // Крутилка загрузки (Спиннер)
        loadingSpinner = ProgressBar(this).apply {
            visibility = View.INVISIBLE
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE) // Белый по умолчанию
            layoutParams = FrameLayout.LayoutParams(350, 350).apply {
                gravity = Gravity.CENTER
            }
        }

        buttonContainer.addView(powerButton)
        buttonContainer.addView(loadingSpinner)

        // 3. IP Адрес
        proxyAddress = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#40FFFFFF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 120
            layoutParams = params
        }

        mainLayout.addView(statusText)
        mainLayout.addView(buttonContainer)
        mainLayout.addView(proxyAddress)
        
        setContentView(mainLayout)

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
            performSwitchAnimation()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && !hasAutoStarted && appStatus.first == AppStatus.Halted) {
            hasAutoStarted = true
            // Для автозапуска анимацию делать не обязательно, просто включаем
            powerButton.postDelayed({ start() }, 300) 
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
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

    // --- КРАСИВАЯ АНИМАЦИЯ ПЕРЕКЛЮЧЕНИЯ ---
    private fun performSwitchAnimation() {
        if (isAnimating) return
        isAnimating = true
        
        powerButton.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        
        // 1. Скрываем иконку питания
        powerButton.imageAlpha = 0 // Прозрачная
        
        // 2. Показываем спиннер
        loadingSpinner.visibility = View.VISIBLE
        
        val targetStatus = if (appStatus.first == AppStatus.Halted) "ON" else "OFF"
        
        // Красим спиннер в цвет будущего состояния
        val spinnerColor = if (targetStatus == "ON") Color.parseColor("#2ECC71") else Color.parseColor("#A0A0A0")
        loadingSpinner.indeterminateTintList = ColorStateList.valueOf(spinnerColor)

        // 3. Ждем секунду для красоты (эмуляция процесса)
        mainLayout.postDelayed({
            // Переключаем сервис
            val (status, _) = appStatus
            if (status == AppStatus.Halted) start() else stop()
            
            // 4. Обновляем интерфейс
            updateUIState()
            
            // 5. Возвращаем иконку
            loadingSpinner.visibility = View.INVISIBLE
            
            // Плавное появление иконки обратно
            val fade = ObjectAnimator.ofInt(powerButton, "imageAlpha", 0, 255)
            fade.duration = 300
            fade.start()
            
            isAnimating = false
        }, 800) // 800мс задержка для плавности
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

        // Анимация перехода цвета фона
        // (Создаем новый Drawable каждый раз для надежности)
        
        if (status == AppStatus.Running) {
            // --- ВКЛЮЧЕНО ---
            statusText.text = "Подключён"
            statusText.setTextColor(Color.parseColor("#2ECC71")) // Зеленый
            
            val onGradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, ON_COLORS)
            mainLayout.background = onGradient
            
            // Кнопка светится
            val glow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#152ECC71"))
                setStroke(6, Color.parseColor("#2ECC71"))
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#302ECC71"))
                setStroke(6, Color.parseColor("#2ECC71"))
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
        } else {
            // --- ВЫКЛЮЧЕНО ---
            statusText.text = "Нет связи"
            statusText.setTextColor(Color.parseColor("#A0A0A0")) // Серый
            
            val offGradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, OFF_COLORS)
            mainLayout.background = offGradient
            
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
