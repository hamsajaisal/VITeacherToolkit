package com.viteacher.toolkit.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.viteacher.toolkit.R
import java.util.Locale

class TTSReminderService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var message: String = ""
    private var language: String = "en"

    companion object {
        const val CHANNEL_ID = "tts_reminder_channel"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_LANGUAGE = "extra_language"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        message = intent?.getStringExtra(EXTRA_MESSAGE) ?: ""
        language = intent?.getStringExtra(EXTRA_LANGUAGE) ?: "en"

        if (message.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(message)
        startForeground(1, notification)

        tts = TextToSpeech(this, this)

        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            val locale = if (language == "ml") Locale("ml", "IN") else Locale.ENGLISH
            val result = tts!!.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts!!.setLanguage(Locale.ENGLISH)
            }

            tts!!.setSpeechRate(0.9f)
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    stopSelfCleanly()
                }
                override fun onError(utteranceId: String?) {
                    stopSelfCleanly()
                }
                override fun onStart(utteranceId: String?) {}
            })

            tts!!.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reminder_utterance")
        } else {
            stopSelfCleanly()
        }
    }

    private fun stopSelfCleanly() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Class Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Announces upcoming classes"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Class Reminder")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}