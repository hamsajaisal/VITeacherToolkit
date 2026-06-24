package com.viteacher.toolkit.ui

import android.content.Context
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.SchoolPeriod
import com.viteacher.toolkit.databinding.ActivitySchoolHoursBinding
import com.viteacher.toolkit.databinding.DialogAddPeriodBinding
import com.viteacher.toolkit.util.ReminderScheduler
import com.viteacher.toolkit.util.TimePickerHelper
import com.viteacher.toolkit.util.setAccessibleSelection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayout

class SchoolHoursActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySchoolHoursBinding
    private lateinit var adapter: PeriodAdapter
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private lateinit var periodNumbers: List<String>
    private var allPeriodsList: List<SchoolPeriod> = emptyList()

    private fun addMinutesToTime(timeStr: String, minutesToAdd: Int): String {
        return try {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
            val date = sdf.parse(timeStr)
            val calendar = java.util.Calendar.getInstance()
            if (date != null) {
                calendar.time = date
                calendar.add(java.util.Calendar.MINUTE, minutesToAdd)
                sdf.format(calendar.time)
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchoolHoursBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        periodNumbers = if (isCollege) {
            (1..8).map { "Hour $it" } + listOf("Forenoon Interval", "Lunch Break", "Afternoon Interval")
        } else {
            (1..8).map { "Period $it" } + listOf("Forenoon Interval", "Lunch Break", "Afternoon Interval")
        }

        if (isCollege) {
            binding.tvTitle.text = "College Hour Schedule"
            binding.btnAddPeriod.text = "Add New Hour"
            binding.btnAddPeriod.contentDescription = "Add a new college hour"
            binding.rvPeriods.contentDescription = "College hours list"
        }

        adapter = PeriodAdapter(
            emptyList(),
            onLongClick = { period ->
                showPeriodOptions(period)
            },
            onDelete = { period ->
                confirmDeletePeriod(period)
            }
        )

        binding.rvPeriods.layoutManager = LinearLayoutManager(this)
        binding.rvPeriods.adapter = adapter

        loadPeriods()

        binding.btnAddPeriod.setOnClickListener {
            showAddPeriodDialog()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updatePeriodsFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadPeriods() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .getAllPeriods()
                .collectLatest { periods ->
                    allPeriodsList = periods
                    updatePeriodsFilter()
                }
        }
    }

    private fun updatePeriodsFilter() {
        val hasExceptions = allPeriodsList.any { it.isException }
        if (hasExceptions) {
            binding.tabLayout.visibility = View.VISIBLE
            if (binding.tabLayout.tabCount == 0) {
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Whole Days"))
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Exceptional Days"))
            }
            val selectedTab = binding.tabLayout.selectedTabPosition
            val filtered = if (selectedTab == 1) {
                allPeriodsList.filter { it.isException }
            } else {
                allPeriodsList.filter { !it.isException }
            }
            adapter.updateList(filtered)
        } else {
            binding.tabLayout.visibility = View.GONE
            binding.tabLayout.removeAllTabs()
            adapter.updateList(allPeriodsList)
        }
    }

    private fun showAddPeriodDialog(editingPeriod: SchoolPeriod? = null) {
        val dialog = Dialog(this)
        val dialogBinding = DialogAddPeriodBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"

        if (isCollege) {
            dialogBinding.tvDialogTitle.text = if (editingPeriod != null) "Edit College Hour" else "Add College Hour"
            dialogBinding.tvPeriodNumberLabel.text = "Hour Number"
            dialogBinding.spinnerPeriodNumber.contentDescription = "Hour number, combo box"
            dialogBinding.cbIsException.contentDescription = "Check this if this hour timing applies to a specific day only, such as Friday"
            dialogBinding.btnSavePeriod.text = if (editingPeriod != null) "Save Changes" else "Save Hour"
            dialogBinding.btnSavePeriod.contentDescription = if (editingPeriod != null) "Save changes to this college hour" else "Save this college hour"
        } else {
            if (editingPeriod != null) {
                dialogBinding.tvDialogTitle.text = "Edit School Period"
                dialogBinding.btnSavePeriod.text = "Save Changes"
                dialogBinding.btnSavePeriod.contentDescription = "Save changes to this school period"
            }
        }

        val lastRegularPeriod = allPeriodsList.filter { !it.isException }.maxByOrNull { it.periodNumber }
        val defaultStartTimeCalculated = lastRegularPeriod?.endTime ?: "09:00 AM"
        val defaultEndTimeCalculated = lastRegularPeriod?.let { addMinutesToTime(it.endTime, if (isCollege) 60 else 45) } ?: "10:00 AM"

        var selectedStartTime = editingPeriod?.startTime ?: defaultStartTimeCalculated
        var selectedEndTime = editingPeriod?.endTime ?: defaultEndTimeCalculated

        dialogBinding.btnSelectStartTime.text = "Select Start Time: $selectedStartTime"
        dialogBinding.btnSelectStartTime.contentDescription = "Select start time, currently $selectedStartTime"
        dialogBinding.btnSelectEndTime.text = "Select End Time: $selectedEndTime"
        dialogBinding.btnSelectEndTime.contentDescription = "Select end time, currently $selectedEndTime"

        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periodNumbers)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPeriodNumber.adapter = periodAdapter

        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerExceptionDay.adapter = dayAdapter
        dialogBinding.spinnerExceptionDay.setAccessibleSelection("Exception day")

        // Setup alert timing options
        val alertTimeOptions = listOf("At break end", "2 minutes before end", "5 minutes before end", "10 minutes before end", "15 minutes before end")
        val alertMinutesValues = listOf(0, 2, 5, 10, 15)
        val alertTimeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, alertTimeOptions)
        alertTimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerAlertMinutes.adapter = alertTimeAdapter
        dialogBinding.spinnerAlertMinutes.setAccessibleSelection("Alert time selection")

        dialogBinding.spinnerPeriodNumber.setAccessibleSelection(if (isCollege) "Hour number" else "Period number") { position ->
            val periodNumber = when (position) {
                8 -> 99
                9 -> 100
                10 -> 101
                else -> position + 1
            }
            
            val isBreak = periodNumber in listOf(99, 100, 101)
            if (isBreak) {
                dialogBinding.layoutBreakAlert.visibility = View.VISIBLE
                val isAlertEnabled = prefs.getBoolean("refreshment_alert_enabled_$periodNumber", false)
                dialogBinding.cbEnableAlert.isChecked = isAlertEnabled
                dialogBinding.layoutAlertMinutes.visibility = if (isAlertEnabled) View.VISIBLE else View.GONE
                
                val savedMinutes = prefs.getInt("refreshment_alert_minutes_$periodNumber", 5)
                val valueIndex = alertMinutesValues.indexOf(savedMinutes).coerceAtLeast(0)
                dialogBinding.spinnerAlertMinutes.setSelection(valueIndex)
            } else {
                dialogBinding.layoutBreakAlert.visibility = View.GONE
            }
        }

        dialogBinding.cbEnableAlert.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                dialogBinding.layoutAlertMinutes.visibility = View.VISIBLE
                dialogBinding.layoutAlertMinutes.announceForAccessibility("Alert timing options visible")
            } else {
                dialogBinding.layoutAlertMinutes.visibility = View.GONE
            }
        }

        dialogBinding.btnSelectStartTime.setOnClickListener {
            TimePickerHelper.show(
                context = this,
                title = "Select Start Time",
                initialTime = selectedStartTime
            ) { time ->
                selectedStartTime = time
                dialogBinding.btnSelectStartTime.text = "Select Start Time: $time"
                dialogBinding.btnSelectStartTime.contentDescription =
                    "Select start time, currently $time"
                dialogBinding.btnSelectStartTime.announceForAccessibility("Start time set to $time")

                // Auto-calculate end time based on the selected period number and institution type
                val periodPosition = dialogBinding.spinnerPeriodNumber.selectedItemPosition
                val minutes = when (periodPosition) {
                    8 -> 10  // Forenoon Interval
                    9 -> 50  // Lunch Break
                    10 -> 5  // Afternoon Interval
                    else -> if (isCollege) 60 else 45 // College Hour (60m) or School Period (45m)
                }
                val calculatedEndTime = addMinutesToTime(time, minutes)
                selectedEndTime = calculatedEndTime
                dialogBinding.btnSelectEndTime.text = "Select End Time: $calculatedEndTime"
                dialogBinding.btnSelectEndTime.contentDescription =
                    "Select end time, currently $calculatedEndTime"
                dialogBinding.btnSelectEndTime.announceForAccessibility("End time automatically set to $calculatedEndTime")
            }
        }

        dialogBinding.btnSelectEndTime.setOnClickListener {
            TimePickerHelper.show(
                context = this,
                title = "Select End Time",
                initialTime = selectedEndTime
            ) { time ->
                selectedEndTime = time
                dialogBinding.btnSelectEndTime.text = "Select End Time: $time"
                dialogBinding.btnSelectEndTime.contentDescription =
                    "Select end time, currently $time"
                dialogBinding.btnSelectEndTime.announceForAccessibility("End time set to $time")
            }
        }

        dialogBinding.cbIsException.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                dialogBinding.layoutExceptionDay.visibility = View.VISIBLE
                dialogBinding.layoutExceptionDay.announceForAccessibility(
                    "Exception day selector is now visible. Please select the day."
                )
            } else {
                dialogBinding.layoutExceptionDay.visibility = View.GONE
            }
        }

        // Set initial spinner/exception values if editing
        if (editingPeriod != null) {
            val spinnerPosition = when (editingPeriod.periodNumber) {
                99 -> 8
                100 -> 9
                101 -> 10
                else -> editingPeriod.periodNumber - 1
            }
            dialogBinding.spinnerPeriodNumber.setSelection(spinnerPosition)

            dialogBinding.cbIsException.isChecked = editingPeriod.isException
            if (editingPeriod.isException) {
                val dayIndex = days.indexOf(editingPeriod.exceptionDay).coerceAtLeast(0)
                dialogBinding.spinnerExceptionDay.setSelection(dayIndex)
            }
        }

        dialogBinding.btnSavePeriod.setOnClickListener {
            val periodPosition = dialogBinding.spinnerPeriodNumber.selectedItemPosition
            val periodNumber = when (periodPosition) {
                8 -> 99
                9 -> 100
                10 -> 101
                else -> periodPosition + 1
            }
            val isException = dialogBinding.cbIsException.isChecked
            val exceptionDay = if (isException)
                dialogBinding.spinnerExceptionDay.selectedItem.toString()
            else ""

            val isBreak = periodNumber in listOf(99, 100, 101)
            if (isBreak) {
                val isAlertEnabled = dialogBinding.cbEnableAlert.isChecked
                val selectedAlertIndex = dialogBinding.spinnerAlertMinutes.selectedItemPosition
                val alertMin = alertMinutesValues.getOrElse(selectedAlertIndex) { 5 }
                
                prefs.edit().apply {
                    putBoolean("refreshment_alert_enabled_$periodNumber", isAlertEnabled)
                    putInt("refreshment_alert_minutes_$periodNumber", alertMin)
                    apply()
                }
            }

            val period = SchoolPeriod(
                id = editingPeriod?.id ?: 0,
                periodNumber = periodNumber,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                isException = isException,
                exceptionDay = exceptionDay
            )

            lifecycleScope.launch {
                if (editingPeriod != null && editingPeriod.periodNumber in listOf(99, 100, 101)) {
                    ReminderScheduler.cancelBreakReminder(applicationContext, editingPeriod.periodNumber)
                }

                AppDatabase.getDatabase(applicationContext)
                    .timetableDao()
                    .insertPeriod(period)
                
                if (isBreak) {
                    ReminderScheduler.scheduleBreakReminder(applicationContext, period)
                }

                runOnUiThread {
                    val labelName = when (periodNumber) {
                        99 -> "Forenoon Interval"
                        100 -> "Lunch Break"
                        101 -> "Afternoon Interval"
                        else -> if (isCollege) "Hour $periodNumber" else "Period $periodNumber"
                    }
                    val message = if (editingPeriod != null)
                        "$labelName updated successfully"
                    else if (isException)
                        "$labelName saved for $exceptionDay only, $selectedStartTime to $selectedEndTime"
                    else
                        "$labelName saved for all days, $selectedStartTime to $selectedEndTime"
                    Toast.makeText(this@SchoolHoursActivity, message, Toast.LENGTH_SHORT).show()
                    dialogBinding.root.announceForAccessibility(message)
                    dialog.dismiss()
                }
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPeriodOptions(period: SchoolPeriod) {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        val label = if (isCollege) "Hour" else "Period"

        val labelName = when (period.periodNumber) {
            99 -> "Forenoon Interval"
            100 -> "Lunch Break"
            101 -> "Afternoon Interval"
            else -> "$label ${period.periodNumber}"
        }

        val options = arrayOf("Edit Details", "Delete $labelName")
        AlertDialog.Builder(this)
            .setTitle("$labelName Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddPeriodDialog(period)
                    1 -> confirmDeletePeriod(period)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePeriod(period: SchoolPeriod) {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        val label = if (isCollege) "Hour" else "Period"

        val labelName = when (period.periodNumber) {
            99 -> "Forenoon Interval"
            100 -> "Lunch Break"
            101 -> "Afternoon Interval"
            else -> "$label ${period.periodNumber}"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete $labelName")
            .setMessage("Are you sure you want to delete $labelName? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getDatabase(applicationContext)
                        .timetableDao()
                        .deletePeriod(period)
                    
                    if (period.periodNumber in listOf(99, 100, 101)) {
                        ReminderScheduler.cancelBreakReminder(applicationContext, period.periodNumber)
                    }

                    runOnUiThread {
                        val message = "$labelName deleted successfully"
                        Toast.makeText(this@SchoolHoursActivity, message, Toast.LENGTH_SHORT).show()
                        binding.root.announceForAccessibility(message)
                    }
                }
            }
            .setNegativeButton("Cancel") { alertDialog, _ ->
                alertDialog.dismiss()
                binding.root.announceForAccessibility("Delete cancelled")
            }
            .create()
        dialog.show()
        binding.root.announceForAccessibility(
            "Confirm delete. Are you sure you want to delete $labelName? Choose Delete or Cancel."
        )
    }

    inner class PeriodAdapter(
        private var periods: List<SchoolPeriod>,
        private val onLongClick: (SchoolPeriod) -> Unit,
        private val onDelete: (SchoolPeriod) -> Unit
    ) : RecyclerView.Adapter<PeriodAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPeriodTitle: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodTitle)
            val tvPeriodTime: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodTime)
            val tvPeriodException: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodException)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(com.viteacher.toolkit.R.layout.item_period, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val period = periods[position]
            val prefs = holder.itemView.context.getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
            val isCollege = prefs.getString("institution_type", "school") == "college"
            val label = if (isCollege) "Hour" else "Period"

            val labelName = when (period.periodNumber) {
                99 -> "Forenoon Interval"
                100 -> "Lunch Break"
                101 -> "Afternoon Interval"
                else -> "$label ${period.periodNumber}"
            }

            holder.tvPeriodTitle.text = labelName
            val timeText = "${period.startTime} to ${period.endTime}"
            holder.tvPeriodTime.text = timeText

            if (period.isException) {
                holder.tvPeriodException.visibility = View.VISIBLE
                holder.tvPeriodException.text = "Exception: ${period.exceptionDay} only"
            } else {
                holder.tvPeriodException.visibility = View.GONE
            }

            val contentDesc = if (period.isException)
                "$labelName, ${period.startTime} to ${period.endTime}, exception for ${period.exceptionDay} only. Double tap and hold for options."
            else
                "$labelName, ${period.startTime} to ${period.endTime}, applies to all days. Double tap and hold for options."

            holder.itemView.contentDescription = contentDesc

            holder.itemView.setOnLongClickListener {
                onLongClick(period)
                true
            }
        }

        override fun getItemCount() = periods.size

        fun updateList(newPeriods: List<SchoolPeriod>) {
            periods = newPeriods
            notifyDataSetChanged()
        }
    }
}