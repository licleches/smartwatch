package com.example.appnew.presentation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Hace que suene como una alarma real: reproduce el tono de alarma en bucle (volumen de ALARMA)
 * y vibra sin parar hasta que el usuario pulse Tomé / Posponer (o pasen 2 minutos).
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra("id", -1) ?: -1
        val med = MedStore.load(this).find { it.id == id }
        startForeground(
            id, Reminders.buildNotification(this, id, med?.name ?: "Medicamento", med?.timeText ?: ""),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        ring()
        // Abre la pantalla de alarma encima de lo que se esté haciendo
        // (funciona con el permiso "Mostrar sobre otras apps" o con la pantalla apagada).
        try {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .putExtra("id", id).putExtra("name", med?.name ?: "Medicamento")
                    .putExtra("time", med?.timeText ?: "").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, 120_000)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun ring() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        try {
            val audio = getSystemService(AudioManager::class.java)
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0)   // que no quede en silencio
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0)
        } catch (e: Exception) { }
        player?.release(); player = null
        for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, RingtoneManager.getDefaultUri(type))
                    isLooping = true
                    prepare(); start()
                }
                break
            } catch (e: Exception) { player?.release(); player = null }
        }
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0), attrs
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
        super.onDestroy()
    }
}