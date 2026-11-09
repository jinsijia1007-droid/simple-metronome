package com.example.simplemetronome

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.sin

class MainActivity : Activity() {

    private var bpm = 120
    private var beatsPerBar = 4   // 分子
    private var beatUnit = 4      // 分母
    private var accentEnabled = true
    private var isRunning = false

    private lateinit var bpmText: TextView
    private lateinit var beatIndicator: TextView
    private lateinit var beatDots: TextView
    private lateinit var startButton: Button
    private lateinit var bpmSeek: SeekBar
    private lateinit var numeratorText: TextView
    private lateinit var denominatorText: TextView

    private var accentTrack: AudioTrack? = null
    private var normalTrack: AudioTrack? = null
    private var beatThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        loadPrefs()
        updateBpmDisplay()
        updateBeatDisplay()
        updateDotsForBeat(0)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF121212.toInt())
        }

        root.addView(TextView(this).apply {
            text = "节拍器"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })

        // ---------- 拍号控制行 ----------
        val beatRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        beatRow.addView(smallBtn("−") { changeNumerator(-1) })
        numeratorText = TextView(this).apply {
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            width = 90
        }
        beatRow.addView(numeratorText)
        beatRow.addView(smallBtn("+") { changeNumerator(1) })

        beatRow.addView(TextView(this).apply {
            text = "/"
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 0, 32, 0)
        })

        beatRow.addView(smallBtn("−") { changeDenominator(-1) })
        denominatorText = TextView(this).apply {
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            width = 90
        }
        beatRow.addView(denominatorText)
        beatRow.addView(smallBtn("+") { changeDenominator(1) })

        root.addView(beatRow)

        // ---------- BPM ----------
        root.addView(TextView(this).apply {
            text = "BPM"
            textSize = 18f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
        })

        bpmText = TextView(this).apply {
            textSize = 96f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(bpmText)

        bpmSeek = SeekBar(this).apply {
            max = 240 - 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        bpm = 30 + progress
                        updateBpmDisplay()
                        savePrefs()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(bpmSeek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 16) })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow.addView(Button(this).apply {
            text = "−"
            textSize = 32f
            setOnClickListener { changeBpm(-1) }
        }, LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT))
        btnRow.addView(Button(this).apply {
            text = "+"
            textSize = 32f
            setOnClickListener { changeBpm(1) }
        }, LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(32, 0, 0, 0) })
        root.addView(btnRow)

        beatIndicator = TextView(this).apply {
            text = "●"
            textSize = 80f
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        root.addView(beatIndicator)

        beatDots = TextView(this).apply {
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        root.addView(beatDots)

        startButton = Button(this).apply {
            text = "开始"
            textSize = 28f
            setOnClickListener { toggle() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 32, 0, 0) })

        setContentView(root)
    }

    private fun smallBtn(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 20f
            minimumWidth = 0
            minWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { onClick() }
        }

    private fun changeNumerator(delta: Int) {
        beatsPerBar = (beatsPerBar + delta).coerceIn(1, 16)
        updateBeatDisplay()
        updateDotsForBeat(0)
        savePrefs()
    }

    private fun changeDenominator(delta: Int) {
        beatUnit = (beatUnit + delta).coerceIn(1, 16)
        updateBeatDisplay()
        savePrefs()
    }

    private fun changeBpm(delta: Int) {
        bpm = (bpm + delta).coerceIn(30, 240)
        updateBpmDisplay()
        savePrefs()
    }

    private fun updateBpmDisplay() {
        bpmText.text = bpm.toString()
        bpmSeek.progress = bpm - 30
    }

    private fun updateBeatDisplay() {
        numeratorText.text = beatsPerBar.toString()
        denominatorText.text = beatUnit.toString()
    }

    private fun updateDotsForBeat(beat: Int) {
        val sb = StringBuilder()
        for (i in 0 until beatsPerBar) {
            sb.append(if (i == beat) "●" else "○")
            if (i < beatsPerBar - 1) sb.append("  ")
        }
        beatDots.text = sb.toString()
    }

    private fun toggle() {
        if (isRunning) stop() else start()
    }

    private fun start() {
        isRunning = true
        startButton.text = "停止"
        initAudio()
        beatThread = Thread {
            var beat = 0
            var nextTick = System.nanoTime()
            while (isRunning) {
                // 一拍时值 = (60 / BPM) * (4 / 分母) 秒
                val intervalNs = 60_000_000_000L * 4L / (bpm * beatUnit.toLong())
                val accent = beat == 0 && accentEnabled
                playClick(accent)
                val displayBeat = beat
                handler.post {
                    updateDotsForBeat(displayBeat)
                    flashIndicator(accent)
                }
                beat = (beat + 1) % beatsPerBar
                nextTick += intervalNs
                val sleepNs = nextTick - System.nanoTime()
                if (sleepNs > 0) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun stop() {
        isRunning = false
        beatThread?.interrupt()
        beatThread = null
        startButton.text = "开始"
        handler.post {
            updateDotsForBeat(0)
            beatIndicator.setTextColor(0xFF333333.toInt())
        }
        releaseAudio()
    }

    private fun flashIndicator(accent: Boolean) {
        beatIndicator.setTextColor(if (accent) 0xFFFF5252.toInt() else 0xFF69F0AE.toInt())
    }

    private fun initAudio() {
        releaseAudio()
        accentTrack = buildClickTrack(1200.0, 40, 0.9)
        normalTrack = buildClickTrack(880.0, 25, 0.6)
    }

    private fun buildClickTrack(freq: Double, durationMs: Int, amp: Double): AudioTrack {
        val sampleRate = 44100
        val n = sampleRate * durationMs / 1000
        val buf = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = 1.0 - i.toDouble() / n
            buf[i] = (sin(2 * Math.PI * freq * t) * amp * env * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buf.size * 2,
            AudioTrack.MODE_STATIC
        )
        track.write(buf, 0, buf.size)
        return track
    }

    private fun releaseAudio() {
        try {
            accentTrack?.release()
            normalTrack?.release()
        } catch (_: Exception) {
        }
        accentTrack = null
        normalTrack = null
    }

    private fun playClick(accent: Boolean) {
        val track = (if (accent) accentTrack else normalTrack) ?: return
        try {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop()
            }
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (_: Exception) {
        }
    }

    private fun loadPrefs() {
        val sp = getSharedPreferences("metronome", MODE_PRIVATE)
        bpm = sp.getInt("bpm", 120).coerceIn(30, 240)
        beatsPerBar = sp.getInt("beats", 4).coerceIn(1, 16)
        beatUnit = sp.getInt("beatUnit", 4).coerceIn(1, 16)
        accentEnabled = sp.getBoolean("accent", true)
    }

    private fun savePrefs() {
        getSharedPreferences("metronome", MODE_PRIVATE).edit()
            .putInt("bpm", bpm)
            .putInt("beats", beatsPerBar)
            .putInt("beatUnit", beatUnit)
            .putBoolean("accent", accentEnabled)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudio()
    }
}