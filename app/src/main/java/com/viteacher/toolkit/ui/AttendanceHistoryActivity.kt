package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.databinding.ActivityAttendanceHistoryBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceHistoryBinding
    private lateinit var historyAdapter: AttendanceHistoryAdapter
    private val historyList = mutableListOf<HistoryGroupItem>()
    private var classId: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadHistoryData()
    }

    private fun setupRecyclerView() {
        historyAdapter = AttendanceHistoryAdapter(historyList) { item ->
            val intent = Intent(this, HistoryDetailActivity::class.java).apply {
                putExtra("selected_date", item.date)
                putExtra("class_id", classId)
            }
            startActivity(intent)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun loadHistoryData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.attendanceDao().getAllSavedDatesAndSessionsFlow(classId).collect { dtos ->
                val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                
                // Group by date string
                val grouped = dtos.groupBy { it.date }
                
                // Map to HistoryGroupItem with parsed LocalDate for sorting
                val items = grouped.map { (dateStr, sessionDtos) ->
                    val localDate = try {
                        LocalDate.parse(dateStr, formatter)
                    } catch (e: Exception) {
                        LocalDate.MIN
                    }
                    val sessionsList = sessionDtos.map { it.session }.sorted() // e.g. ["Forenoon", "Afternoon"]
                    HistoryGroupItem(dateStr, localDate, sessionsList)
                }

                // Sort descending by date (newest first)
                val sortedItems = items.sortedWith(compareByDescending<HistoryGroupItem> { it.localDate }
                    .thenBy { it.date })

                runOnUiThread {
                    historyList.clear()
                    historyList.addAll(sortedItems)
                    
                    if (historyList.isEmpty()) {
                        binding.rvHistory.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.rvHistory.visibility = View.VISIBLE
                        binding.tvEmptyState.visibility = View.GONE
                        historyAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }
}

// Data class to represent a group of attendance records for a single date
data class HistoryGroupItem(
    val date: String,
    val localDate: LocalDate,
    val sessions: List<String>
)

// Custom Adapter for History RecyclerView
class AttendanceHistoryAdapter(
    private val list: List<HistoryGroupItem>,
    private val onItemClick: (HistoryGroupItem) -> Unit
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val tvSessions: TextView = view.findViewById(R.id.tvHistorySessions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        
        holder.tvDate.text = item.date
        
        val sessionText = when {
            item.sessions.contains("Forenoon") && item.sessions.contains("Afternoon") -> "Forenoon and Afternoon"
            item.sessions.contains("Forenoon") -> "Forenoon only"
            item.sessions.contains("Afternoon") -> "Afternoon only"
            else -> item.sessions.joinToString(" and ")
        }
        holder.tvSessions.text = sessionText

        val contentDesc = "${item.date}, $sessionText sessions available. Double tap to view details."
        holder.view.contentDescription = contentDesc

        holder.view.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = list.size
}
