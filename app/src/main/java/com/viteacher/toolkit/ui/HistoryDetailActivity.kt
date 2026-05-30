package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.AttendanceRecord
import com.viteacher.toolkit.databinding.ActivityHistoryDetailBinding
import kotlinx.coroutines.launch

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private var selectedDate: String = ""
    private var classId: Int = 1
    
    private val sessionList = mutableListOf<SessionGroupData>()
    private lateinit var sessionAdapter: HistoryDetailCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedDate = intent.getStringExtra("selected_date") ?: ""
        classId = intent.getIntExtra("class_id", 1)

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

        setupRecyclerView()
        loadDetailData()
    }

    private fun setupRecyclerView() {
        sessionAdapter = HistoryDetailCardAdapter(selectedDate, sessionList) { records, sessionName ->
            shareAbsentees(records, sessionName)
        }
        binding.rvSessionCards.layoutManager = LinearLayoutManager(this)
        binding.rvSessionCards.adapter = sessionAdapter
    }

    private fun loadDetailData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val allRecords = db.attendanceDao().getAttendanceForDateAndClass(classId, selectedDate)
            
            runOnUiThread {
                if (allRecords.isEmpty()) {
                    Toast.makeText(this@HistoryDetailActivity, "No records found for this date.", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }

                // Group by session name (e.g. "Forenoon", "Afternoon", "Daily", "Hour 1", etc.)
                val grouped = allRecords.groupBy { it.session }
                
                sessionList.clear()
                // Sort keys so sessions are displayed in a clean order (e.g., Forenoon first, Hour 1 before Hour 2)
                val sortedKeys = grouped.keys.sortedWith(compareBy { it })
                
                sortedKeys.forEach { sessionKey ->
                    val records = grouped[sessionKey] ?: emptyList()
                    sessionList.add(SessionGroupData(sessionName = sessionKey, records = records))
                }

                sessionAdapter.notifyDataSetChanged()

                // Accessible vocal announcement summary for TalkBack
                val announcement = StringBuilder("Loaded history details for $selectedDate. ")
                sessionList.forEach { group ->
                    val absent = group.records.count { !it.isPresent }
                    announcement.append("${group.sessionName} has $absent absent. ")
                }
                binding.root.announceForAccessibility(announcement.toString())
            }
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
            message.append("Session/Period: $sessionName\n\n")
            
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

// Helper data class
data class SessionGroupData(
    val sessionName: String,
    val records: List<AttendanceRecord>
)

// Dynamic Card Adapter
class HistoryDetailCardAdapter(
    private val selectedDate: String,
    private val list: List<SessionGroupData>,
    private val onShareClick: (List<AttendanceRecord>, String) -> Unit
) : RecyclerView.Adapter<HistoryDetailCardAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvSessionName: TextView = view.findViewById(R.id.tvSessionName)
        val tvStats: TextView = view.findViewById(R.id.tvStats)
        val tvAbsentees: TextView = view.findViewById(R.id.tvAbsentees)
        val btnShare: Button = view.findViewById(R.id.btnShare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_history_detail_card, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        
        holder.tvSessionName.text = "${item.sessionName} Session"
        
        val total = item.records.size
        val absentees = item.records.filter { !it.isPresent }
        val totalAbsent = absentees.size
        val totalPresent = total - totalAbsent

        // Stats Text
        val statsText = "Present: $totalPresent  |  Absent: $totalAbsent"
        holder.tvStats.text = statsText
        holder.tvStats.contentDescription = "${item.sessionName} session status, $totalPresent present and $totalAbsent absent out of $total students."

        // Absentees List
        val absenteesTextBuilder = StringBuilder()
        if (totalAbsent == 0) {
            absenteesTextBuilder.append("No absentees. Full attendance present!")
            holder.tvAbsentees.text = absenteesTextBuilder.toString()
            holder.tvAbsentees.contentDescription = "No absentees. Full attendance present."
        } else {
            absentees.forEachIndexed { index, record ->
                absenteesTextBuilder.append("Roll No. ${record.rollNumber} — ${record.name}")
                if (index < absentees.size - 1) {
                    absenteesTextBuilder.append("\n")
                }
            }
            holder.tvAbsentees.text = absenteesTextBuilder.toString()
            
            val spokenAbsentees = absentees.joinToString(", ") { "Roll number ${it.rollNumber}, ${it.name}" }
            holder.tvAbsentees.contentDescription = "Absentees list: $spokenAbsentees"
        }

        // Share button context and click
        holder.btnShare.text = "Share ${item.sessionName} Absentees"
        holder.btnShare.contentDescription = "Share ${item.sessionName} absentees report button"
        holder.btnShare.setOnClickListener {
            onShareClick(item.records, item.sessionName)
        }
    }

    override fun getItemCount(): Int = list.size
}
