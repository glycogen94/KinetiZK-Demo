package com.example.kinetizk.demo

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.kinetizk.demo.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import com.kinetizk.sdk.KinetiZK
import com.kinetizk.sdk.KinetiZKConfig
import com.kinetizk.sdk.KinetiZKResult
import com.kinetizk.sdk.KinetiZKError

class MainActivity : AppCompatActivity() {

    /* ───────── 뷰 ───────── */
    private lateinit var binding: ActivityMainBinding

    /* ───────── 상태 플래그 ───────── */
    private var botMode      = false
    private val botHandler   = Handler(Looper.getMainLooper())
    private var challengeRunning = false

    /* ───────── 봇-터치 파라미터 ───────── */
    private val rand               = Random.Default
    private val BOT_MIN_DELAY_MS   = 600L
    private val BOT_MAX_DELAY_MS   = 1300L
    private val BOT_HOLD_MS        = 70L

    /* ───────── onCreate ───────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }
        window.statusBarColor = getColor(R.color.background_dark)

        /* 1) KinetiZK SDK 초기화 */
        val config = KinetiZKConfig(
            siteKey = "demo_site_key",
            enableLogging = true,
            timeoutMillis = Long.MAX_VALUE  // 무제한 대기
        )
        KinetiZK.initialize(this, config)

        initUi()
    }

    /* ───────── UI 초기 세팅 ───────── */
    private fun initUi() {
        binding.switchBotMode.setOnCheckedChangeListener { _, checked ->
            botMode = checked
            updateInstruction()
            if (checked) {
                startBotLoop()
            } else {
                stopBotLoop()
            }
        }
        updateInstruction()
        
        // SDK가 직접 센싱하도록 백그라운드에서 시작
        startContinuousSensing()
    }
    
    /* ───────── 터치 감지 ───────── */
    private fun startContinuousSensing() {
        if (challengeRunning) return
        
        challengeRunning = true
        updateInstruction("SDK monitoring sensors...")
        
        val startTime = System.currentTimeMillis()
        
        KinetiZK.execute(this@MainActivity) { result ->
            val elapsed = System.currentTimeMillis() - startTime
            
            when (result) {
                is KinetiZKResult.Success -> {
                    showResult(result.isHuman, result.score)
                }
                is KinetiZKResult.Failure -> {
                    handleError(result.error)
                }
            }
            
            binding.tvElapsed.text = "Elapsed: ${elapsed} ms"
            binding.progressBar.isVisible = false
            
            // 결과 표시 후 다시 센싱 시작
            Handler(Looper.getMainLooper()).postDelayed({
                challengeRunning = false
                startContinuousSensing()
            }, 2000)
        }
    }


    /* ───────── 결과 카드 UI ───────── */
    private fun showResult(isHuman: Boolean, score: Double) {
        val cardColor: Int
        val textColor: Int
        val label: String

        if (isHuman) {
            cardColor = R.color.success_green_light
            textColor = R.color.success_green
            label     = "HUMAN (score=$score)"
            updateInstruction("Verification successful!")
        } else {
            cardColor = R.color.error_red_light
            textColor = R.color.error_red
            label     = "BOT (score=$score)"
            updateInstruction("Bot detected!")
        }

        binding.cardResult.apply {
            setCardBackgroundColor(getColor(cardColor))
            binding.tvClassification.apply {
                text = label
                setTextColor(getColor(textColor))
            }
            isVisible = true
        }
    }

    private fun handleError(error: KinetiZKError) {
        val msg = error.name
        updateInstruction("Error: $msg")
        Snackbar.make(binding.rootLayout, msg, Snackbar.LENGTH_LONG).show()
    }

    /* ───────── 봇-모드 루프 ───────── */
    private fun startBotLoop() {
        botHandler.post(object : Runnable {
            override fun run() {
                if (!botMode) return
                
                val (x, y) = randomPos()
                // SDK가 직접 감지할 수 있도록 실제 터치 이벤트 주입
                injectSyntheticTouch(x, y)
                showDot(x, y)
                botHandler.postDelayed(
                    this,
                    rand.nextLong(BOT_MIN_DELAY_MS, BOT_MAX_DELAY_MS)
                )
            }
        })
    }
    
    private fun stopBotLoop() = botHandler.removeCallbacksAndMessages(null)

    private fun randomPos(): Pair<Int, Int> {
        val w = binding.touchArea.width
        val h = binding.touchArea.height
        return rand.nextInt(w) to rand.nextInt(h * 2 / 3)
    }

    /** DOWN → UP 70 ms 간격의 합성 터치 이벤트 주입 */
    private fun injectSyntheticTouch(x: Int, y: Int) {
        val t0  = SystemClock.uptimeMillis()
        val t1  = t0 + BOT_HOLD_MS
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        val up   = MotionEvent.obtain(t0, t1, MotionEvent.ACTION_UP,   x.toFloat(), y.toFloat(), 0)
        
        // 터치 영역에 전달 (시각적 효과용)
        binding.touchArea.dispatchTouchEvent(down)
        binding.touchArea.dispatchTouchEvent(up)
        
        // SDK에도 전달 (봇 감지용) - SDK가 백그라운드에서 감지할 수 있도록
        window.decorView.dispatchTouchEvent(down)
        Handler(Looper.getMainLooper()).postDelayed({
            window.decorView.dispatchTouchEvent(up)
        }, BOT_HOLD_MS)
        
        down.recycle()
        up.recycle()
    }

    /* ───────── 터치 시각화 ───────── */
    private val dotDrawable: Drawable by lazy {
        ContextCompat.getDrawable(this, R.drawable.touch_dot)!!
    }
    private fun showDot(x: Int, y: Int) {
        val size = 20.dp
        val v = View(this).apply {
            background = dotDrawable
            layoutParams = ViewGroup.LayoutParams(size, size)
            this.x = (x - size / 2).toFloat()
            this.y = (y - size / 2).toFloat()
            alpha = 0.8f
        }
        binding.touchArea.addView(v)
        v.animate().alpha(0f).setDuration(450).withEndAction {
            binding.touchArea.removeView(v)
        }.start()
    }

    /* ───────── 기타 헬퍼 ───────── */
    private fun updateInstruction(text: String = defaultInstruction()) {
        binding.tvInstruction.text = text
    }
    private fun defaultInstruction() = when {
        botMode -> "Bot-mode active. Simulating taps…"
        challengeRunning -> "Challenge in progress…"
        else -> "Tap the screen to generate a proof."
    }
    private val Int.dp get() = (this * resources.displayMetrics.density).roundToInt()
    
    /* ───────── 터치 이벤트 감지 ───────── */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let { event ->
            // SDK에 터치 이벤트 전달
            KinetiZK.handleTouchEvent(event)
        }
        return super.dispatchTouchEvent(ev)
    }
}
