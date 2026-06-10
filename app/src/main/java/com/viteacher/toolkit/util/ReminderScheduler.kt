package com.viteacher.toolkit.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.data.SchoolPeriod
import java.util.Calendar

object ReminderScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(
        context: Context,
        entry: TimetableEntry,
        period: SchoolPeriod,
        language: String
    ) {
        if (entry.reminderMinutesBefore < 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val message = buildMessage(entry, language)
        val triggerTime = calculateTriggerTime(entry, period)

        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("entry_id", entry.id)
            putExtra("message", message)
            putExtra("language", language)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, entryId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entryId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun buildMessage(entry: TimetableEntry, language: String): String {
        return if (language == "ml") {
            "അടുത്ത പിരീഡിൽ ${entry.subject} ക്ലാസ് ${entry.className} ${entry.division}"
        } else {
            if (entry.reminderMinutesBefore == 0) {
                "Your class ${entry.subject} for class ${entry.className} ${entry.division} is starting now"
            } else {
                "In ${entry.reminderMinutesBefore} minutes you have ${entry.subject} for class ${entry.className} ${entry.division}"
            }
        }
    }

    private fun calculateTriggerTime(entry: TimetableEntry, period: SchoolPeriod): Long {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        val targetDay = dayNameToCalendarDay(entry.dayOfWeek)

        var daysUntilTarget = (targetDay - today + 7) % 7

        val timeParts = period.startTime
            .replace(" AM", "").replace(" PM", "").split(":")
        var hour = timeParts[0].trim().toIntOrNull() ?: 9
        val minute = timeParts[1].trim().toIntOrNull() ?: 0

        if (period.startTime.contains("PM") && hour != 12) hour += 12
        if (period.startTime.contains("AM") && hour == 12) hour = 0

        if (daysUntilTarget == 0) {
            val classTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute - entry.reminderMinutesBefore)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (classTime.timeInMillis <= System.currentTimeMillis()) {
                daysUntilTarget = 7
            }
        }

        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysUntilTarget)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute - entry.reminderMinutesBefore)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun scheduleBreakReminder(context: Context, period: SchoolPeriod) {
        val prefs = context.getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("refreshment_alert_enabled_${period.periodNumber}", false)
        if (!isEnabled) {
            cancelBreakReminder(context, period.periodNumber)
            return
        }
        val warningMin = prefs.getInt("refreshment_alert_minutes_${period.periodNumber}", 5)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val daysToSchedule = if (period.isException) {
            listOf(period.exceptionDay)
        } else {
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        }

        daysToSchedule.forEach { dayName ->
            val calendarDay = dayNameToCalendarDay(dayName)
            val triggerTime = calculateBreakTriggerTime(period, calendarDay, warningMin)
            val requestCode = period.periodNumber * 10 + calendarDay
            
            val breakLabel = when (period.periodNumber) {
                99 -> "Forenoon Interval"
                100 -> "Lunch Break"
                101 -> "Afternoon Interval"
                else -> "Break"
            }
            val message = if (warningMin == 0) {
                "Your $breakLabel has ended. Please resume your schedule."
            } else {
                "Only $warningMin minutes remaining to end your $breakLabel."
            }

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("is_break", true)
                putExtra("break_name", breakLabel)
                putExtra("period_number", period.periodNumber)
                putExtra("message", message)
                putExtra("language", "en")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }

    fun cancelBreakReminder(context: Context, periodNumber: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val daysList = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
        daysList.forEach { calendarDay ->
            val requestCode = periodNumber * 10 + calendarDay
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun calculateBreakTriggerTime(period: SchoolPeriod, calendarDay: Int, warningMin: Int): Long {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        
        var daysUntilTarget = (calendarDay - today + 7) % 7

        val timeParts = period.endTime
            .replace(" AM", "").replace(" PM", "").split(":")
        var hour = timeParts[0].trim().toIntOrNull() ?: 12
        val minute = timeParts[1].trim().toIntOrNull() ?: 0

        if (period.endTime.contains("PM") && hour != 12) hour += 12
        if (period.endTime.contains("AM") && hour == 12) hour = 0

        var finalMin = minute - warningMin
        var finalHour = hour
        if (finalMin < 0) {
            finalHour -= 1
            finalMin += 60
            if (finalHour < 0) {
                finalHour += 24
            }
        }

        if (daysUntilTarget == 0) {
            val breakTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, finalHour)
                set(Calendar.MINUTE, finalMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (breakTime.timeInMillis <= System.currentTimeMillis()) {
                daysUntilTarget = 7
            }
        }

        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysUntilTarget)
            set(Calendar.HOUR_OF_DAY, finalHour)
            set(Calendar.MINUTE, finalMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dayNameToCalendarDay(day: String): Int {
        return when (day) {
            "Sunday" -> Calendar.SUNDAY
            "Monday" -> Calendar.MONDAY
            "Tuesday" -> Calendar.TUESDAY
            "Wednesday" -> Calendar.WEDNESDAY
            "Thursday" -> Calendar.THURSDAY
            "Friday" -> Calendar.FRIDAY
            "Saturday" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }
}