package com.viteacher.toolkit.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.SchoolPeriod
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.databinding.ActivityAddTimetableEntryBinding
import com.viteacher.toolkit.util.ReminderScheduler
import com.viteacher.toolkit.util.setAccessibleSelection
import kotlinx.coroutines.launch

class AddTimetableEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTimetableEntryBinding
    private var loadedPeriods: List<SchoolPeriod> = emptyList()
    private var displayedPeriods: List<SchoolPeriod> = emptyList()
    private var isCollege = false

    private val days = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    private val reminderOptions = listOf(
        "No reminder",
        "At class time",
        "5 minutes before",
        "10 minutes before",
        "15 minutes before",
        "30 minutes before"
    )

    private val reminderMinutes = listOf(-1, 0, 5, 10, 15, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTimetableEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        isCollege = prefs.getString("institution_type", "school") == "college"
        if (isCollege) {
            binding.tvClassLabel.text = "Program"
            binding.etClass.hint = "Example: BSc CS"
            binding.etClass.contentDescription = "Program name edit box"

            binding.tvDivisionLabel.text = "Year"
            binding.etDivision.hint = "Example: 1st Year"
            binding.etDivision.contentDescription = "Year edit box"
        }

        setupDaySpinner()
        setupReminderSpinner()
        checkSchoolHoursAndLoad()
        checkExactAlarmPermission()

        binding.btnSaveEntry.setOnClickListener {
            saveEntry()
        }

        binding.btnCancelEntry.setOnClickListener {
            finish()
        }
    }

    private fun checkSchoolHoursAndLoad() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val periods = db.timetableDao().getAllPeriodsOnce()
            loadedPeriods = periods

            runOnUiThread {
                if (periods.isEmpty()) {
                    showNoSchoolHoursDialog()
                } else {
                    val currentDay = binding.spinnerDay.selectedItem?.toString() ?: "Monday"
                    updatePeriodSpinnerForDay(currentDay)
                }
            }
        }
    }

    private fun showNoSchoolHoursDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("School Hours Not Set")
            .setMessage(
                "You have not set up your school hour schedule yet. " +
                        "You need to create your school periods in Settings before " +
                        "adding timetable entries. Would you like to go to Settings now?"
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .create()
        dialog.show()
        binding.root.announceForAccessibility(
            "School hours not set. You need to create your school periods in Settings first. " +
                    "Choose Go to Settings or Cancel."
        )
    }

    private fun updatePeriodSpinnerForDay(selectedDay: String) {
        val filtered = if (loadedPeriods.any { it.isException && it.exceptionDay == selectedDay }) {
            loadedPeriods.filter { it.isException && it.exceptionDay == selectedDay && it.periodNumber !in listOf(99, 100, 101) }
        } else {
            loadedPeriods.filter { !it.isException && it.periodNumber !in listOf(99, 100, 101) }
        }
        displayedPeriods = filtered.sortedBy { it.periodNumber }

        val periodLabels = if (displayedPeriods.isEmpty()) {
            listOf("No periods set for $selectedDay")
        } else {
            displayedPeriods.map {
                "Period ${it.periodNumber} (${it.startTime} - ${it.endTime})"
            }
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            periodLabels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = adapter
        binding.spinnerPeriod.setAccessibleSelection("Period")
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage(
                        "To receive class reminders, please allow VI Teacher Toolkit " +
                                "to schedule exact alarms. Press OK to open settings."
                    )
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        ).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show()
                binding.root.announceForAccessibility(
                    "Permission required. Please allow exact alarms for reminders to work."
                )
            }
        }
    }

    private fun setupDaySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDay.adapter = adapter
        binding.spinnerDay.setAccessibleSelection("Day")

        binding.spinnerDay.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (loadedPeriods.isNotEmpty()) {
                    updatePeriodSpinnerForDay(days[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupReminderSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reminderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerReminder.adapter = adapter
        binding.spinnerReminder.setAccessibleSelection("Remind me before class")
    }

    private fun saveEntry() {
        val day = binding.spinnerDay.selectedItem.toString()
        val periodPosition = binding.spinnerPeriod.selectedItemPosition
        val subject = binding.etSubject.text.toString().trim()
        val className = binding.etClass.text.toString().trim()
        val division = binding.etDivision.text.toString().trim()
        val reminderPosition = binding.spinnerReminder.selectedItemPosition
        val reminderMinutesBefore = reminderMinutes[reminderPosition]

        if (displayedPeriods.isEmpty()) {
            Toast.makeText(this, "No periods set for $day. Please define school hours first.", Toast.LENGTH_LONG).show()
            return
        }

        if (subject.isEmpty()) {
            binding.etSubject.error = "Please enter a subject"
            binding.etSubject.requestFocus()
            return
        }
        if (className.isEmpty()) {
            binding.etClass.error = if (isCollege) "Please enter a program" else "Please enter a class"
            binding.etClass.requestFocus()
            return
        }
        if (division.isEmpty()) {
            binding.etDivision.error = if (isCollege) "Please enter a year" else "Please enter a division"
            binding.etDivision.requestFocus()
            return
        }

        val selectedPeriod = displayedPeriods[periodPosition]

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val entry = TimetableEntry(
                dayOfWeek = day,
                periodNumber = selectedPeriod.periodNumber,
                subject = subject,
                className = className,
                division = division,
                reminderMinutesBefore = if (reminderMinutesBefore == -1) 0
                else reminderMinutesBefore
            )
            db.timetableDao().insertEntry(entry)

            if (reminderMinutesBefore >= 0) {
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val language = prefs.getString("reminder_language", "en") ?: "en"
                ReminderScheduler.scheduleReminder(
                    applicationContext,
                    entry,
                    selectedPeriod,
                    language
                )
            }

            runOnUiThread {
                val message = if (isCollege) {
                    "Timetable entry saved. $subject for program $className year $division on $day period ${selectedPeriod.periodNumber}"
                } else {
                    "Timetable entry saved. $subject for class $className $division on $day period ${selectedPeriod.periodNumber}"
                }
                Toast.makeText(this@AddTimetableEntryActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }
}