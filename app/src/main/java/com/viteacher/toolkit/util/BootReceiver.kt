package com.viteacher.toolkit.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.viteacher.toolkit.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val language = prefs.getString("reminder_language", "en") ?: "en"

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val entries = db.timetableDao().getAllEntriesOnce()
                val periods = db.timetableDao().getAllPeriodsOnce()

                entries.forEach { entry ->
                    if (entry.reminderMinutesBefore >= 0) {
                        val matchingPeriod = periods.find {
                            it.periodNumber == entry.periodNumber
                        }
                        if (matchingPeriod != null) {
                            ReminderScheduler.scheduleReminder(
                                context,
                                entry,
                                matchingPeriod,
                                language
                            )
                        }
                    }
                }

                periods.forEach { period ->
                    if (period.periodNumber in listOf(99, 100, 101)) {
                        ReminderScheduler.scheduleBreakReminder(context, period)
                    }
                }
            }
        }
    }
}