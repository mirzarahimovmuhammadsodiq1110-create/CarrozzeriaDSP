package com.carrozzeria.dspplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioPlayerService : Service() {

    private val binder = LocalBinder()
    var mediaPlayer: MediaPlayer? = null
    lateinit var audioEngine: PioneerAudioEngine

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        audioEngine = PioneerAudioEngine(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun playAudio(uri: Uri, onPrepared: (Int) -> Unit) {
        startForeground(1, buildNotification("Carrozzeria DSP ишламоқда"))
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(this@AudioPlayerService, uri)
            prepare()
            audioEngine.attachToSession(audioSessionId)
            start()
            onPrepared(audioSessionId)
        }
    }

    fun togglePlayPause(): Boolean {
        val mp = mediaPlayer ?: return false
        return if (mp.isPlaying) {
            mp.pause()
            false
        } else {
            mp.start()
            true
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "dsp_channel", "Carrozzeria DSP Playback", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "dsp_channel")
            .setContentTitle("Carrozzeria DSP Player")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        mediaPlayer?.release()
    }
}
