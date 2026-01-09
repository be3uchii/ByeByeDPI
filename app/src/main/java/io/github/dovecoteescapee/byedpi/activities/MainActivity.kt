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
        hideSystemUI()

        // 1. Фон (Градиент)
        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#1A2980"), Color.parseColor("#26D0CE"))
        )

        // 2. Основной слой
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            this.background = background
        }

        // 3. Текст статуса
        statusText = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 100)
        }

        // 4. Кнопка
        statusButton = Button(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            isAllCaps = false
            
            // Создаем разные drawable для состояний
            val normal = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF"))
                cornerRadius = 100f
                setStroke(3, Color.WHITE)
            }
            val pressed = GradientDrawable().apply {
                setColor(Color.parseColor("#80FFFFFF"))
                cornerRadius = 100f
            }
            
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            
            background = states // Присваиваем StateListDrawable в background (тип Drawable) - теперь ошибок не будет
            
            layoutParams = LinearLayout.LayoutParams(600, 180)
        }

        // 5. Адрес
        proxyAddress = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#B0FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 0)
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
            val (status, _) = appStatus
            when (status) {
                AppStatus.Halted -> start()
                AppStatus.Running -> stop()
            }
            statusButton.postDelayed({ statusButton.isClickable = true }, 1000)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        ShortcutUtils.update(this)
        
        if (savedInstanceState == null && appStatus.first == AppStatus.Halted) {
            statusButton.postDelayed({ start() }, 300) 
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateStatus()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
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
        val (status, mode) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = getString(R.string.proxy_address, ip, port)

        if (status == AppStatus.Running) {
            statusText.text = if (mode == Mode.VPN) getString(R.string.vpn_connected) else getString(R.string.proxy_up)
            statusButton.text = if (mode == Mode.VPN) getString(R.string.vpn_disconnect) else getString(R.string.proxy_stop)
            statusButton.alpha = 0.8f
        } else {
            statusText.text = if (prefs.mode() == Mode.VPN) getString(R.string.vpn_disconnected) else getString(R.string.proxy_down)
            statusButton.text = if (prefs.mode() == Mode.VPN) getString(R.string.vpn_connect) else getString(R.string.proxy_start)
            statusButton.alpha = 1.0f
        }
    }
}
