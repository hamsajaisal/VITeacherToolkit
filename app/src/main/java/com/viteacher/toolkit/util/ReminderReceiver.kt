package com.viteacher.toolkit.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return
        val language = intent.getStringExtra("language") ?: "en"

        val serviceIntent = Intent(context, TTSReminderService::class.java).apply {
            putExtra(TTSReminderService.EXTRA_MESSAGE, message)
            putExtra(TTSReminderService.EXTRA_LANGUAGE, language)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}