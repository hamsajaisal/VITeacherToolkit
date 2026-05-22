package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.AttendanceRecord
import com.viteacher.toolkit.databinding.ActivityHistoryDetailBinding
import kotlinx.coroutines.launch

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedDate = intent.getStringExtra("selected_date") ?: ""
        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "Error: No date selected.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvSelectedDate.text = selectedDate
        binding.tvSelectedDate.contentDescription = "Details for $selectedDate"

        binding.btnBack.setOnClickListener {
            finish()
        }

        loadDetailData()
    }

    private fun loadDetailData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            val forenoonRecords = db.attendanceDao().getAttendanceForDateAndSession(selectedDate, "Forenoon")
            val afternoonRecords = db.attendanceDao().getAttendanceForDateAndSession(selectedDate, "Afternoon")
            
            runOnUiThread {
                if (forenoonRecords.isEmpty() && afternoonRecords.isEmpty()) {
                    Toast.makeText(this@HistoryDetailActivity, "No records found for this date.", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }

                setupSessionCard(
                    records = forenoonRecords,
                    sessionName = "Forenoon",
                    cardLayout = binding.layoutForenoonCard,
                    statsView = binding.tvForenoonStats,
                    absenteesView = binding.tvForenoonAbsentees,
                    shareButton = binding.btnShareForenoon
                )

                setupSessionCard(
                    records = afternoonRecords,
                    sessionName = "Afternoon",
                    cardLayout = binding.layoutAfternoonCard,
                    statsView = binding.tvAfternoonStats,
                    absenteesView = binding.tvAfternoonAbsentees,
                    shareButton = binding.btnShareAfternoon
                )
                
                // Accessible announcement of the details loaded
                val announcement = StringBuilder("Loaded history detail for $selectedDate. ")
                if (forenoonRecords.isNotEmpty()) {
                    val absent = forenoonRecords.count { !it.isPresent }
                    announcement.append("Forenoon has $absent absent. ")
                }
                if (afternoonRecords.isNotEmpty()) {
                    val absent = afternoonRecords.count { !it.isPresent }
                    announcement.append("Afternoon has $absent absent.")
                }
                binding.root.announceForAccessibility(announcement.toString())
            }
        }
    }

    private fun setupSessionCard(
        records: List<AttendanceRecord>,
        sessionName: String,
        cardLayout: View,
        statsView: android.widget.TextView,
        absenteesView: android.widget.TextView,
        shareButton: android.widget.Button
    ) {
        if (records.isEmpty()) {
            cardLayout.visibility = View.GONE
            return
        }

        cardLayout.visibility = View.VISIBLE

        val total = records.size
        val absentees = records.filter { !it.isPresent }
        val totalAbsent = absentees.size
        val totalPresent = total - totalAbsent

        // Set Stats Text
        val statsText = "Present: $totalPresent  |  Absent: $totalAbsent"
        statsView.text = statsText
        statsView.contentDescription = "$sessionName session status, $totalPresent present and $totalAbsent absent out of $total students."

        // Set Absentees List Text
        val absenteesTextBuilder = StringBuilder()
        if (totalAbsent == 0) {
            absenteesTextBuilder.append("No absentees. Full attendance present!")
            absenteesView.text = absenteesTextBuilder.toString()
            absenteesView.contentDescription = "No absentees. Full attendance present."
        } else {
            absentees.forEachIndexed { index, record ->
                absenteesTextBuilder.append("Roll No. ${record.rollNumber} — ${record.name}")
                if (index < absentees.size - 1) {
                    absenteesTextBuilder.append("\n")
                }
            }
            absenteesView.text = absenteesTextBuilder.toString()
            
            // Build a descriptive label for TalkBack to read absentees list clearly without pausing on newlines
            val spokenAbsentees = absentees.joinToString(", ") { "Roll number ${it.rollNumber}, ${it.name}" }
            absenteesView.contentDescription = "Absentees list: $spokenAbsentees"
        }

        // Set Share Button Action
        shareButton.setOnClickListener {
            shareAbsentees(records, sessionName)
        }
    }

    private fun shareAbsentees(records: List<AttendanceRecord>, sessionName: String) {
        val absentees = records.filter { !it.isPresent }
        val totalAbsent = absentees.size
        val totalPresent = records.size - totalAbsent

        val message = StringBuilder()
        if (totalAbsent == 0) {
            message.append("No absentees for $sessionName on $selectedDate. Full attendance present.")
        } else {
            message.append("Absentees Report\n")
            message.append("Date: $selectedDate\n")
            message.append("Session: $sessionName\n\n")
            
            absentees.forEach {
                message.append("Roll No. ${it.rollNumber} — ${it.name}\n")
            }
            
            message.append("\nTotal Absent: $totalAbsent\n")
            message.append("Total Present: $totalPresent")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.toString())
            type = "text/plain"
        }

        val chooser = Intent.createChooser(shareIntent, "Share $sessionName Absentees Report")
        startActivity(chooser)
        binding.root.announceForAccessibility("Opening share menu for $sessionName absentees report.")
    }
}
