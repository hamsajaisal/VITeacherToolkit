package com.viteacher.toolkit.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.viteacher.toolkit.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Intercept and skip announcement if today's date is silenced
        val prefs = context.getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val silentDates = prefs.getStringSet("silent_dates", emptySet()) ?: emptySet()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (silentDates.contains(todayStr)) {
            // Even if silenced today, reschedule for next week so it doesn't stop repeating!
            rescheduleForNextWeek(context, intent)
            return
        }

        val message = intent.getStringExtra("message") ?: return
        val language = intent.getStringExtra("language") ?: "en"

        val serviceIntent = Intent(context, TTSReminderService::class.java).apply {
            putExtra(TTSReminderService.EXTRA_MESSAGE, message)
            putExtra(TTSReminderService.EXTRA_LANGUAGE, language)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // Reschedule for next week
        rescheduleForNextWeek(context, intent)
    }

    private fun rescheduleForNextWeek(context: Context, intent: Intent) {
        val isBreak = intent.getBooleanExtra("is_break", false)
        if (isBreak) {
            val periodNumber = intent.getIntExtra("period_number", -1)
            if (periodNumber != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context.applicationContext)
                        val periods = db.timetableDao().getAllPeriodsOnce()
                        val period = periods.find { it.periodNumber == periodNumber }
                        if (period != null) {
                            ReminderScheduler.scheduleBreakReminder(context.applicationContext, period)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return
        }

        val entryId = intent.getIntExtra("entry_id", -1)
        val language = intent.getStringExtra("language") ?: "en"
        if (entryId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val entry = db.timetableDao().getEntryById(entryId)
                    val periods = db.timetableDao().getAllPeriodsOnce()
                    if (entry != null) {
                        val matchingPeriod = periods.find { it.periodNumber == entry.periodNumber }
                        if (matchingPeriod != null) {
                            ReminderScheduler.scheduleReminder(
                                context.applicationContext,
                                entry,
                                matchingPeriod,
                                language
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}