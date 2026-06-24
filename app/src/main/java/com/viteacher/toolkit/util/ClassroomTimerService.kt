package com.viteacher.toolkit.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.SchoolPeriod
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.ui.ClassroomTimerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class AlertMode {
    AUDIO_ONLY,
    BEEP_ONLY,
    VIBRATE_ONLY,
    AUDIO_AND_VIBRATE,
    BEEP_AND_VIBRATE;

    companion object {
        fun fromString(value: String): AlertMode {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                AUDIO_ONLY
            }
        }
    }
}

class ClassroomTimerService : Service(), TextToSpeech.OnInitListener {

    private val binder = TimerBinder()
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var volumeHelper: TtsVolumeHelper? = null

    var alertMode: AlertMode = AlertMode.AUDIO_ONLY

    private var wakeLock: PowerManager.WakeLock? = null

    var autoMonitorEnabled = false
    private var todayPeriods = listOf<SchoolPeriod>()
    private var todayTimetableEntries = listOf<TimetableEntry>()
    private var lastLoadedDay = ""
    private var lastAutoStartedPeriodNumber = -1

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ClassroomTimerService::WakeLock"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max safety limit
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    fun enableAutoMonitor(enabled: Boolean) {
        autoMonitorEnabled = enabled
        if (enabled) {
            refreshTodayTimetable()
            startTickerIfNeeded()
        } else {
            stopSelfCleanlyIfIdle()
        }
    }

