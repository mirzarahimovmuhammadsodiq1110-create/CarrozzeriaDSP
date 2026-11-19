package com.carrozzeria.dspplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot

class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fftBytes: ByteArray? = null
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.FILL
    }

    fun updateFft(bytes: ByteArray) {
        fftBytes = bytes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = fftBytes ?: return
        val barCount = 24
        val barWidth = width.toFloat() / barCount
        for (i in 0 until barCount) {
            val idx = (i * 2 + 2).coerceAtMost(data.size - 2)
            val rfk = data[idx].toFloat()
            val ifk = data[idx + 1].toFloat()
            val magnitude = hypot(rfk, ifk)
            val barHeight = (magnitude / 64f * height).coerceIn(8f, height.toFloat())
            val left = i * barWidth + 4f
            val right = (i + 1) * barWidth - 4f
            val top = height - barHeight
            canvas.drawRect(left, top, right, height.toFloat(), barPaint)
            canvas.drawRect(left, (top - 8f).coerceAtLeast(0f), right, top - 2f, peakPaint)
        }
    }
}
