package com.viteacher.toolkit.ui

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.SchoolPeriod
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.databinding.ActivityEditTimetableEntryBinding
import com.viteacher.toolkit.util.ReminderScheduler
import com.viteacher.toolkit.util.setAccessibleSelection
import kotlinx.coroutines.launch

class EditTimetableEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTimetableEntryBinding
    private var loadedPeriods: List<SchoolPeriod> = emptyList()
    private var displayedPeriods: List<SchoolPeriod> = emptyList()
    private var entryId: Int = 0
    private var restoredEntryId = -1
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
        binding = ActivityEditTimetableEntryBinding.inflate(layoutInflater)
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

        entryId = intent.getIntExtra("entry_id", 0)

        setupDaySpinner()
        setupReminderSpinner()
        loadPeriodsAndEntry()

        binding.btnUpdateEntry.setOnClickListener {
            updateEntry()
        }

        binding.btnCancelEdit.setOnClickListener {
            finish()
        }
    }

    private fun setupDaySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDay.adapter = adapter
        binding.spinnerDay.setAccessibleSelection("Day")
    }

    private fun setupReminderSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reminderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerReminder.adapter = adapter
        binding.spinnerReminder.setAccessibleSelection("Remind me before class")
    }

    private fun loadPeriodsAndEntry() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val periods = db.timetableDao().getAllPeriodsOnce()
            val allEntries = db.timetableDao().getAllEntriesOnce()
            val entry = allEntries.find { it.id == entryId }
            loadedPeriods = periods

            runOnUiThread {
                binding.spinnerDay.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        val selectedDay = days[position]
                        updatePeriodSpinnerForDay(selectedDay)
                        if (entry != null && restoredEntryId != entry.id) {
                            val periodIndex = displayedPeriods.indexOfFirst {
                                it.periodNumber == entry.periodNumber
                            }
                            if (periodIndex >= 0) {
                                binding.spinnerPeriod.setSelection(periodIndex)
                                restoredEntryId = entry.id
                            }
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

                if (entry != null) {
                    val dayIndex = days.indexOf(entry.dayOfWeek)
                    if (dayIndex >= 0) {
                        binding.spinnerDay.setSelection(dayIndex)
                    } else {
                        updatePeriodSpinnerForDay(days[0])
                    }

                    binding.etSubject.setText(entry.subject)
                    binding.etClass.setText(entry.className)
                    binding.etDivision.setText(entry.division)

                    val reminderIndex = reminderMinutes.indexOf(entry.reminderMinutesBefore)
                    if (reminderIndex >= 0) binding.spinnerReminder.setSelection(reminderIndex)
                } else {
                    updatePeriodSpinnerForDay(days[0])
                }
            }
        }
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
            displayedPeriods.map { "Period ${it.periodNumber} (${it.startTime} - ${it.endTime})" }
        }

        val periodAdapter = ArrayAdapter(
            this@EditTimetableEntryActivity,
            android.R.layout.simple_spinner_item,
            periodLabels
        )
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriod.adapter = periodAdapter
        binding.spinnerPeriod.setAccessibleSelection("Period")
    }

    private fun updateEntry() {
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
            val updatedEntry = TimetableEntry(
                id = entryId,
                dayOfWeek = day,
                periodNumber = selectedPeriod.periodNumber,
                subject = subject,
                className = className,
                division = division,
                reminderMinutesBefore = if (reminderMinutesBefore == -1) 0
                else reminderMinutesBefore
            )
            db.timetableDao().updateEntry(updatedEntry)

            ReminderScheduler.cancelReminder(applicationContext, entryId)
            if (reminderMinutesBefore >= 0) {
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val language = prefs.getString("reminder_language", "en") ?: "en"
                ReminderScheduler.scheduleReminder(
                    applicationContext,
                    updatedEntry,
                    selectedPeriod,
                    language
                )
            }

            runOnUiThread {
                val message = if (isCollege) "Timetable entry updated for program $className year $division successfully" else "Timetable entry updated successfully"
                Toast.makeText(this@EditTimetableEntryActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }
}