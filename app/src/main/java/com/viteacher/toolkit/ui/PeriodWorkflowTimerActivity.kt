package com.viteacher.toolkit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.SchoolPeriod
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.databinding.ActivityPeriodWorkflowTimerBinding
import com.viteacher.toolkit.databinding.DialogAddPhaseBinding
import com.viteacher.toolkit.util.ClassroomTimerService
import com.viteacher.toolkit.util.setAccessibleSelection
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch
import java.util.Locale

class PeriodWorkflowTimerActivity : AppCompatActivity(), ClassroomTimerService.WorkflowListener {

    private lateinit var binding: ActivityPeriodWorkflowTimerBinding
    private var timerService: ClassroomTimerService? = null
    private var isBound = false

    private var workflowPhases = mutableListOf<ClassroomTimerService.WorkflowPhase>()
    private var lastWasRunning = false
    private var lastWasPaused = false

    private var todayPeriodsList = listOf<SchoolPeriod>()
    private var todayTimetableEntries = listOf<TimetableEntry>()
    private var templateNamesList = listOf<String>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ClassroomTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true

            // Restore state if running
            timerService!!.workflowListener = this@PeriodWorkflowTimerActivity

            if (timerService!!.isWorkflowRunning) {
                workflowPhases = timerService!!.workflowPhases
                showRunningUI()
                updateStateUI(timerService!!.isWorkflowRunning, timerService!!.isWorkflowPaused)
                lastWasRunning = timerService!!.isWorkflowRunning
                lastWasPaused = timerService!!.isWorkflowPaused
                
                val currentService = timerService!!
                val isEmpty = currentService.workflowPhases.isEmpty()
                val phaseName = if (isEmpty) {
                    currentService.warningPeriodName ?: "Period"
                } else {
                    currentService.workflowPhases.getOrNull(currentService.currentPhaseIndex)?.name ?: ""
                }
                val prefix = if (isEmpty) "Monitoring: " else "Current Phase: "
                binding.tvCurrentPhase.text = "$prefix$phaseName"
                binding.tvCurrentPhase.contentDescription = "$prefix$phaseName"

                val formattedTime = currentService.formatTimeMMSS(currentService.phaseRemainingSeconds)
                binding.tvCountdown.text = "$formattedTime remaining"
                binding.tvCountdown.contentDescription = "$formattedTime remaining"

                val totalSec = if (isEmpty) {
                    currentService.totalPeriodDurationSeconds
                } else {
                    (currentService.workflowPhases.getOrNull(currentService.currentPhaseIndex)?.durationMinutes ?: 0) * 60
                }
                val progress = if (totalSec > 0) (totalSec - currentService.phaseRemainingSeconds).toFloat() / totalSec else 0f
                binding.pbWorkflow.progress = (progress * 100).toInt()

                // Show warning message if present
                val warningMs = currentService.warningTimeMs
                val periodName = currentService.warningPeriodName
                val warnMin = currentService.warningMinutes
                if (warningMs != null && periodName != null && warnMin != null) {
                    binding.tvWarningStatus.visibility = View.VISIBLE
                    binding.tvWarningStatus.text = "Warning set for $periodName at ${formatTimeFromMs(warningMs)}"
                }
            } else {
                // Load last used phases setup by default
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val lastUsedJson = prefs.getString("workflow_last_used_phases", "") ?: ""
                if (lastUsedJson.isNotEmpty()) {
                    workflowPhases = deserializePhases(lastUsedJson).toMutableList()
                } else {
                    workflowPhases = mutableListOf()
                }
                showSetupUI()
                lastWasRunning = false
                lastWasPaused = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeriodWorkflowTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind Service
        val intent = Intent(this, ClassroomTimerService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.contentDescription = "Go back"
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddPhase.contentDescription = "Add phase"
        binding.btnAddPhase.setOnClickListener {
            showAddPhaseDialog()
        }

        binding.btnSaveAsTemplate.setOnClickListener {
            showSaveTemplateDialog()
        }

        binding.btnStartWorkflow.contentDescription = "Start period workflow timer"
        binding.btnStartWorkflow.setOnClickListener {
            tryStartWorkflow()
        }

        binding.btnPauseResume.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (service.isWorkflowPaused) {
                service.resumeWorkflow()
            } else {
                service.pauseWorkflow()
            }
        }

        binding.btnStopReset.contentDescription = "Stop and reset timer"
        binding.btnStopReset.setOnClickListener {
            confirmStopWorkflow()
        }

        // Setup accessibility elements on EditTexts
        binding.etTotalDuration.contentDescription = "Total period duration in minutes, input field"
        binding.etPeriodWarning.contentDescription = "Custom warn me this many minutes before period ends, input field"

        // Make countdown area readable on tap
        binding.tvCountdown.setOnClickListener {
            timerService?.let { service ->
                if (service.isWorkflowRunning) {
                    val remainingText = service.formatDurationForTTS(service.phaseRemainingSeconds)
                    binding.tvCountdown.announceForAccessibility("$remainingText remaining")
                }
            }
        }

        setupReminderSpinner()
        setupTemplatesSpinner()
        loadTodayTimetable()
    }

