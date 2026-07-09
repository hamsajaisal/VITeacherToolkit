package com.viteacher.toolkit.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.viteacher.toolkit.ui.MegaphoneActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class MegaphoneService : Service() {

    private val binder = MegaphoneBinder()
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isStreaming = false
    private var recordingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // Flow for real-time sound amplitude (for the visualizer)
    private val _amplitudeFlow = MutableStateFlow(0)
    val amplitudeFlow: StateFlow<Int> = _amplitudeFlow

    private val _isMutedFlow = MutableStateFlow(false)
    val isMutedFlow: StateFlow<Boolean> = _isMutedFlow

    private var gainFactor: Float = 1.0f

    var aecActive = false
        private set
    var nsActive = false
        private set

    inner class MegaphoneBinder : Binder() {
        fun getService(): MegaphoneService = this@MegaphoneService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        companionService = this
        _isRunningFlow.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        } else if (action == ACTION_MUTE_TOGGLE) {
            toggleMute()
            updateNotification()
        }

        startForeground(NOTIFICATION_ID, createNotification())
        if (!isStreaming) {
            startStreaming()
        }
        return START_STICKY
    }

    fun setGain(gain: Float) {
        gainFactor = gain
    }

    fun toggleMute() {
        _isMutedFlow.value = !_isMutedFlow.value
    }

    fun isMuted(): Boolean = _isMutedFlow.value

    private fun startStreaming() {
        isStreaming = true
        recordingJob = serviceScope.launch {
            val sampleRate = 44100
            val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
            val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
            val minBufSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
            
            if (minBufSizeIn == AudioRecord.ERROR || minBufSizeIn == AudioRecord.ERROR_BAD_VALUE ||
                minBufSizeOut == AudioTrack.ERROR || minBufSizeOut == AudioTrack.ERROR_BAD_VALUE) {
                isStreaming = false
                return@launch
            }

            val bufferSize = (minBufSizeIn * 2).coerceAtLeast(minBufSizeOut * 2)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    bufferSize
                )

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigOut)
                    .setEncoding(audioFormat)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                // Try to enable Acoustic Echo Cancellation (AEC)
                if (AcousticEchoCanceler.isAvailable()) {
                    val aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
                    if (aec != null) {
                        aec.enabled = true
                        aecActive = true
                    }
                }

                // Try to enable Noise Suppression (NS)
                if (NoiseSuppressor.isAvailable()) {
                    val ns = NoiseSuppressor.create(audioRecord!!.audioSessionId)
                    if (ns != null) {
                        ns.enabled = true
                        nsActive = true
                    }
                }

                audioRecord!!.startRecording()
                audioTrack!!.play()

                val buffer = ShortArray(bufferSize / 2)
                while (isStreaming) {
                    val read = audioRecord!!.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var maxVal = 0
                        if (_isMutedFlow.value) {
                            // Muted: write silence
                            for (i in 0 until read) {
                                buffer[i] = 0
                            }
                        } else {
                            // Process digital gain boost
                            for (i in 0 until read) {
                                val original = buffer[i]
                                val absOriginal = abs(original.toInt())
                                if (absOriginal > maxVal) {
                                    maxVal = absOriginal
                                }

                                if (gainFactor != 1.0f) {
                                    val amplified = (original * gainFactor).toInt()
                                    buffer[i] = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                }
                            }
                        }

                        // Send max amplitude back to visualizer flow (normalized between 0 and 100)
                        val normAmplitude = ((maxVal.toFloat() / Short.MAX_VALUE.toFloat()) * 100).toInt()
                        _amplitudeFlow.value = normAmplitude

                        audioTrack!!.write(buffer, 0, read)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                isStreaming = false
            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
            }
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
        _amplitudeFlow.value = 0
    }

    override fun onDestroy() {
        stopStreaming()
        companionService = null
        _isRunningFlow.value = false
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "megaphone_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Megaphone Audio Stream",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openActivityIntent = Intent(this, MegaphoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionText = if (_isMutedFlow.value) "Unmute" else "Mute"
        val muteIntent = Intent(this, MegaphoneService::class.java).apply {
            action = ACTION_MUTE_TOGGLE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, 1, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MegaphoneService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (_isMutedFlow.value) "Microphone is muted" else "Voice is streaming to speaker"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth Megaphone Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, muteActionText, mutePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    companion object {
        private const val NOTIFICATION_ID = 8881
        const val ACTION_STOP = "com.viteacher.toolkit.util.MegaphoneService.ACTION_STOP"
        const val ACTION_MUTE_TOGGLE = "com.viteacher.toolkit.util.MegaphoneService.ACTION_MUTE_TOGGLE"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow

        var companionService: MegaphoneService? = null
            private set
    }
}
