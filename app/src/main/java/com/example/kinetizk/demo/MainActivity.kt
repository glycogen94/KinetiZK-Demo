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
import com.example.kinetizk.demo.SensorCollector
import com.example.kinetizk.demo.KinetiZKHelper

class MainActivity : AppCompatActivity() {

    /* ───────── 뷰 및 헬퍼 ───────── */
    private lateinit var binding: ActivityMainBinding
    private val sensorCollector by lazy { SensorCollector(this) }

    /* ───────── 상태 플래그 ───────── */
    private var collecting   = false
    private var botMode      = false
    private val botHandler   = Handler(Looper.getMainLooper())

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
        lifecycleScope.launch(Dispatchers.IO) {
            KinetiZKHelper.initialize(applicationContext)
            withContext(Dispatchers.Main) { initUi() }
        }
    }

    /* ───────── UI 초기 세팅 ───────── */
    private fun initUi() {
        binding.switchBotMode.setOnCheckedChangeListener { _, checked ->
            botMode = checked
            updateInstruction()
            if (checked) startBotLoop() else stopBotLoop()
        }
        updateInstruction()
        setupTouchDetection()
    }

    /* ───────── 터치 감지 ───────── */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchDetection() = binding.touchArea.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (!collecting) beginCollection(ev) }
            MotionEvent.ACTION_UP   -> { if (collecting)  endCollection()     }
        }
        true
    }

    private fun beginCollection(ev: MotionEvent) {
        collecting = true
        sensorCollector.register()                // 센서 리스너 등록
        KinetiZKHelper.startCollection()

        val downNs = ev.eventTime * 1_000_000L
        sensorCollector.beginWindow(downNs) { r ->
            KinetiZKHelper.addSensorReading(
                r.timestamp,
                r.accelX, r.accelY, r.accelZ,
                r.gyroX,  r.gyroY,  r.gyroZ
            )
        }
        binding.progressBar.isVisible = true
        binding.cardResult.isVisible  = false
        updateInstruction("Collecting…")
    }

    private fun endCollection() {
        collecting = false
        sensorCollector.endWindow()
        sensorCollector.unregister()
        updateInstruction("Generating proof…")

        lifecycleScope.launch {
            val elapsed = measureTimeMillis {
                runCatching { runPipeline() }
                    .onFailure { showError(it.message) }
            }
            binding.tvElapsed.text = "Elapsed: ${elapsed} ms"
            binding.progressBar.isVisible = false
        }
    }

    /* ───────── ZKP 파이프라인 ───────── */
private suspend fun runPipeline() = withContext(Dispatchers.IO) {
    val result = KinetiZKHelper.collectAndProve()     // ★

    withContext(Dispatchers.Main) {
        if (result.success)
            // classification: 0=human, 1=bot
            showResult(result.classification == 0, score = result.score)
        else
            showError("Proof verification failed")
            showResult(result.classification == 0, score = result.score)

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

    private fun showError(msg: String?) {
        updateInstruction("Error: $msg")
        Snackbar.make(binding.rootLayout, msg ?: "Unknown error", Snackbar.LENGTH_LONG).show()
    }

    /* ───────── 봇-모드 루프 ───────── */
    private fun startBotLoop() {
        botHandler.post(object : Runnable {
            override fun run() {
                if (!botMode) return
                val (x, y) = randomPos()
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
        binding.touchArea.dispatchTouchEvent(down)
        binding.touchArea.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
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
    private fun defaultInstruction() = if (botMode)
        "Bot-mode active. Simulating taps…" else "Tap the screen to generate a proof."
    private val Int.dp get() = (this * resources.displayMetrics.density).roundToInt()
}
