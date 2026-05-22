package com.viteacher.toolkit.util

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.databinding.DialogTimePickerBinding

object TimePickerHelper {

    fun show(
        context: Context,
        title: String,
        initialTime: String = "09:00 AM",
        onTimeSelected: (String) -> Unit
    ) {
        val dialog = Dialog(context)
        val binding = DialogTimePickerBinding.inflate(
            android.view.LayoutInflater.from(context)
        )
        dialog.setContentView(binding.root)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        binding.tvTimePickerTitle.text = title

        var currentHour = 9
        var currentMinute = 0
        var isAm = true

        fun updateDisplay() {
            val hourStr = currentHour.toString().padStart(2, '0')
            val minuteStr = currentMinute.toString().padStart(2, '0')
            val amPm = if (isAm) "AM" else "PM"
            binding.etHour.setText(hourStr)
            binding.etMinute.setText(minuteStr)
            val timeText = "Selected time: $hourStr:$minuteStr $amPm"
            binding.tvSelectedTime.text = timeText
            binding.tvSelectedTime.announceForAccessibility(timeText)
            binding.btnAm.alpha = if (isAm) 1.0f else 0.5f
            binding.btnPm.alpha = if (isAm) 0.5f else 1.0f
        }

        binding.btnHourUp.setOnClickListener {
            currentHour = if (currentHour >= 12) 1 else currentHour + 1
            updateDisplay()
            binding.etHour.announceForAccessibility("Hour ${currentHour}")
        }

        binding.btnHourDown.setOnClickListener {
            currentHour = if (currentHour <= 1) 12 else currentHour - 1
            updateDisplay()
            binding.etHour.announceForAccessibility("Hour ${currentHour}")
        }

        binding.btnMinuteUp.setOnClickListener {
            currentMinute = if (currentMinute >= 55) 0 else currentMinute + 5
            updateDisplay()
            binding.etMinute.announceForAccessibility("Minute ${currentMinute}")
        }

        binding.btnMinuteDown.setOnClickListener {
            currentMinute = if (currentMinute <= 0) 55 else currentMinute - 5
            updateDisplay()
            binding.etMinute.announceForAccessibility("Minute ${currentMinute}")
        }

        binding.btnAm.setOnClickListener {
            isAm = true
            updateDisplay()
            binding.btnAm.announceForAccessibility("AM selected")
        }

        binding.btnPm.setOnClickListener {
            isAm = false
            updateDisplay()
            binding.btnPm.announceForAccessibility("PM selected")
        }

        binding.etHour.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val typed = binding.etHour.text.toString().toIntOrNull()
                if (typed != null && typed in 1..12) {
                    currentHour = typed
                    updateDisplay()
                }
            }
        }

        binding.etMinute.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val typed = binding.etMinute.text.toString().toIntOrNull()
                if (typed != null && typed in 0..59) {
                    currentMinute = typed
                    updateDisplay()
                }
            }
        }

        binding.btnTimeOk.setOnClickListener {
            val hourStr = currentHour.toString().padStart(2, '0')
            val minuteStr = currentMinute.toString().padStart(2, '0')
            val amPm = if (isAm) "AM" else "PM"
            val result = "$hourStr:$minuteStr $amPm"
            onTimeSelected(result)
            dialog.dismiss()
        }

        binding.btnTimeCancel.setOnClickListener {
            dialog.dismiss()
        }

        updateDisplay()
        dialog.show()
    }
}