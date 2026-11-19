package com.carrozzeria.dspplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var playerService: AudioPlayerService? = null
    private var isBound = false
    private var visualizer: Visualizer? = null

    private lateinit var visualizerView: VisualizerView
    private lateinit var bgVideoView: VideoView
    private lateinit var spinnerPresets: Spinner
    private lateinit var eqContainer: LinearLayout
    private val seekBars = mutableListOf<SeekBar>()
    private val valueTexts = mutableListOf<TextView>()

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            playerService?.playAudio(it) { sessionId ->
                setupVisualizer(sessionId)
            }
        }
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            visualizerView.visibility = View.GONE
            bgVideoView.visibility = View.VISIBLE
            bgVideoView.setVideoURI(it)
            bgVideoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.start()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true
            setupPresetsSpinner()
            setupEqSliders()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        visualizerView = findViewById(R.id.visualizerView)
        bgVideoView = findViewById(R.id.bgVideoView)
        spinnerPresets = findViewById(R.id.spinnerPresets)
        eqContainer = findViewById(R.id.eqContainer)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        val intent = Intent(this, AudioPlayerService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        findViewById<Button>(R.id.btnPickAudio).setOnClickListener { pickAudio.launch("audio/*") }
        findViewById<Button>(R.id.btnPlayPause).setOnClickListener { playerService?.togglePlayPause() }
        findViewById<Button>(R.id.btnPickVideo).setOnClickListener {
            if (bgVideoView.visibility == View.VISIBLE) {
                bgVideoView.stopPlayback()
                bgVideoView.visibility = View.GONE
                visualizerView.visibility = View.VISIBLE
            } else {
                pickVideo.launch("video/*")
            }
        }

        findViewById<Button>(R.id.btnEchoOff).setOnClickListener { playerService?.audioEngine?.setReverbMode("OFF") }
        findViewById<Button>(R.id.btnEchoStudio).setOnClickListener { playerService?.audioEngine?.setReverbMode("STUDIO") }
        findViewById<Button>(R.id.btnEchoClub).setOnClickListener { playerService?.audioEngine?.setReverbMode("CLUB") }
        findViewById<Button>(R.id.btnEchoConcert).setOnClickListener { playerService?.audioEngine?.setReverbMode("CONCERT") }

        findViewById<Button>(R.id.btnSavePreset).setOnClickListener { showSavePresetDialog() }
    }

    private fun setupVisualizer(audioSessionId: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            visualizer?.release()
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        fft?.let { visualizerView.updateFft(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (_: Exception) {}
    }

    private fun setupPresetsSpinner() {
        val engine = playerService?.audioEngine ?: return
        val names = engine.presets.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinnerPresets.adapter = adapter
        spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val preset = engine.presets[pos]
                engine.applyPreset(preset)
                updateSlidersFromEngine()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupEqSliders() {
        val engine = playerService?.audioEngine ?: return
        eqContainer.removeAllViews()
        seekBars.clear()
        valueTexts.clear()

        for (i in 0 until 13) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }
            val label = TextView(this).apply {
                text = "${engine.freqLabels[i]} Hz"
                setTextColor(Color.WHITE)
                width = 160
            }
            val valText = TextView(this).apply {
                text = "0.0 dB"
                setTextColor(Color.parseColor("#00E676"))
                width = 140
            }
            val seekBar = SeekBar(this).apply {
                max = 240 // -12.0 to +12.0
                progress = 120
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        val db = (p - 120) / 10f
                        valText.text = String.format("%.1f dB", db)
                        if (fromUser) engine.setBandGain(i, db)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            row.addView(label)
            row.addView(seekBar)
            row.addView(valText)
            eqContainer.addView(row)
            seekBars.add(seekBar)
            valueTexts.add(valText)
        }
    }

    private fun updateSlidersFromEngine() {
        val engine = playerService?.audioEngine ?: return
        for (i in 0 until 13) {
            val db = engine.currentGains[i]
            seekBars[i].progress = ((db * 10) + 120).toInt().coerceIn(0, 240)
            valueTexts[i].text = String.format("%.1f dB", db)
        }
    }

    private fun showSavePresetDialog() {
        val input = EditText(this)
        input.hint = "Пресет номи (масалан: Менинг басим)"
        AlertDialog.Builder(this)
            .setTitle("Янги пресет сақлаш")
            .setView(input)
            .setPositiveButton("Сақлаш") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    playerService?.audioEngine?.saveCustomPreset(name)
                    setupPresetsSpinner()
                    Toast.makeText(this, "Сақланди: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Бекор қилиш", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        visualizer?.release()
        if (isBound) unbindService(connection)
    }
}
