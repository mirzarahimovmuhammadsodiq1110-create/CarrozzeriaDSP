package com.carrozzeria.dspplayer

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import org.json.JSONArray
import org.json.JSONObject

data class EqPreset(val name: String, val gains: FloatArray)

class PioneerAudioEngine(private val context: Context) {

    val frequencies = floatArrayOf(
        50f, 80f, 125f, 200f, 315f, 500f, 800f,
        1250f, 2000f, 3150f, 5000f, 8000f, 12500f
    )

    val freqLabels = arrayOf(
        "50", "80", "125", "200", "315", "500", "800",
        "1.25K", "2K", "3.15K", "5K", "8K", "12.5K"
    )

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var eq: DynamicsProcessing.Eq? = null
    private var reverb: PresetReverb? = null

    val currentGains = FloatArray(13) { 0f }
    private var eqEnabled = true
    var currentReverbMode = "OFF"

    val presets = mutableListOf(
        EqPreset("Standard", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Carrozzeria Studio", floatArrayOf(4.5f, 3.5f, 1.5f, -1f, -1.5f, -1f, 0f, 0.5f, 1f, 2f, 3f, 3.5f, 4f)),
        EqPreset("Super Bass", floatArrayOf(6f, 5f, 3.5f, 1.5f, 0f, -1f, -1f, 0f, 1f, 1.5f, 2f, 2.5f, 2f)),
        EqPreset("Jazz", floatArrayOf(2f, 1.5f, 1f, 0.5f, -1f, -1f, 0f, 1f, 2f, 2.5f, 2f, 1.5f, 1f)),
        EqPreset("Blues", floatArrayOf(3f, 2f, 1f, 0f, 1f, 1.5f, 1f, 0f, 1f, 2f, 2.5f, 2f, 1f)),
        EqPreset("Dance", floatArrayOf(5f, 4f, 2f, 0f, -1f, 0f, 1f, 2f, 2.5f, 3f, 3.5f, 3f, 2.5f)),
        EqPreset("Pop", floatArrayOf(1.5f, 2f, 1f, 0f, 1f, 2f, 2.5f, 2f, 1f, 1.5f, 2f, 2.5f, 2f)),
        EqPreset("Rock", floatArrayOf(4f, 3f, 1.5f, 0f, -1.5f, -1f, 0.5f, 2f, 3f, 3.5f, 4f, 3.5f, 3f)),
        EqPreset("Vocal", floatArrayOf(-2f, -1f, 0f, 1f, 2f, 3f, 3.5f, 3f, 2f, 1f, 0f, -1f, -1f)),
        EqPreset("Powerful", floatArrayOf(5f, 4f, 2.5f, 1f, 0f, 0f, 1f, 2f, 3f, 3.5f, 4f, 4.5f, 4f))
    )

    init {
        loadCustomPresets()
    }

    fun attachToSession(audioSessionId: Int) {
        release()
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, true, 13, false, 0, false, 0, true
            ).build()

            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                enabled = eqEnabled
            }

            eq = DynamicsProcessing.Eq(true, true, 13).apply {
                for (i in frequencies.indices) {
                    getBand(i).apply {
                        isEnabled = true
                        cutoffFrequency = frequencies[i]
                        gain = currentGains[i]
                    }
                }
            }
            dynamicsProcessing?.setPreEqAllChannelsTo(eq)

            reverb = PresetReverb(0, audioSessionId)
            setReverbMode(currentReverbMode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        currentGains[index] = clamped
        eq?.getBand(index)?.gain = clamped
        try {
            dynamicsProcessing?.setPreEqAllChannelsTo(eq)
        } catch (_: Exception) {}
    }

    fun setEqEnabled(enabled: Boolean) {
        eqEnabled = enabled
        try {
            dynamicsProcessing?.enabled = enabled
        } catch (_: Exception) {}
    }

    fun applyPreset(preset: EqPreset) {
        for (i in 0 until 13) {
            setBandGain(i, preset.gains[i])
        }
    }

    fun saveCustomPreset(name: String) {
        val copy = currentGains.copyOf()
        presets.add(EqPreset(name, copy))
        val prefs = context.getSharedPreferences("carrozzeria_presets", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("custom_list", "[]"))
        val obj = JSONObject()
        obj.put("name", name)
        val gainsArr = JSONArray()
        copy.forEach { gainsArr.put(it.toDouble()) }
        obj.put("gains", gainsArr)
        arr.put(obj)
        prefs.edit().putString("custom_list", arr.toString()).apply()
    }

    private fun loadCustomPresets() {
        val prefs = context.getSharedPreferences("carrozzeria_presets", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("custom_list", "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val gArr = obj.getJSONArray("gains")
            val gains = FloatArray(13) { idx -> gArr.optDouble(idx, 0.0).toFloat() }
            presets.add(EqPreset(name, gains))
        }
    }

    fun setReverbMode(mode: String) {
        currentReverbMode = mode
        try {
            when (mode) {
                "OFF" -> {
                    reverb?.preset = PresetReverb.PRESET_NONE
                    reverb?.enabled = false
                }
                "STUDIO" -> {
                    reverb?.preset = PresetReverb.PRESET_SMALLROOM
                    reverb?.enabled = true
                }
                "CLUB" -> {
                    reverb?.preset = PresetReverb.PRESET_MEDIUMROOM
                    reverb?.enabled = true
                }
                "CONCERT" -> {
                    reverb?.preset = PresetReverb.PRESET_LARGEHALL
                    reverb?.enabled = true
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try { dynamicsProcessing?.release() } catch (_: Exception) {}
        try { reverb?.release() } catch (_: Exception) {}
        dynamicsProcessing = null
        reverb = null
    }
}
