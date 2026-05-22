package com.viteacher.toolkit.ui

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
import com.viteacher.toolkit.util.TimePickerHelper
import com.viteacher.toolkit.util.setAccessibleSelection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SchoolHoursActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySchoolHoursBinding
    private lateinit var adapter: PeriodAdapter
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val periodNumbers = (1..8).map { "Period $it" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchoolHoursBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        var selectedStartTime = "09:00 AM"
        var selectedEndTime = "10:00 AM"

        val periodAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, periodNumbers)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerPeriodNumber.adapter = periodAdapter
        dialogBinding.spinnerPeriodNumber.setAccessibleSelection("Period number")

        val dayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerExceptionDay.adapter = dayAdapter
        dialogBinding.spinnerExceptionDay.setAccessibleSelection("Exception day")

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
            val periodNumber = periodPosition + 1
            val isException = dialogBinding.cbIsException.isChecked
            val exceptionDay = if (isException)
                dialogBinding.spinnerExceptionDay.selectedItem.toString()
            else ""

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
                runOnUiThread {
                    val message = if (isException)
                        "Period $periodNumber saved for $exceptionDay only, $selectedStartTime to $selectedEndTime"
                    else
                        "Period $periodNumber saved for all days, $selectedStartTime to $selectedEndTime"
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Period")
            .setMessage("Are you sure you want to delete Period ${period.periodNumber}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getDatabase(applicationContext)
                        .timetableDao()
                        .deletePeriod(period)
                    runOnUiThread {
                        val message = "Period ${period.periodNumber} deleted successfully"
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
            "Confirm delete. Are you sure you want to delete Period ${period.periodNumber}? Choose Delete or Cancel."
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
            holder.tvPeriodTitle.text = "Period ${period.periodNumber}"
            val timeText = "${period.startTime} to ${period.endTime}"
            holder.tvPeriodTime.text = timeText

            if (period.isException) {
                holder.tvPeriodException.visibility = View.VISIBLE
                holder.tvPeriodException.text = "Exception: ${period.exceptionDay} only"
            } else {
                holder.tvPeriodException.visibility = View.GONE
            }

            val contentDesc = if (period.isException)
                "Period ${period.periodNumber}, ${period.startTime} to ${period.endTime}, exception for ${period.exceptionDay} only"
            else
                "Period ${period.periodNumber}, ${period.startTime} to ${period.endTime}, applies to all days"

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