    private fun showSetupUI() {
        binding.layoutSetup.visibility = View.VISIBLE
        binding.layoutRunning.visibility = View.GONE
        renderPhasesList()
    }

    private fun showRunningUI() {
        binding.layoutSetup.visibility = View.GONE
        binding.layoutRunning.visibility = View.VISIBLE
    }

    private fun renderPhasesList() {
        binding.layoutPhasesList.removeAllViews()
        var totalMinutes = 0
        workflowPhases.forEachIndexed { index, phase ->
            totalMinutes += phase.durationMinutes
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dpToPx())
                }
                text = "${phase.name} — ${phase.durationMinutes} minutes"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                setBackgroundColor(android.graphics.Color.parseColor("#FF1E1E1E"))
                isClickable = true
                isFocusable = true
                
                contentDescription = "${phase.name} — ${phase.durationMinutes} minutes"
                
                setOnLongClickListener {
                    showPopupMenu(this, index)
                    true
                }
            }
            binding.layoutPhasesList.addView(tv)
        }
        binding.tvTotalAssigned.text = "Total assigned: $totalMinutes minutes"

        // Update Start Button Visibility - always visible to allow zero-phase monitoring mode
        binding.btnStartWorkflow.visibility = View.VISIBLE
    }

    private fun showPopupMenu(anchorView: View, position: Int) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchorView)
        popup.menu.add("Delete")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Delete") {
                confirmDeletePhase(position)
            }
            true
        }
        popup.show()
    }

    private fun confirmDeletePhase(position: Int) {
        val phaseName = workflowPhases[position].name
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Phase")
            .setMessage("Are you sure you want to delete phase \"$phaseName\"?")
            .setPositiveButton("Yes") { _, _ ->
                workflowPhases.removeAt(position)
                renderPhasesList()
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes, delete phase"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No, cancel"
        binding.root.announceForAccessibility("Delete phase dialog. Are you sure you want to delete phase $phaseName? Choose Yes or No.")
    }

    private fun showAddPhaseDialog() {
        val dialog = AlertDialog.Builder(this).create()
        val dialogBinding = DialogAddPhaseBinding.inflate(LayoutInflater.from(this))
        dialog.setView(dialogBinding.root)

        // Setup accessibility descriptions for inputs
        dialogBinding.etPhaseName.contentDescription = "Phase name input field"
        dialogBinding.etPhaseDuration.contentDescription = "Phase duration in minutes input field"

        dialogBinding.btnCancel.contentDescription = "Cancel"
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.contentDescription = "Save"
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etPhaseName.text.toString().trim()
            val durationStr = dialogBinding.etPhaseDuration.text.toString().trim()
            val duration = durationStr.toIntOrNull()

            if (name.isEmpty()) {
                Toast.makeText(this, "Phase name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (duration == null || duration <= 0) {
                Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            workflowPhases.add(ClassroomTimerService.WorkflowPhase(name, duration))
            renderPhasesList()
            dialog.dismiss()
        }

        dialog.show()
        binding.root.announceForAccessibility("Add Phase dialog. Please enter phase name and duration, then save.")
    }

    private fun serializePhases(phases: List<ClassroomTimerService.WorkflowPhase>): String {
        val jsonArray = org.json.JSONArray()
        for (phase in phases) {
            val jsonObject = org.json.JSONObject()
            jsonObject.put("name", phase.name)
            jsonObject.put("duration", phase.durationMinutes)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun deserializePhases(jsonStr: String): List<ClassroomTimerService.WorkflowPhase> {
        val list = mutableListOf<ClassroomTimerService.WorkflowPhase>()
        if (jsonStr.isNotEmpty()) {
            try {
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val duration = jsonObject.getInt("duration")
                    list.add(ClassroomTimerService.WorkflowPhase(name, duration))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    private fun saveTemplate(name: String, phases: List<ClassroomTimerService.WorkflowPhase>) {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val serialized = serializePhases(phases)
        val templateNames = prefs.getStringSet("workflow_template_names", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        templateNames.add(name)
        
        prefs.edit()
            .putStringSet("workflow_template_names", templateNames)
            .putString("workflow_template_$name", serialized)
            .apply()
    }

    private fun getTemplates(): Map<String, List<ClassroomTimerService.WorkflowPhase>> {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val templateNames = prefs.getStringSet("workflow_template_names", emptySet()) ?: emptySet()
        val map = mutableMapOf<String, List<ClassroomTimerService.WorkflowPhase>>()
        for (name in templateNames) {
            val jsonStr = prefs.getString("workflow_template_$name", "") ?: ""
            if (jsonStr.isNotEmpty()) {
                map[name] = deserializePhases(jsonStr)
            }
        }
        return map
    }

    private fun showSaveTemplateDialog() {
        if (workflowPhases.isEmpty()) {
            Toast.makeText(this, "Please add at least one phase to save a template", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this)
        input.setupCursorEndForEditTexts()
        input.hint = "Example: Standard Lesson"
        input.contentDescription = "Template name, required"

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(48, 16, 48, 16)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Save as Template")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveTemplate(name, workflowPhases)
                    setupTemplatesSpinner()
                    // Select the newly saved template in the spinner
                    val index = templateNamesList.indexOf(name)
                    if (index >= 0) {
                        binding.spinnerPhaseTemplates.setSelection(index + 2)
                    }
                    Toast.makeText(this, "Template '$name' saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Template name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupTemplatesSpinner() {
        val options = mutableListOf<String>()
        options.add("Custom / Empty Setup")
        options.add("Last Used Setup")

        val templates = getTemplates()
        templateNamesList = templates.keys.toList()
        for (name in templateNamesList) {
            options.add("Template: $name")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPhaseTemplates.adapter = adapter
        binding.spinnerPhaseTemplates.setAccessibleSelection("Workflow phase template options")

        binding.spinnerPhaseTemplates.setAccessibleSelection("Workflow phase template options") { position ->
            if (position == 0) {
                // Custom / Empty Setup
            } else if (position == 1) {
                // Last Used Setup
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val lastUsedJson = prefs.getString("workflow_last_used_phases", "") ?: ""
                if (lastUsedJson.isNotEmpty()) {
                    workflowPhases.clear()
                    workflowPhases.addAll(deserializePhases(lastUsedJson))
                    renderPhasesList()
                    binding.root.announceForAccessibility("Loaded last used setup")
                } else {
                    Toast.makeText(this, "No last used setup found", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Custom template
                val templateName = templateNamesList[position - 2]
                val phases = templates[templateName]
                if (phases != null) {
                    workflowPhases.clear()
                    workflowPhases.addAll(phases)
                    renderPhasesList()
                    binding.root.announceForAccessibility("Loaded template $templateName")
                }
            }
        }
    }

    private fun setupReminderSpinner() {
        val options = listOf("5 minutes before", "10 minutes before", "No reminder", "Custom...")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerReminderInterval.adapter = adapter
        binding.spinnerReminderInterval.setAccessibleSelection("Period end reminder options")

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val savedInterval = prefs.getString("workflow_reminder_interval", "5 minutes before")
        val index = options.indexOf(savedInterval)
        if (index >= 0) {
            binding.spinnerReminderInterval.setSelection(index)
        }

        binding.spinnerReminderInterval.setAccessibleSelection("Period end reminder options") { position ->
            val selectedOption = options[position]
            prefs.edit().putString("workflow_reminder_interval", selectedOption).apply()
            
            if (selectedOption == "Custom...") {
                binding.tvPeriodWarningLabel.visibility = View.VISIBLE
                binding.etPeriodWarning.visibility = View.VISIBLE
            } else {
                binding.tvPeriodWarningLabel.visibility = View.GONE
                binding.etPeriodWarning.visibility = View.GONE
            }
        }
    }

    private fun getWarningMinutes(): Int? {
        val selectedOption = binding.spinnerReminderInterval.selectedItem.toString()
        return when (selectedOption) {
            "5 minutes before" -> 5
            "10 minutes before" -> 10
            "No reminder" -> null
            "Custom..." -> {
                val customStr = binding.etPeriodWarning.text.toString().trim()
                customStr.toIntOrNull()
            }
            else -> 5
        }
    }

    private fun loadTodayTimetable() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val periods = db.timetableDao().getAllPeriodsOnce()
            val currentDay = getCurrentDayOfWeek()

            val todayPeriods = if (periods.any { it.isException && it.exceptionDay == currentDay }) {
                periods.filter { it.isException && it.exceptionDay == currentDay }
            } else {
                periods.filter { !it.isException }
            }
            
            val sortedPeriods = todayPeriods.sortedBy { parseTimeToMinutes(it.startTime) }
            todayPeriodsList = sortedPeriods

            val entries = db.timetableDao().getAllEntriesOnce()
            todayTimetableEntries = entries.filter { it.dayOfWeek == currentDay }

            runOnUiThread {
                setupTimetableSpinner()
            }
        }
    }

    private fun setupTimetableSpinner() {
        val options = mutableListOf<String>()
        options.add("Manual Setup (None)")

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        val periodLabel = if (isCollege) "Hour" else "Period"

        val activePeriods = todayPeriodsList.filter { it.periodNumber !in listOf(99, 100, 101) }

        for (p in activePeriods) {
            val entry = todayTimetableEntries.find { it.periodNumber == p.periodNumber }
            val subjectName = entry?.subject ?: "Free"
            val timeRange = "${p.startTime} - ${p.endTime}"
            options.add("$periodLabel ${p.periodNumber}: $subjectName ($timeRange)")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimetablePeriods.adapter = adapter
        binding.spinnerTimetablePeriods.setAccessibleSelection("Timetable synchronization options")

        binding.spinnerTimetablePeriods.setAccessibleSelection("Timetable synchronization options") { position ->
            if (position == 0) {
                // Manual setup selected
            } else {
                val selectedPeriod = activePeriods[position - 1]
                val startMin = parseTimeToMinutes(selectedPeriod.startTime)
                val endMin = parseTimeToMinutes(selectedPeriod.endTime)
                val duration = endMin - startMin
                
                binding.etTotalDuration.setText(duration.toString())
            }
        }

        selectCurrentPeriodInSpinner()
    }

    private fun selectCurrentPeriodInSpinner() {
        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

        val activePeriods = todayPeriodsList.filter { it.periodNumber !in listOf(99, 100, 101) }
        var currentIdx = -1
        for (i in activePeriods.indices) {
            val p = activePeriods[i]
            val startMin = parseTimeToMinutes(p.startTime)
            val endMin = parseTimeToMinutes(p.endTime)
            if (currentMinutes in startMin until endMin) {
                currentIdx = i
                break
            }
        }

        if (currentIdx != -1) {
            binding.spinnerTimetablePeriods.setSelection(currentIdx + 1)
        }
    }

    private fun startWorkflowTimer(
        warningMin: Int?,
        warningTimeMs: Long?,
        periodName: String?,
        remainingSec: Int,
        totalPeriodDurationSec: Int,
        isEmptyPhases: Boolean
    ) {
        val intent = Intent(this, ClassroomTimerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        timerService?.workflowPhases = workflowPhases
        timerService?.startWorkflow(
            warningMin, 
            warningTimeMs, 
            periodName, 
            remainingSec, 
            totalPeriodDurationSec
        )
        showRunningUI()
        if (warningTimeMs != null && warningMin != null && periodName != null) {
            binding.tvWarningStatus.visibility = View.VISIBLE
            val labelText = "Warning set for $periodName at ${formatTimeFromMs(warningTimeMs)}"
            binding.tvWarningStatus.text = labelText
            binding.tvWarningStatus.announceForAccessibility(labelText)
        } else {
            binding.tvWarningStatus.visibility = View.GONE
        }
        updateStateUI(true, false)

        val prefix = if (isEmptyPhases) "Monitoring: " else "Current Phase: "
        val displayName = if (isEmptyPhases) (periodName ?: "Period") else (workflowPhases.getOrNull(0)?.name ?: "")
        binding.tvCurrentPhase.text = "$prefix$displayName"
        binding.tvCurrentPhase.contentDescription = "$prefix$displayName"

        val displayTimeSec = if (isEmptyPhases) remainingSec else ((workflowPhases.getOrNull(0)?.durationMinutes ?: 0) * 60)
        val formattedTime = timerService?.formatTimeMMSS(displayTimeSec) ?: "00:00"
        binding.tvCountdown.text = "$formattedTime remaining"
        binding.tvCountdown.contentDescription = "$formattedTime remaining"

        val progress = if (totalPeriodDurationSec > 0 && isEmptyPhases) {
            (totalPeriodDurationSec - remainingSec).toFloat() / totalPeriodDurationSec
        } else 0f
        binding.pbWorkflow.progress = (progress * 100).toInt()
    }

    private fun tryStartWorkflow() {
        val totalDurationStr = binding.etTotalDuration.text.toString().trim()
        val totalDurationMin = totalDurationStr.toIntOrNull()
        
        val warningMin = getWarningMinutes()

        val isEmptyPhases = workflowPhases.isEmpty()

        // 1. Initial Validation
        if (isEmptyPhases && warningMin == null && totalDurationMin == null) {
            val errorMsg = "Please add at least one phase, set a period end warning, or enter a total duration."
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.root.announceForAccessibility(errorMsg)
            return
        }

        // Save last used phases structure permanently
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("workflow_last_used_phases", serializePhases(workflowPhases)).apply()

        val spinnerPos = binding.spinnerTimetablePeriods.selectedItemPosition
        val isManual = (spinnerPos == 0)

        // Run db operations on IO thread
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var matchedPeriod: SchoolPeriod? = null
            val activePeriods = todayPeriodsList.filter { it.periodNumber !in listOf(99, 100, 101) }

            if (!isManual && spinnerPos - 1 < activePeriods.size) {
                matchedPeriod = activePeriods[spinnerPos - 1]
            }

            if (matchedPeriod != null) {
                // Timetable Period is active/selected
                val endMin = parseTimeToMinutes(matchedPeriod.endTime)
                val endCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, endMin / 60)
                    set(java.util.Calendar.MINUTE, endMin % 60)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                val db = AppDatabase.getDatabase(applicationContext)
                val entries = db.timetableDao().getAllEntriesOnce()
                val currentDay = getCurrentDayOfWeek()
                val entry = entries.find { it.dayOfWeek == currentDay && it.periodNumber == matchedPeriod.periodNumber }

                val isCollege = prefs.getString("institution_type", "school") == "college"
                val periodLabel = if (isCollege) "Hour" else "Period"
                val periodName = if (entry != null) entry.subject else "$periodLabel ${matchedPeriod.periodNumber}"

                val startMin = parseTimeToMinutes(matchedPeriod.startTime)
                val totalPeriodDurationSec = (endMin - startMin) * 60
                val remainingMs = endCalendar.timeInMillis - System.currentTimeMillis()
                val remainingSec = (remainingMs / 1000).toInt()

                val warningTimeMs = if (warningMin != null) {
                    endCalendar.timeInMillis - (warningMin * 60 * 1000)
                } else null

                if (warningTimeMs == null || warningTimeMs > System.currentTimeMillis()) {
                    runOnUiThread {
                        startWorkflowTimer(warningMin, warningTimeMs, periodName, remainingSec, totalPeriodDurationSec, isEmptyPhases)
                    }
                } else {
                    if (isEmptyPhases) {
                        runOnUiThread {
                            val remainingMinutes = remainingSec / 60
                            val errorMsg = "Warning time has already passed. Only $remainingMinutes minutes remaining in this period."
                            Toast.makeText(this@PeriodWorkflowTimerActivity, errorMsg, Toast.LENGTH_LONG).show()
                            binding.root.announceForAccessibility(errorMsg)
                        }
                    } else {
                        runOnUiThread {
                            val errorMsg = "Period end warning could not be set as the warning time has already passed."
                            Toast.makeText(this@PeriodWorkflowTimerActivity, errorMsg, Toast.LENGTH_LONG).show()
                            startWorkflowTimer(null, null, null, 0, 0, isEmptyPhases)
                        }
                    }
                }
            } else {
                // Manual setup Scenario
                if (totalDurationMin != null && totalDurationMin > 0) {
                    if (warningMin != null && warningMin >= totalDurationMin) {
                        runOnUiThread {
                            val errorMsg = "Warning minutes must be less than total period duration."
                            Toast.makeText(this@PeriodWorkflowTimerActivity, errorMsg, Toast.LENGTH_LONG).show()
                            binding.root.announceForAccessibility(errorMsg)
                        }
                    } else {
                        val totalPeriodDurationSec = totalDurationMin * 60
                        val remainingSec = totalPeriodDurationSec
                        val warningTimeMs = if (warningMin != null) {
                            System.currentTimeMillis() + (totalDurationMin - warningMin) * 60 * 1000L
                        } else null
                        
                        val periodName = "Class Period"
                        runOnUiThread {
                            startWorkflowTimer(warningMin, warningTimeMs, periodName, remainingSec, totalPeriodDurationSec, isEmptyPhases)
                        }
                    }
                } else {
                    if (isEmptyPhases) {
                        runOnUiThread {
                            val errorMsg = "Cannot start monitoring: No active class period in timetable and no total duration set."
                            Toast.makeText(this@PeriodWorkflowTimerActivity, errorMsg, Toast.LENGTH_LONG).show()
                            binding.root.announceForAccessibility(errorMsg)
                        }
                    } else {
                        runOnUiThread {
                            startWorkflowTimer(null, null, null, 0, 0, isEmptyPhases)
                        }
                    }
                }
            }
        }
    }

    private fun confirmStopWorkflow() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Stop Timer")
            .setMessage("Are you sure you want to stop the timer?")
            .setPositiveButton("Yes") { _, _ ->
                timerService?.stopWorkflow()
                showSetupUI()
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes, stop timer"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No, cancel"
        binding.root.announceForAccessibility("Stop timer dialog. Are you sure you want to stop the timer? Choose Yes or No.")
    }

    private fun updateStateUI(isRunning: Boolean, isPaused: Boolean) {
        val isEmptyPhases = workflowPhases.isEmpty()
        if (isEmptyPhases) {
            binding.btnPauseResume.isEnabled = false
            binding.btnPauseResume.text = "Pause"
            binding.btnPauseResume.contentDescription = "Pause disabled"
        } else {
            binding.btnPauseResume.isEnabled = true
            if (isPaused) {
                binding.btnPauseResume.text = "Resume"
                binding.btnPauseResume.contentDescription = "Resume"
            } else {
                binding.btnPauseResume.text = "Pause"
                binding.btnPauseResume.contentDescription = "Pause"
            }
        }
    }

    // --- WorkflowListener Callbacks ---
    override fun onTick(phaseName: String, remainingSec: Int, durationMin: Int, progress: Float) {
        runOnUiThread {
            val isEmptyPhases = workflowPhases.isEmpty()
            val prefix = if (isEmptyPhases) "Monitoring: " else "Current Phase: "
            binding.tvCurrentPhase.text = "$prefix$phaseName"
            binding.tvCurrentPhase.contentDescription = "$prefix$phaseName"
            
            val formattedTime = timerService?.formatTimeMMSS(remainingSec) ?: "00:00"
            binding.tvCountdown.text = "$formattedTime remaining"
            binding.tvCountdown.contentDescription = "$formattedTime remaining"

            binding.pbWorkflow.progress = (progress * 100).toInt()
        }
    }

    override fun onStateChanged(isRunning: Boolean, isPaused: Boolean) {
        runOnUiThread {
            if (!isRunning) {
                showSetupUI()
                if (lastWasRunning) {
                    val remaining = timerService?.phaseRemainingSeconds ?: 0
                    if (remaining <= 0) {
                        val isEmpty = workflowPhases.isEmpty()
                        val msg = if (isEmpty) {
                            "${timerService?.warningPeriodName ?: "Period"} complete. Well done!"
                        } else {
                            "All phases complete. Well done!"
                        }
                        binding.root.announceForAccessibility(msg)
                    } else {
                        binding.root.announceForAccessibility("Timer stopped")
                    }
                }
            } else {
                updateStateUI(isRunning, isPaused)
                if (!lastWasRunning) {
                    binding.root.announceForAccessibility("Timer started")
                } else if (!lastWasPaused && isPaused) {
                    binding.root.announceForAccessibility("Timer paused")
                } else if (lastWasPaused && !isPaused) {
                    binding.root.announceForAccessibility("Timer resumed")
                }
            }
            lastWasRunning = isRunning
            lastWasPaused = isPaused
        }
    }

    override fun onPeriodWarning(message: String) {
        runOnUiThread {
            binding.tvWarningStatus.text = message
            binding.tvWarningStatus.announceForAccessibility(message)
        }
    }

    // --- Helpers ---
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

    private fun getCurrentDayOfWeek(): String {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "Sunday"
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            java.util.Calendar.SATURDAY -> "Saturday"
            else -> "Monday"
        }
    }

    private fun formatTimeFromMs(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.US)
        return sdf.format(java.util.Date(ms))
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        if (isBound) {
            timerService?.workflowListener = null
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}
