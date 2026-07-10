package com.viteacher.toolkit.ui

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        binding.btnSilenceAnnouncements.setOnClickListener {
            showSilenceAnnouncementsDialog()
        }

        binding.btnShareTimetable.setOnClickListener {
            showShareOptionsDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDynamicLabels()
        loadAllEntries()
    }

    private fun updateDynamicLabels() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        if (isCollege) {
            binding.btnViewByClass.text = "View by Program"
            binding.btnViewByClass.contentDescription = "View by Program"
        } else {
            binding.btnViewByClass.text = "View by Class"
            binding.btnViewByClass.contentDescription = "View by Class"
        }
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
            val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val isCollege = prefs.getString("institution_type", "school") == "college"
            db.timetableDao().getAllEntriesOnce().let { entries ->
                val classes = entries.map { "${it.className} ${it.division}" }
                    .distinct().sorted()
                runOnUiThread {
                    if (classes.isEmpty()) {
                        Toast.makeText(this@TimetableActivity, "No entries yet", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val title = if (isCollege) "Select Program" else "Select Class"
                    AlertDialog.Builder(this@TimetableActivity)
                        .setTitle(title)
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
                    val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                    val isCollege = prefs.getString("institution_type", "school") == "college"
                    val message = if (filtered.isEmpty()) {
                        if (isCollege) "No entries for program $classAndDivision" else "No entries for class $classAndDivision"
                    } else {
                        if (isCollege) "Showing ${filtered.size} entries for program $classAndDivision" else "Showing ${filtered.size} entries for class $classAndDivision"
                    }
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

    private fun formatSilencedDates(silentDates: Set<String>): String {
        if (silentDates.isEmpty()) return "None"
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dates = silentDates.mapNotNull {
            try { sdf.parse(it) } catch (e: Exception) { null }
        }.sorted()
        if (dates.isEmpty()) return "None"

        val ranges = mutableListOf<String>()
        val cal = Calendar.getInstance()
        
        var rangeStart = dates[0]
        var prevDate = dates[0]
        
        val displaySdf = SimpleDateFormat("MMM d", Locale.US)
        
        for (i in 1 until dates.size) {
            val currentDate = dates[i]
            cal.time = prevDate
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val expectedNext = cal.time
            
            val isConsecutive = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currentDate) ==
                               SimpleDateFormat("yyyy-MM-dd", Locale.US).format(expectedNext)
            
            if (!isConsecutive) {
                if (rangeStart == prevDate) {
                    ranges.add(displaySdf.format(rangeStart))
                } else {
                    ranges.add("${displaySdf.format(rangeStart)} to ${displaySdf.format(prevDate)}")
                }
                rangeStart = currentDate
            }
            prevDate = currentDate
        }
        
        if (rangeStart == prevDate) {
            ranges.add(displaySdf.format(rangeStart))
        } else {
            ranges.add("${displaySdf.format(rangeStart)} to ${displaySdf.format(prevDate)}")
        }
        
        return ranges.joinToString(", ")
    }

    private fun showSilenceAnnouncementsDialog() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val silentDates = prefs.getStringSet("silent_dates", emptySet())?.toMutableSet() ?: mutableSetOf()

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        val options = arrayOf(
            "Silence Today (${if (silentDates.contains(todayStr)) "Status: Silenced" else "Status: Reminders are active"})",
            "Silence Tomorrow (${if (silentDates.contains(tomorrowStr)) "Status: Silenced" else "Status: Reminders are active"})",
            "Silence Specific Date",
            "Silence Date Range",
            "Clear All Silenced Dates"
        )

        val silencedSummary = formatSilencedDates(silentDates)
        val dialogTitle = "Silence Timetable Announcements\n(Silenced: $silencedSummary)"

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val activeSet = prefs.getStringSet("silent_dates", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val announceMsg: String
                        if (activeSet.contains(todayStr)) {
                            activeSet.remove(todayStr)
                            announceMsg = "Announcements for today are now active"
                        } else {
                            activeSet.add(todayStr)
                            announceMsg = "Announcements for today are now silenced"
                        }
                        prefs.edit().putStringSet("silent_dates", activeSet).apply()
                        Toast.makeText(this, announceMsg, Toast.LENGTH_SHORT).show()
                        binding.root.announceForAccessibility(announceMsg)
                        showSilenceAnnouncementsDialog()
                    }
                    1 -> {
                        val activeSet = prefs.getStringSet("silent_dates", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val announceMsg: String
                        if (activeSet.contains(tomorrowStr)) {
                            activeSet.remove(tomorrowStr)
                            announceMsg = "Announcements for tomorrow are now active"
                        } else {
                            activeSet.add(tomorrowStr)
                            announceMsg = "Announcements for tomorrow are now silenced"
                        }
                        prefs.edit().putStringSet("silent_dates", activeSet).apply()
                        Toast.makeText(this, announceMsg, Toast.LENGTH_SHORT).show()
                        binding.root.announceForAccessibility(announceMsg)
                        showSilenceAnnouncementsDialog()
                    }
                    2 -> {
                        showSilenceDatePicker()
                    }
                    3 -> {
                        showSilenceDateRangePicker()
                    }
                    4 -> {
                        prefs.edit().putStringSet("silent_dates", emptySet()).apply()
                        val msg = "All silenced dates cleared. Announcements are active for all days."
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        binding.root.announceForAccessibility(msg)
                        showSilenceAnnouncementsDialog()
                    }
                }
            }
            .setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
        
        binding.root.announceForAccessibility("Silence Timetable Announcements dialog opened. Current silenced dates: $silencedSummary. Select Silence Today, Silence Tomorrow, Silence Specific Date, Silence Date Range, or Clear All Silenced Dates.")
    }

    private fun showSilenceDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCalendar.time)
                
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val activeSet = prefs.getStringSet("silent_dates", emptySet())?.toMutableSet() ?: mutableSetOf()
                activeSet.add(selectedDateStr)
                prefs.edit().putStringSet("silent_dates", activeSet).apply()
                
                val formattedDateReadable = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(selectedCalendar.time)
                val msg = "Announcements silenced for $formattedDateReadable"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                binding.root.announceForAccessibility(msg)
                showSilenceAnnouncementsDialog()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
        binding.root.announceForAccessibility("DatePicker dialog opened. Select a year, month, and day to silence announcements, then select OK.")
    }

    private fun showSilenceDateRangePicker() {
        val calendar = Calendar.getInstance()
        val startPickerDialog = android.app.DatePickerDialog(
            this,
            { _, sYear, sMonth, sDay ->
                val startCal = Calendar.getInstance()
                startCal.set(sYear, sMonth, sDay)
                
                val endPickerDialog = android.app.DatePickerDialog(
                    this@TimetableActivity,
                    { _, eYear, eMonth, eDay ->
                        val endCal = Calendar.getInstance()
                        endCal.set(eYear, eMonth, eDay)
                        
                        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                        val activeSet = prefs.getStringSet("silent_dates", emptySet())?.toMutableSet() ?: mutableSetOf()
                        
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val tempCal = startCal.clone() as Calendar
                        var count = 0
                        while (!tempCal.after(endCal)) {
                            activeSet.add(sdf.format(tempCal.time))
                            tempCal.add(Calendar.DAY_OF_YEAR, 1)
                            count++
                        }
                        
                        prefs.edit().putStringSet("silent_dates", activeSet).apply()
                        
                        val startStr = SimpleDateFormat("d MMMM yyyy", Locale.US).format(startCal.time)
                        val endStr = SimpleDateFormat("d MMMM yyyy", Locale.US).format(endCal.time)
                        val msg = "Announcements silenced for $count days (from $startStr to $endStr)"
                        Toast.makeText(this@TimetableActivity, msg, Toast.LENGTH_LONG).show()
                        binding.root.announceForAccessibility(msg)
                        showSilenceAnnouncementsDialog()
                    },
                    sYear, sMonth, sDay
                )
                
                endPickerDialog.datePicker.minDate = startCal.timeInMillis
                endPickerDialog.setTitle("Select End Date")
                endPickerDialog.show()
                binding.root.announceForAccessibility("End Date Picker opened. Select the last day to silence announcements, then select OK.")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        startPickerDialog.setTitle("Select Start Date")
        startPickerDialog.show()
        binding.root.announceForAccessibility("Start Date Picker opened. Select the first day to silence announcements, then select OK.")
    }

    private fun showShareOptionsDialog() {
        val options = arrayOf("Today's Timetable", "Tomorrow's Timetable", "Select Date...")
        AlertDialog.Builder(this)
            .setTitle("Share Timetable")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val calendar = Calendar.getInstance()
                        generateAndShareTimetable(calendar)
                    }
                    1 -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        generateAndShareTimetable(calendar)
                    }
                    2 -> {
                        showShareDatePicker()
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
        binding.root.announceForAccessibility("Share Timetable dialog opened. Select Today's Timetable, Tomorrow's Timetable, or Select Date.")
    }

    private fun showShareDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                generateAndShareTimetable(selectedCalendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.setTitle("Select Date to Share")
        datePickerDialog.show()
        binding.root.announceForAccessibility("Date Picker opened. Select the date of the timetable you want to share, then select OK.")
    }

    private fun generateAndShareTimetable(date: Calendar) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val periods = db.timetableDao().getAllPeriodsOnce()
                val entries = db.timetableDao().getAllEntriesOnce()

                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val label = if (isCollege) "Hour" else "Period"

                val dayName = SimpleDateFormat("EEEE", Locale.US).format(date.time)
                val hasExceptions = periods.any { it.isException && it.exceptionDay == dayName }
                val activePeriods = if (hasExceptions) {
                    periods.filter { it.isException && it.exceptionDay == dayName }
                } else {
                    periods.filter { !it.isException }
                }

                val sortedPeriods = activePeriods.sortedBy { parseTimeToMinutes(it.startTime) }

                val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(date.time)
                val sb = java.lang.StringBuilder()
                sb.append("Timetable: ").append(dateStr).append("\n\n")

                if (sortedPeriods.isEmpty()) {
                    sb.append("No school periods set for this day.\n")
                } else {
                    sortedPeriods.forEach { p ->
                        val pNum = p.periodNumber
                        val isBreak = pNum in listOf(99, 100, 101)
                        val timeSpan = "(${p.startTime} - ${p.endTime})"

                        if (isBreak) {
                            val breakName = when (pNum) {
                                99 -> "Forenoon Interval"
                                100 -> "Lunch Break"
                                101 -> "Afternoon Interval"
                                else -> "Break"
                            }
                            sb.append("- ").append(breakName).append(" ").append(timeSpan).append(": Break\n")
                        } else {
                            val entry = entries.find { it.dayOfWeek == dayName && it.periodNumber == pNum }
                            val ordinalLabel = "$pNum${getOrdinalSuffix(pNum)} $label"

                            if (entry != null) {
                                val classLabel = if (isCollege) "" else "Class "
                                sb.append("- ").append(ordinalLabel).append(" ").append(timeSpan).append(": ")
                                    .append(classLabel).append(entry.className).append(" ").append(entry.division)
                                    .append(" - ").append(entry.subject).append("\n")
                            } else {
                                sb.append("- ").append(ordinalLabel).append(" ").append(timeSpan).append(": Leisure Time\n")
                            }
                        }
                    }
                }

                val messageText = sb.toString()

                runOnUiThread {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Timetable")
                        putExtra(Intent.EXTRA_TEXT, messageText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Timetable via"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
            val date = sdf.parse(timeStr)
            if (date != null) {
                val calendar = java.util.Calendar.getInstance()
                calendar.time = date
                calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getOrdinalSuffix(number: Int): String {
        if (number in 11..13) return "th"
        return when (number % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_timetable_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.tvSubject.text = entry.subject
            val prefs = holder.itemView.context.getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val isCollege = prefs.getString("institution_type", "school") == "college"
            if (isCollege) {
                holder.tvClass.text = "${entry.className} ${entry.division}"
                holder.itemView.contentDescription =
                    "${entry.subject}, Program ${entry.className} ${entry.division}, ${entry.dayOfWeek}, Hour ${entry.periodNumber}. Double tap and hold for options."
                holder.tvTime.text = "${entry.dayOfWeek}, Hour ${entry.periodNumber}"
            } else {
                holder.tvClass.text = "Class ${entry.className} ${entry.division}"
                holder.itemView.contentDescription =
                    "${entry.subject}, Class ${entry.className} ${entry.division}, ${entry.dayOfWeek}, Period ${entry.periodNumber}. Double tap and hold for options."
                holder.tvTime.text = "${entry.dayOfWeek}, Period ${entry.periodNumber}"
            }
            holder.itemView.setOnLongClickListener {
                val options = arrayOf("Edit Details", "Delete")
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Options for ${entry.subject}")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> onEdit(entry)
                            1 -> onDelete(entry)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show()
                true
            }
        }

        override fun getItemCount() = entries.size

        fun updateList(newEntries: List<TimetableEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }
    }
}