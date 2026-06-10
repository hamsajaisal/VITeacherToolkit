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

class SchoolHoursActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySchoolHoursBinding
    private lateinit var adapter: PeriodAdapter
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private lateinit var periodNumbers: List<String>

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

        adapter = PeriodAdapter(emptyList()) { period ->
            confirmDeletePeriod(period)
        }

        binding.rvPeriods.layoutManager = LinearLayoutManager(this)
        binding.rvPeriods.adapter = adapter

        loadPeriods()

        binding.btnAddPeriod.setOnClickListener {
            showAddPeriodDialog()
        }
    }

    private fun loadPeriods() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .timetableDao()
                .getAllPeriods()
                .collectLatest { periods ->
                    adapter.updateList(periods)
                }
        }
    }

    private fun showAddPeriodDialog() {
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
            dialogBinding.tvDialogTitle.text = "Add College Hour"
            dialogBinding.tvPeriodNumberLabel.text = "Hour Number"
            dialogBinding.spinnerPeriodNumber.contentDescription = "Hour number, combo box"
            dialogBinding.cbIsException.contentDescription = "Check this if this hour timing applies to a specific day only, such as Friday"
            dialogBinding.btnSavePeriod.text = "Save Hour"
            dialogBinding.btnSavePeriod.contentDescription = "Save this college hour"
        }

        var selectedStartTime = "09:00 AM"
        var selectedEndTime = "10:00 AM"

        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periodNumbers)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPeriodNumber.adapter = periodAdapter
        dialogBinding.spinnerPeriodNumber.setAccessibleSelection(if (isCollege) "Hour number" else "Period number")

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

        dialogBinding.spinnerPeriodNumber.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
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

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
                periodNumber = periodNumber,
                startTime = selectedStartTime,
                endTime = selectedEndTime,
                isException = isException,
                exceptionDay = exceptionDay
            )

            lifecycleScope.launch {
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
                    val message = if (isException)
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
        private val onDelete: (SchoolPeriod) -> Unit
    ) : RecyclerView.Adapter<PeriodAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPeriodTitle: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodTitle)
            val tvPeriodTime: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodTime)
            val tvPeriodException: TextView = itemView.findViewById(com.viteacher.toolkit.R.id.tvPeriodException)
            val btnDelete: Button = itemView.findViewById(com.viteacher.toolkit.R.id.btnDeletePeriod)
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
                "$labelName, ${period.startTime} to ${period.endTime}, exception for ${period.exceptionDay} only"
            else
                "$labelName, ${period.startTime} to ${period.endTime}, applies to all days"

            holder.itemView.contentDescription = contentDesc
            holder.btnDelete.setOnClickListener { onDelete(period) }
        }

        override fun getItemCount() = periods.size

        fun updateList(newPeriods: List<SchoolPeriod>) {
            periods = newPeriods
            notifyDataSetChanged()
        }
    }
}