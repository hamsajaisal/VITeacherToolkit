package com.viteacher.toolkit.util

import android.content.Context
import android.media.AudioManager

class TtsVolumeHelper(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalVolume: Int = -1

    fun setVolume(volumePref: Float) {
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            if (originalVolume == -1) {
                originalVolume = currentVol
            }
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val targetVol = (maxVol * volumePref).toInt().coerceIn(0, maxVol)
            if (targetVol != currentVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, targetVol, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreVolume() {
        try {
            if (originalVolume != -1) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalVolume, 0)
                originalVolume = -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