    private fun refreshTodayTimetable() {
        val currentDay = getCurrentDayOfWeek()
        lastLoadedDay = currentDay
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val periods = db.timetableDao().getAllPeriodsOnce()
                
                val todayPeriodsFiltered = if (periods.any { it.isException && it.exceptionDay == currentDay }) {
                    periods.filter { it.isException && it.exceptionDay == currentDay }
                } else {
                    periods.filter { !it.isException }
                }
                todayPeriods = todayPeriodsFiltered.sortedBy { parseTimeToMinutes(it.startTime) }
                
                val entries = db.timetableDao().getAllEntriesOnce()
                todayTimetableEntries = entries.filter { it.dayOfWeek == currentDay }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getCurrentDayOfWeek(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Monday"
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        val cleanTime = timeStr.trim().uppercase()
        val timeParts = cleanTime.replace(" AM", "").replace(" PM", "").split(":")
        if (timeParts.size < 2) return 0
        var hour = timeParts[0].trim().toIntOrNull() ?: 0
        val minute = timeParts[1].trim().toIntOrNull() ?: 0
        if (cleanTime.contains("PM") && hour != 12) hour += 12
        if (cleanTime.contains("AM") && hour == 12) hour = 0
        return hour * 60 + minute
    }

    private fun deserializePhases(jsonStr: String): List<WorkflowPhase> {
        val list = mutableListOf<WorkflowPhase>()
        if (jsonStr.isNotEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val duration = jsonObject.getInt("duration")
                    list.add(WorkflowPhase(name, duration))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    companion object {
        const val CHANNEL_ID = "classroom_timer_channel"
        const val NOTIFICATION_ID = 2
    }

    inner class TimerBinder : Binder() {
        fun getService(): ClassroomTimerService = this@ClassroomTimerService
    }

    override fun onCreate() {
        super.onCreate()
        volumeHelper = TtsVolumeHelper(this)
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val selectedEngine = prefs.getString("tts_engine", null)
        tts = if (!selectedEngine.isNullOrEmpty()) {
            TextToSpeech(this, this, selectedEngine)
        } else {
            TextToSpeech(this, this)
        }
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Classroom Timer is active"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Classroom Timer is active"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Classroom Timer is active"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Classroom Timer is active"))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    volumeHelper?.restoreVolume()
                }
                override fun onError(utteranceId: String?) {
                    volumeHelper?.restoreVolume()
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    volumeHelper?.restoreVolume()
                }
                override fun onStart(utteranceId: String?) {}
            })

            val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val speed = prefs.getFloat("tts_speed", 0.9f)
            tts?.language = Locale.ENGLISH
            tts?.setSpeechRate(speed)
            ttsInitialized = true
        }
    }

    // --- ALERTS ENGINE ---
    fun playAlert(text: String, isFinal: Boolean = false) {
        val shouldVibrate = alertMode == AlertMode.VIBRATE_ONLY ||
                            alertMode == AlertMode.AUDIO_AND_VIBRATE ||
                            alertMode == AlertMode.BEEP_AND_VIBRATE
        if (shouldVibrate) {
            val vibrateDuration = if (isFinal) 1000L else 200L
            triggerVibration(vibrateDuration)
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isNormalRinger = audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL

        if (isNormalRinger) {
            when (alertMode) {
                AlertMode.AUDIO_ONLY -> {
                    speakText(text)
                }
                AlertMode.BEEP_ONLY -> {
                    triggerBeep(isFinal)
                }
                AlertMode.VIBRATE_ONLY -> {
                    // Already vibrated
                }
                AlertMode.AUDIO_AND_VIBRATE -> {
                    speakText(text)
                }
                AlertMode.BEEP_AND_VIBRATE -> {
                    triggerBeep(isFinal)
                }
            }
        }
    }

    private fun speakText(text: String) {
        if (ttsInitialized && tts != null) {
            val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val volume = prefs.getFloat("tts_volume", 1.0f)
            volumeHelper?.setVolume(volume)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "classroom_timer_utterance")
        }
    }

    private fun triggerBeep(isFinal: Boolean) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            if (isFinal) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500)
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerVibration(durationMs: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Classroom Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Handles active Classroom Timer features"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val intent = Intent(this, ClassroomTimerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Classroom Timer")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    // --- SERVICE TICK ENGINE ---
    private val handler = Handler(Looper.getMainLooper())
    private var tickerRunning = false

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (autoMonitorEnabled) {
                checkAutoMonitorTimetable()
            }
            tickWorkflowTimer()
            tickActivityTimer()
            tickStopwatch()
            if (isAnyTimerRunning()) {
                val status = getActiveTimerStatusMessage()
                updateNotification(status)
                handler.postDelayed(this, 1000)
            } else {
                tickerRunning = false
                releaseWakeLock()
                updateNotification("Classroom Timer is active")
                stopSelfCleanlyIfIdle()
            }
        }
    }

    private fun checkAutoMonitorTimetable() {
        val currentDay = getCurrentDayOfWeek()
        if (currentDay != lastLoadedDay) {
            refreshTodayTimetable()
            return
        }

        if (todayPeriods.isEmpty()) return

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val activePeriods = todayPeriods.filter { it.periodNumber !in listOf(99, 100, 101) }
        var currentPeriod: SchoolPeriod? = null
        for (p in activePeriods) {
            val startMin = parseTimeToMinutes(p.startTime)
            val endMin = parseTimeToMinutes(p.endTime)
            if (currentMinutes in startMin until endMin) {
                currentPeriod = p
                break
            }
        }

        if (currentPeriod != null) {
            val entry = todayTimetableEntries.find { it.periodNumber == currentPeriod.periodNumber }
            if (entry != null) {
                if (!isWorkflowRunning && lastAutoStartedPeriodNumber != currentPeriod.periodNumber) {
                    autoStartWorkflowForPeriod(currentPeriod, entry)
                }
            }
        } else {
            if (lastAutoStartedPeriodNumber != -1) {
                val lastPeriod = activePeriods.find { it.periodNumber == lastAutoStartedPeriodNumber }
                if (lastPeriod != null) {
                    val startMin = parseTimeToMinutes(lastPeriod.startTime)
                    val endMin = parseTimeToMinutes(lastPeriod.endTime)
                    if (currentMinutes !in startMin until endMin) {
                        lastAutoStartedPeriodNumber = -1
                    }
                } else {
                    lastAutoStartedPeriodNumber = -1
                }
            }
        }
    }

    private fun autoStartWorkflowForPeriod(period: SchoolPeriod, entry: TimetableEntry) {
        lastAutoStartedPeriodNumber = period.periodNumber

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val warningOption = prefs.getString("workflow_reminder_interval", "5 minutes before")
        val warningMin = when (warningOption) {
            "5 minutes before" -> 5
            "10 minutes before" -> 10
            "No reminder" -> null
            "Custom..." -> prefs.getString("workflow_custom_warning_minutes", "5")?.toIntOrNull() ?: 5
            else -> 5
        }

        val lastUsedJson = prefs.getString("workflow_last_used_phases", "") ?: ""
        val phases = if (lastUsedJson.isNotEmpty()) {
            deserializePhases(lastUsedJson)
        } else {
            emptyList()
        }

        workflowPhases = phases.toMutableList()

        val endMin = parseTimeToMinutes(period.endTime)
        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endMin / 60)
            set(Calendar.MINUTE, endMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startMin = parseTimeToMinutes(period.startTime)
        val totalPeriodDurationSec = (endMin - startMin) * 60
        val remainingMs = endCalendar.timeInMillis - System.currentTimeMillis()
        val remainingSec = (remainingMs / 1000).toInt()

        val isCollege = prefs.getString("institution_type", "school") == "college"
        val periodLabel = if (isCollege) "Hour" else "Period"
        val periodName = entry.subject.ifEmpty { "$periodLabel ${period.periodNumber}" }

        val warningTimeMs = if (warningMin != null) {
            endCalendar.timeInMillis - (warningMin * 60 * 1000)
        } else null

        val finalWarningMin = if (warningTimeMs != null && warningTimeMs > System.currentTimeMillis()) warningMin else null
        val finalWarningTimeMs = if (warningTimeMs != null && warningTimeMs > System.currentTimeMillis()) warningTimeMs else null

        startWorkflow(finalWarningMin, finalWarningTimeMs, periodName, remainingSec, totalPeriodDurationSec)
    }

    private fun startTickerIfNeeded() {
        if (!tickerRunning) {
            tickerRunning = true
            acquireWakeLock()
            handler.post(tickerRunnable)
        }
    }

    private fun isAnyTimerRunning(): Boolean {
        return isWorkflowRunning || isActivityRunning || isStopwatchRunning || autoMonitorEnabled
    }

    private fun stopSelfCleanlyIfIdle() {
        if (!isAnyTimerRunning()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun getActiveTimerStatusMessage(): String {
        return when {
            isWorkflowRunning -> {
                val formattedTime = formatTimeMMSS(phaseRemainingSeconds)
                if (workflowPhases.isNotEmpty()) {
                    val currentPhase = workflowPhases.getOrNull(currentPhaseIndex)?.name ?: "Phase"
                    "$currentPhase: $formattedTime remaining"
                } else {
                    val periodName = warningPeriodName ?: "Period"
                    "$periodName: $formattedTime remaining"
                }
            }
            isActivityRunning -> {
                val formattedTime = formatTimeMMSS(activityRemainingSeconds)
                "Activity: $formattedTime remaining"
            }
            isStopwatchRunning -> {
                val formattedTime = formatTimeMMSS(stopwatchElapsedSeconds)
                "Stopwatch: $formattedTime elapsed"
            }
            autoMonitorEnabled -> {
                "Auto-monitoring timetable periods..."
            }
            else -> "Classroom Timer is active"
        }
    }

    // --- 1. PERIOD WORKFLOW TIMER STATE ---
    data class WorkflowPhase(val name: String, val durationMinutes: Int)
    
    interface WorkflowListener {
        fun onTick(phaseName: String, remainingSec: Int, durationMin: Int, progress: Float)
        fun onStateChanged(isRunning: Boolean, isPaused: Boolean)
        fun onPeriodWarning(message: String)
    }

    var workflowPhases = mutableListOf<WorkflowPhase>()
    var currentPhaseIndex = 0
    var phaseRemainingSeconds = 0
    var isWorkflowRunning = false
    var isWorkflowPaused = false
    var workflowListener: WorkflowListener? = null

    // Period warning parameters
    var warningMinutes: Int? = null
    var warningTimeMs: Long? = null
    var warningPeriodName: String? = null
    var warningFired = false
    var totalPeriodDurationSeconds = 0

    fun startWorkflow(warnMin: Int?, warnTimeMs: Long?, periodName: String?, periodRemainingSec: Int = 0, totalPeriodDurationSec: Int = 0) {
        val isEmptyPhases = workflowPhases.isEmpty()
        if (isEmptyPhases && periodRemainingSec <= 0) return
        
        currentPhaseIndex = 0
        totalPeriodDurationSeconds = totalPeriodDurationSec
        
        if (isEmptyPhases) {
            phaseRemainingSeconds = periodRemainingSec
        } else {
            phaseRemainingSeconds = workflowPhases[0].durationMinutes * 60
        }
        isWorkflowRunning = true
        isWorkflowPaused = false
        
        warningMinutes = warnMin
        warningTimeMs = warnTimeMs
        warningPeriodName = periodName
        warningFired = false

        if (isEmptyPhases) {
            val startAnnounce = "Period timer started. Monitoring $periodName."
            playAlert(startAnnounce)
        } else {
            val firstPhase = workflowPhases[0]
            val startAnnounce = "Period timer started. Your ${workflowPhases.size} phase workflow has begun. First phase — ${firstPhase.name} — ${firstPhase.durationMinutes} minutes."
            playAlert(startAnnounce)
        }

        workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
        startTickerIfNeeded()
    }

    fun pauseWorkflow() {
        if (!isWorkflowRunning || isWorkflowPaused) return
        isWorkflowPaused = true
        playAlert("Timer paused.")
        workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
    }

    fun resumeWorkflow() {
        if (!isWorkflowRunning || !isWorkflowPaused) return
        isWorkflowPaused = false
        val remainingText = formatDurationForTTS(phaseRemainingSeconds)
        if (workflowPhases.isNotEmpty()) {
            val currentPhase = workflowPhases[currentPhaseIndex]
            playAlert("Timer resumed. ${currentPhase.name} — $remainingText remaining.")
        } else {
            playAlert("Timer resumed. $warningPeriodName — $remainingText remaining.")
        }
        workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
    }

    fun stopWorkflow() {
        if (!isWorkflowRunning) return
        isWorkflowRunning = false
        isWorkflowPaused = false
        playAlert("Timer stopped.")
        workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
    }

    private fun tickWorkflowTimer() {
        if (!isWorkflowRunning || isWorkflowPaused) return
        phaseRemainingSeconds--

        val isEmptyPhases = workflowPhases.isEmpty()

        // Check if 1 minute remains in the current phase/period
        if (phaseRemainingSeconds == 60) {
            if (!isEmptyPhases) {
                val currentPhase = workflowPhases[currentPhaseIndex]
                playAlert("One minute remaining in ${currentPhase.name}.")
            } else {
                playAlert("One minute remaining in $warningPeriodName.")
            }
        }

        // Check period end warning
        val warningMs = warningTimeMs
        val warnMin = warningMinutes
        val periodName = warningPeriodName
        if (warningMs != null && warnMin != null && !warningFired) {
            if (System.currentTimeMillis() >= warningMs) {
                val warnText = "Attention — $periodName ends in $warnMin minutes. Please begin wrapping up."
                playAlert(warnText)
                warningFired = true
                workflowListener?.onPeriodWarning(warnText)
            }
        }

        if (isEmptyPhases) {
            val totalSec = totalPeriodDurationSeconds
            val progress = if (totalSec > 0) (totalSec - phaseRemainingSeconds).toFloat() / totalSec else 0f
            
            if (phaseRemainingSeconds <= 0) {
                isWorkflowRunning = false
                isWorkflowPaused = false
                playAlert("${warningPeriodName ?: "Period"} complete. Well done!")
                workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
            } else {
                workflowListener?.onTick(warningPeriodName ?: "Period", phaseRemainingSeconds, totalSec / 60, progress)
            }
        } else {
            val currentPhase = workflowPhases[currentPhaseIndex]
            val totalSec = currentPhase.durationMinutes * 60
            val progress = if (totalSec > 0) (totalSec - phaseRemainingSeconds).toFloat() / totalSec else 0f

            if (phaseRemainingSeconds <= 0) {
                val prevPhaseName = currentPhase.name
                currentPhaseIndex++
                if (currentPhaseIndex < workflowPhases.size) {
                    val nextPhase = workflowPhases[currentPhaseIndex]
                    phaseRemainingSeconds = nextPhase.durationMinutes * 60
                    playAlert("$prevPhaseName complete. Starting ${nextPhase.name} — ${nextPhase.durationMinutes} minutes remaining in this phase.")
                    workflowListener?.onTick(nextPhase.name, phaseRemainingSeconds, nextPhase.durationMinutes, 0f)
                } else {
                    isWorkflowRunning = false
                    isWorkflowPaused = false
                    playAlert("All phases complete. Well done!")
                    workflowListener?.onStateChanged(isWorkflowRunning, isWorkflowPaused)
                }
            } else {
                workflowListener?.onTick(currentPhase.name, phaseRemainingSeconds, currentPhase.durationMinutes, progress)
            }
        }
    }

    // --- 2. ACTIVITY TIMER STATE ---
    interface ActivityListener {
        fun onTick(remainingSec: Int, totalSec: Int, progress: Float)
        fun onStateChanged(isRunning: Boolean, isPaused: Boolean)
    }

    var activityTotalSeconds = 0
    var activityRemainingSeconds = 0
    var isActivityRunning = false
    var isActivityPaused = false
    var activityListener: ActivityListener? = null

    private var activityHalfwayFired = false
    private var activity10sFired = false
    private val activityCountdownSpoken = mutableSetOf<Int>()

    fun startActivityTimer(durationSec: Int) {
        if (durationSec <= 0) return
        activityTotalSeconds = durationSec
        activityRemainingSeconds = durationSec
        isActivityRunning = true
        isActivityPaused = false

        activityHalfwayFired = false
        activity10sFired = false
        activityCountdownSpoken.clear()

        val remainingText = formatDurationForTTS(activityRemainingSeconds)
        playAlert("Activity timer started. $remainingText remaining.")

        activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
        startTickerIfNeeded()
    }

    fun pauseActivityTimer() {
        if (!isActivityRunning || isActivityPaused) return
        isActivityPaused = true
        val remainingText = formatDurationForTTS(activityRemainingSeconds)
        playAlert("Timer paused. $remainingText remaining.")
        activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
    }

    fun resumeActivityTimer() {
        if (!isActivityRunning || !isActivityPaused) return
        isActivityPaused = false
        playAlert("Timer resumed.")
        activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
    }

    fun restartActivityTimer() {
        activityRemainingSeconds = activityTotalSeconds
        isActivityRunning = true
        isActivityPaused = false
        activityHalfwayFired = false
        activity10sFired = false
        activityCountdownSpoken.clear()

        playAlert("Timer restarted.")
        activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
        startTickerIfNeeded()
    }

    fun stopActivityTimer() {
        if (!isActivityRunning) return
        isActivityRunning = false
        isActivityPaused = false
        activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
    }

    private fun tickActivityTimer() {
        if (!isActivityRunning || isActivityPaused) return
        activityRemainingSeconds--

        // Check halfway
        if (!activityHalfwayFired && activityRemainingSeconds <= activityTotalSeconds / 2) {
            val remainingText = formatDurationForTTS(activityRemainingSeconds)
            playAlert("Halfway there. $remainingText remaining.")
            activityHalfwayFired = true
        }

        // Check 10 seconds remaining
        if (!activity10sFired && activityRemainingSeconds == 10) {
            playAlert("10 seconds remaining.")
            activity10sFired = true
        }

        // Check 5, 4, 3, 2, 1 seconds countdown
        if (activityRemainingSeconds in 1..5 && !activityCountdownSpoken.contains(activityRemainingSeconds)) {
            playAlert("$activityRemainingSeconds")
            activityCountdownSpoken.add(activityRemainingSeconds)
        }

        val progress = if (activityTotalSeconds > 0) (activityTotalSeconds - activityRemainingSeconds).toFloat() / activityTotalSeconds else 0f

        if (activityRemainingSeconds <= 0) {
            isActivityRunning = false
            isActivityPaused = false
            playAlert("Time is up!", isFinal = true)
            activityListener?.onStateChanged(isActivityRunning, isActivityPaused)
        } else {
            activityListener?.onTick(activityRemainingSeconds, activityTotalSeconds, progress)
        }
    }

    // --- 3. STOPWATCH STATE ---
    interface StopwatchListener {
        fun onTick(elapsedSec: Int)
        fun onLapAdded(laps: List<String>)
        fun onStateChanged(isRunning: Boolean)
    }

    var stopwatchElapsedSeconds = 0
    var isStopwatchRunning = false
    var stopwatchLaps = mutableListOf<String>()
    var stopwatchListener: StopwatchListener? = null

    fun startStopwatch() {
        isStopwatchRunning = true
        playAlert("Stopwatch started.")
        stopwatchListener?.onStateChanged(isStopwatchRunning)
        startTickerIfNeeded()
    }

    fun stopStopwatch() {
        if (!isStopwatchRunning) return
        isStopwatchRunning = false
        val totalText = formatDurationForTTS(stopwatchElapsedSeconds)
        playAlert("Stopwatch stopped. Total time — $totalText.")
        stopwatchListener?.onStateChanged(isStopwatchRunning)
    }

    fun recordLap() {
        if (!isStopwatchRunning) return
        val lapNum = stopwatchLaps.size + 1
        val formattedTime = formatTimeMMSS(stopwatchElapsedSeconds)
        val lapText = "Lap $lapNum — $formattedTime"
        stopwatchLaps.add(0, lapText) // add to top
        val speakLapText = formatDurationForTTS(stopwatchElapsedSeconds)
        playAlert("Lap $lapNum recorded at $speakLapText.")
        stopwatchListener?.onLapAdded(stopwatchLaps)
    }

    fun resetStopwatch() {
        stopwatchElapsedSeconds = 0
        stopwatchLaps.clear()
        isStopwatchRunning = false
        playAlert("Stopwatch reset.")
        stopwatchListener?.onTick(0)
        stopwatchListener?.onLapAdded(emptyList())
        stopwatchListener?.onStateChanged(isStopwatchRunning)
    }

    private fun tickStopwatch() {
        if (!isStopwatchRunning) return
        stopwatchElapsedSeconds++
        stopwatchListener?.onTick(stopwatchElapsedSeconds)
    }

    // --- COMMON HELPERS ---
    fun formatDurationForTTS(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "$m minute${if (m > 1) "s" else ""} and $s second${if (s > 1) "s" else ""}"
            m > 0 -> "$m minute${if (m > 1) "s" else ""}"
            else -> "$s second${if (s > 1) "s" else ""}"
        }
    }

    fun formatTimeMMSS(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickerRunnable)
        releaseWakeLock()
        tts?.stop()
        tts?.shutdown()
        volumeHelper?.restoreVolume()
        super.onDestroy()
    }
}
