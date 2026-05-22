package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.TimetableEntry
import com.viteacher.toolkit.databinding.ActivityTimetableBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimetableBinding
    private lateinit var adapter: TimetableAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TimetableAdapter(emptyList(),
            onEdit = { entry -> editEntry(entry) },
            onDelete = { entry -> confirmDeleteEntry(entry) }
        )

        binding.rvTimetable.layoutManager = LinearLayoutManager(this)
        binding.rvTimetable.adapter = adapter

        loadAllEntries()

        binding.btnAddEntry.setOnClickListener {
            startActivity(Intent(this, AddTimetableEntryActivity::class.java))
        }

        binding.btnViewByDay.setOnClickListener {
            showDayFilterDialog()
        }

        binding.btnViewByClass.setOnClickListener {
            showClassFilterDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadAllEntries()
    }

    private fun loadAllEntries() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .getAllEntries()
                .collectLatest { entries ->
                    adapter.updateList(entries)
                    if (entries.isEmpty()) {
                        binding.root.announceForAccessibility(
                            "No timetable entries yet. Press Add New Timetable Entry to add one."
                        )
                    }
                }
        }
    }

    private fun editEntry(entry: TimetableEntry) {
        val intent = Intent(this, EditTimetableEntryActivity::class.java)
        intent.putExtra("entry_id", entry.id)
        startActivity(intent)
    }

    private fun showDayFilterDialog() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        AlertDialog.Builder(this)
            .setTitle("Select Day")
            .setItems(days) { _, which ->
                filterByDay(days[which])
            }
            .create()
            .show()
    }

    private fun filterByDay(day: String) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .getEntriesForDay(day)
                .collectLatest { entries ->
                    adapter.updateList(entries)
                    val message = if (entries.isEmpty())
                        "No entries for $day"
                    else
                        "Showing ${entries.size} entries for $day"
                    binding.root.announceForAccessibility(message)
                }
        }
    }

    private fun showClassFilterDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.timetableDao().getAllEntriesOnce().let { entries ->
                val classes = entries.map { "${it.className} ${it.division}" }
                    .distinct().sorted()
                runOnUiThread {
                    if (classes.isEmpty()) {
                        Toast.makeText(this@TimetableActivity, "No entries yet", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    AlertDialog.Builder(this@TimetableActivity)
                        .setTitle("Select Class")
                        .setItems(classes.toTypedArray()) { _, which ->
                            filterByClass(classes[which])
                        }
                        .create()
                        .show()
                }
            }
        }
    }

    private fun filterByClass(classAndDivision: String) {
        val parts = classAndDivision.split(" ")
        val className = parts[0]
        val division = if (parts.size > 1) parts[1] else ""
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .getAllEntries()
                .collectLatest { entries ->
                    val filtered = entries.filter {
                        it.className == className && it.division == division
                    }
                    adapter.updateList(filtered)
                    val message = if (filtered.isEmpty())
                        "No entries for class $classAndDivision"
                    else
                        "Showing ${filtered.size} entries for class $classAndDivision"
                    binding.root.announceForAccessibility(message)
                }
        }
    }

    private fun confirmDeleteEntry(entry: TimetableEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete ${entry.subject} on ${entry.dayOfWeek} period ${entry.periodNumber}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteEntry(entry)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Delete cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility(
            "Confirm delete. Are you sure you want to delete ${entry.subject} on ${entry.dayOfWeek}? Choose Delete or Cancel."
        )
    }

    private fun deleteEntry(entry: TimetableEntry) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .deleteEntry(entry)
            runOnUiThread {
                val message = "${entry.subject} deleted successfully"
                Toast.makeText(this@TimetableActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
        }
    }

    inner class TimetableAdapter(
        private var entries: List<TimetableEntry>,
        private val onEdit: (TimetableEntry) -> Unit,
        private val onDelete: (TimetableEntry) -> Unit
    ) : RecyclerView.Adapter<TimetableAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvSubject: TextView = itemView.findViewById(R.id.tvEntrySubject)
            val tvClass: TextView = itemView.findViewById(R.id.tvEntryClass)
            val tvTime: TextView = itemView.findViewById(R.id.tvEntryTime)
            val btnEdit: Button = itemView.findViewById(R.id.btnEditEntry)
            val btnDelete: Button = itemView.findViewById(R.id.btnDeleteEntry)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_timetable_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.tvSubject.text = entry.subject
            holder.tvClass.text = "Class ${entry.className} ${entry.division}"
            holder.tvTime.text = "${entry.dayOfWeek}, Period ${entry.periodNumber}"
            holder.itemView.contentDescription =
                "${entry.subject}, Class ${entry.className} ${entry.division}, ${entry.dayOfWeek}, Period ${entry.periodNumber}"
            holder.btnEdit.setOnClickListener { onEdit(entry) }
            holder.btnDelete.setOnClickListener { onDelete(entry) }
        }

        override fun getItemCount() = entries.size

        fun updateList(newEntries: List<TimetableEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }
    }
}