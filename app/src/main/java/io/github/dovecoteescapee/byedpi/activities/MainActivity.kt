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
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
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
        private const val ANIMATION_DURATION = 400L

        private val OFF_COLORS = intArrayOf(
            Color.parseColor("#4834D4"),
            Color.parseColor("#3D2EAF"),
            Color.parseColor("#342894"),
            Color.parseColor("#2C2279"),
            Color.parseColor("#241C5E"),
            Color.parseColor("#1D174B"),
            Color.parseColor("#161238"),
            Color.parseColor("#100D26"),
            Color.parseColor("#0A0815"),
            Color.parseColor("#060807")
        )

        private val ON_COLORS = intArrayOf(
            Color.parseColor("#00FF9D"),
            Color.parseColor("#00E08A"),
            Color.parseColor("#00C278"),
            Color.parseColor("#00A566"),
            Color.parseColor("#008955"),
            Color.parseColor("#006D45"),
            Color.parseColor("#005235"),
            Color.parseColor("#003825"),
            Color.parseColor("#001F15"),
            Color.parseColor("#060807")
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action in listOf(STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST)) {
                if (!isAnimating) {
                    updateStatusWithAnimation()
                }
            }
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
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
            alpha = 0f
        }

        statusText = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, 280, 0, 0)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        powerButton = ImageButton(this).apply {
            val icon = object : Drawable() {
                override fun draw(canvas: Canvas) {
                    val paint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 10f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        setShadowLayer(18f, 0f, 4f, Color.parseColor("#60000000"))
                    }
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()
                    val cx = w / 2
                    val cy = h / 2
                    val r = w / 3.2f
                    canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 270f + 15f, 330f, false, paint)
                    canvas.drawLine(cx, cy - r, cx, cy - r * 0.35f, paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            setImageDrawable(icon)
            
            val params = LinearLayout.LayoutParams(480, 480)
            params.topMargin = 180
            layoutParams = params
            alpha = 0f
        }

        proxyAddress = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#50FFFFFF"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 140
            layoutParams = params
            alpha = 0f
        }

        mainLayout.addView(statusText)
        mainLayout.addView(powerButton)
        mainLayout.addView(proxyAddress)
        
        setContentView(mainLayout)

        val fadeInAnimation = AlphaAnimation(0f, 1f).apply {
            duration = 600
            fillAfter = true
        }
        mainLayout.startAnimation(fadeInAnimation)
        mainLayout.alpha = 1f
        
        powerButton.postDelayed({
            val buttonFadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 400
                fillAfter = true
            }
            powerButton.startAnimation(buttonFadeIn)
            powerButton.alpha = 1f
            
            val textFadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 400
                startOffset = 200
                fillAfter = true
            }
            statusText.startAnimation(textFadeIn)
            statusText.alpha = 1f
            
            val addressFadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 400
                startOffset = 300
                fillAfter = true
            }
            proxyAddress.startAnimation(addressFadeIn)
            proxyAddress.alpha = 1f
        }, 200)

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
            if (isAnimating) return@setOnClickListener
            
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            powerButton.isClickable = false
            hasAutoStarted = true
            val (status, _) = appStatus
            
            val clickAnimation = ScaleAnimation(
                1f, 0.85f, 1f, 0.85f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 150
                fillAfter = true
            }
            
            powerButton.startAnimation(clickAnimation)
            
            powerButton.postDelayed({
                val releaseAnimation = ScaleAnimation(
                    0.85f, 1f, 0.85f, 1f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 150
                    fillAfter = true
                }
                powerButton.startAnimation(releaseAnimation)
                
                powerButton.postDelayed({
                    when (status) {
                        AppStatus.Halted -> start()
                        AppStatus.Running -> stop()
                    }
                    powerButton.postDelayed({ powerButton.isClickable = true }, 500)
                }, 150)
            }, 150)
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
        if (!isAnimating) {
            updateStatus()
        }
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
            statusText.text = "ПОДКЛЮЧЁН"
            statusText.setTextColor(Color.parseColor("#00FF9D"))
            
            val onGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                ON_COLORS
            ).apply {
                cornerRadius = 0f
                alpha = 210
            }
            mainLayout.background = onGradient
            
            val glow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2000FF9D"))
                setStroke(8, Color.parseColor("#80FF9D"))
                alpha = 230
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4000FF9D"))
                setStroke(8, Color.parseColor("#A0FF9D"))
                alpha = 240
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), glow)
            powerButton.background = states
            
        } else {
            statusText.text = "НЕТ СВЯЗИ"
            statusText.setTextColor(Color.parseColor("#C0C0C0"))
            
            val offGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                OFF_COLORS
            ).apply {
                cornerRadius = 0f
                alpha = 190
            }
            mainLayout.background = offGradient
            
            val normal = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(4, Color.parseColor("#40FFFFFF"))
                alpha = 200
            }
            val pressed = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#20FFFFFF"))
                setStroke(4, Color.parseColor("#70FFFFFF"))
                alpha = 220
            }
            val states = StateListDrawable()
            states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
            states.addState(intArrayOf(), normal)
            powerButton.background = states
        }
    }

    private fun updateStatusWithAnimation() {
        val (status, _) = appStatus
        val prefs = getPreferences()
        val (ip, port) = prefs.getProxyIpAndPort()

        proxyAddress.text = "$ip:$port"
        
        isAnimating = true
        
        val fadeOut = AlphaAnimation(1f, 0.3f).apply {
            duration = ANIMATION_DURATION / 2
            fillAfter = true
        }
        
        val scaleDown = ScaleAnimation(
            1f, 0.95f, 1f, 0.95f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = ANIMATION_DURATION / 2
            fillAfter = true
        }
        
        val animationSet = AnimationSet(true).apply {
            addAnimation(fadeOut)
            addAnimation(scaleDown)
        }
        
        mainLayout.startAnimation(animationSet)
        powerButton.startAnimation(animationSet)
        statusText.startAnimation(fadeOut)
        
        powerButton.postDelayed({
            if (status == AppStatus.Running) {
                statusText.text = "ПОДКЛЮЧЁН"
                statusText.setTextColor(Color.parseColor("#00FF9D"))
                
                val onGradient = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    ON_COLORS
                ).apply {
                    cornerRadius = 0f
                    alpha = 210
                }
                mainLayout.background = onGradient
                
                val glow = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#2000FF9D"))
                    setStroke(8, Color.parseColor("#80FF9D"))
                    alpha = 230
                }
                val pressed = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#4000FF9D"))
                    setStroke(8, Color.parseColor("#A0FF9D"))
                    alpha = 240
                }
                val states = StateListDrawable()
                states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
                states.addState(intArrayOf(), glow)
                powerButton.background = states
                
            } else {
                statusText.text = "НЕТ СВЯЗИ"
                statusText.setTextColor(Color.parseColor("#C0C0C0"))
                
                val offGradient = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    OFF_COLORS
                ).apply {
                    cornerRadius = 0f
                    alpha = 190
                }
                mainLayout.background = offGradient
                
                val normal = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(4, Color.parseColor("#40FFFFFF"))
                    alpha = 200
                }
                val pressed = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#20FFFFFF"))
                    setStroke(4, Color.parseColor("#70FFFFFF"))
                    alpha = 220
                }
                val states = StateListDrawable()
                states.addState(intArrayOf(android.R.attr.state_pressed), pressed)
                states.addState(intArrayOf(), normal)
                powerButton.background = states
            }
            
            val fadeIn = AlphaAnimation(0.3f, 1f).apply {
                duration = ANIMATION_DURATION / 2
                fillAfter = true
            }
            
            val scaleUp = ScaleAnimation(
                0.95f, 1f, 0.95f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = ANIMATION_DURATION / 2
                fillAfter = true
            }
            
            val bounceScale = ScaleAnimation(
                1f, 1.05f, 1f, 1.05f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 100
                startOffset = ANIMATION_DURATION / 2
                fillAfter = false
            }
            
            val bounceBack = ScaleAnimation(
                1.05f, 1f, 1.05f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 100
                startOffset = ANIMATION_DURATION / 2 + 100
                fillAfter = true
            }
            
            val finalAnimationSet = AnimationSet(true).apply {
                addAnimation(fadeIn)
                addAnimation(scaleUp)
                addAnimation(bounceScale)
                addAnimation(bounceBack)
                doOnEnd { isAnimating = false }
            }
            
            mainLayout.startAnimation(finalAnimationSet)
            powerButton.startAnimation(finalAnimationSet)
            statusText.startAnimation(fadeIn)
            
            val rippleAnimation = ScaleAnimation(
                1f, 1.15f, 1f, 1.15f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 300
                fillAfter = false
            }
            
            powerButton.postDelayed({
                powerButton.startAnimation(rippleAnimation)
            }, ANIMATION_DURATION / 2 + 50)
            
        }, ANIMATION_DURATION / 2)
    }
}
