package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import java.io.IOException

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var statusButton: Button
    private lateinit var proxyAddress: TextView

    companion object {
        private const val REQUEST_VPN = 1
        private const val REQUEST_LOGS = 2
        private const val REQUEST_NOTIFICATIONS = 3
        
        // Флаг, чтобы автозапуск срабатывал только при холодном старте,
        // а не когда ты вернулся с паузы
        private var hasAutoStarted = false

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
        
        // 1. Делаем НАСТОЯЩИЙ полный экран (под вырез камеры и батарею)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // 2. Фон (Градиент)
        val mainGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364"))
        )

        // 3. Основной контейнер
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Добавляем отступы, чтобы контент не налез на закругленные углы экрана
            setPadding(40, 100, 40, 40) 
            this.background = mainGradient
        }

        // 4. Текст статуса (Меньше и аккуратнее)
        statusText = TextView(this).apply {
            textSize = 16f 
            letterSpacing = 0.1f
            setTextColor(Color.parseColor("#AAAAAA"))
            isAllCaps = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        // 5. Кнопка (Круглая и красивая)
        statusButton = Button(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            isAllCaps = true
            elevation = 10f
            stateListAnimator = null // Убираем стандартную тень андроида
            
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 200f
                setStroke(4, Color.WHITE)
            }
            val pressed = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 200f
                setStroke(4, Color.parseColor("#DDDDDD"))
            }
            
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            
            this.background = states
            
            // Делаем кнопку круглой (180x180)
            layoutParams = LinearLayout.LayoutParams(450, 450)
        }

        // 6. Адрес (еле заметный внизу)
        proxyAddress = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#50FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }

        layout.addView(statusText)
        layout.addView(statusButton)
        layout.addView(proxyAddress)
        
        setContentView(layout)

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

        statusButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            statusButton.isClickable = false
            
            // Ручное управление сбрасывает логику "первого запуска"
            hasAutoStarted = true 
            
            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            statusButton.postDelayed({ statusButton.isClickable = true }, 500)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        // --- ЛОГИЧНЫЙ АВТОЗАПУСК ---
        // Включаем, ТОЛЬКО если это первый старт приложения и сервис выключен.
        // Если сервис выключили кнопкой (Пауза/Стоп), hasAutoStarted уже будет true (или сервис Running),
        // и повторного включения не произойдет.
        if (savedInstanceState == null && !hasAutoStarted && appStatus.first == AppStatus.Halted) {
            hasAutoStarted = true
            statusButton.postDelayed({ start() }, 300) 
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

    private fun updateStatus() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"

        if (status == AppStatus.Running) {
            // ЛОГИКА ТЕКСТОВ
            statusText.text = "ПОДКЛЮЧЕНО"
            statusText.setTextColor(Color.parseColor("#66FF99")) // Зеленоватый
            
            statusButton.text = "СТОП"
            
            // Эффект "Свечения" при работе
            val glow = GradientDrawable().apply {
                setColor(Color.parseColor("#2066FF99")) // Прозрачный зеленый
                cornerRadius = 200f
                setStroke(4, Color.parseColor("#66FF99"))
            }
            statusButton.background = glow
            
        } else {
            statusText.text = "ОТКЛЮЧЕНО"
            statusText.setTextColor(Color.parseColor("#FF6666")) // Красноватый
            
            statusButton.text = "СТАРТ"
            
            // Обычный вид
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 200f
                setStroke(4, Color.WHITE)
            }
            statusButton.background = normal
        }
    }
}
