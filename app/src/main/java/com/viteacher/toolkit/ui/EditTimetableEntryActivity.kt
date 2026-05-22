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
    private var entryId: Int = 0

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

            val periodLabels = if (periods.isEmpty()) {
                listOf("No periods set.")
            } else {
                periods.map { "Period ${it.periodNumber} (${it.startTime} - ${it.endTime})" }
            }

            runOnUiThread {
                val periodAdapter = ArrayAdapter(
                    this@EditTimetableEntryActivity,
                    android.R.layout.simple_spinner_item,
                    periodLabels
                )
                periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerPeriod.adapter = periodAdapter
                binding.spinnerPeriod.setAccessibleSelection("Period")

                if (entry != null) {
                    val dayIndex = days.indexOf(entry.dayOfWeek)
                    if (dayIndex >= 0) binding.spinnerDay.setSelection(dayIndex)

                    val periodIndex = periods.indexOfFirst {
                        it.periodNumber == entry.periodNumber
                    }
                    if (periodIndex >= 0) binding.spinnerPeriod.setSelection(periodIndex)

                    binding.etSubject.setText(entry.subject)
                    binding.etClass.setText(entry.className)
                    binding.etDivision.setText(entry.division)

                    val reminderIndex = reminderMinutes.indexOf(entry.reminderMinutesBefore)
                    if (reminderIndex >= 0) binding.spinnerReminder.setSelection(reminderIndex)
                }
            }
        }
    }

    private fun updateEntry() {
        val day = binding.spinnerDay.selectedItem.toString()
        val periodPosition = binding.spinnerPeriod.selectedItemPosition
        val subject = binding.etSubject.text.toString().trim()
        val className = binding.etClass.text.toString().trim()
        val division = binding.etDivision.text.toString().trim()
        val reminderPosition = binding.spinnerReminder.selectedItemPosition
        val reminderMinutesBefore = reminderMinutes[reminderPosition]

        if (subject.isEmpty()) {
            binding.etSubject.error = "Please enter a subject"
            binding.etSubject.requestFocus()
            return
        }
        if (className.isEmpty()) {
            binding.etClass.error = "Please enter a class"
            binding.etClass.requestFocus()
            return
        }
        if (division.isEmpty()) {
            binding.etDivision.error = "Please enter a division"
            binding.etDivision.requestFocus()
            return
        }

        val selectedPeriod = loadedPeriods[periodPosition]

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
                val message = "Timetable entry updated successfully"
                Toast.makeText(this@EditTimetableEntryActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }
}