package com.viteacher.toolkit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.R
import com.viteacher.toolkit.databinding.ActivityStopwatchBinding
import com.viteacher.toolkit.util.ClassroomTimerService

class StopwatchActivity : AppCompatActivity(), ClassroomTimerService.StopwatchListener {

    private lateinit var binding: ActivityStopwatchBinding
    private var timerService: ClassroomTimerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ClassroomTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true

            // Restore state if running
            timerService!!.stopwatchListener = this@StopwatchActivity
            
            // Sync time display
            val elapsed = timerService!!.stopwatchElapsedSeconds
            val formatted = timerService!!.formatTimeMMSS(elapsed)
            binding.tvStopwatchDisplay.text = formatted
            binding.tvStopwatchDisplay.contentDescription = "$formatted elapsed"

            // Sync buttons & laps
            updateButtonsState(timerService!!.isStopwatchRunning, elapsed > 0)
            renderLaps(timerService!!.stopwatchLaps)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopwatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind Service
        val intent = Intent(this, ClassroomTimerService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.contentDescription = "Go back"
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnStartStop.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (service.isStopwatchRunning) {
                service.stopStopwatch()
            } else {
                service.startStopwatch()
            }
        }

        binding.btnLap.setOnClickListener {
            timerService?.recordLap()
        }

        binding.btnReset.setOnClickListener {
            confirmReset()
        }

        // Make time display readable on tap
        binding.tvStopwatchDisplay.setOnClickListener {
            timerService?.let { service ->
                val elapsedText = service.formatDurationForTTS(service.stopwatchElapsedSeconds)
                binding.tvStopwatchDisplay.announceForAccessibility("$elapsedText elapsed")
            }
        }

        // Initial accessibility configuration
        updateButtonsState(isRunning = false, hasTime = false)
    }

    private fun confirmReset() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Reset Stopwatch")
            .setMessage("Are you sure you want to reset the stopwatch?")
            .setPositiveButton("Yes") { _, _ ->
                timerService?.resetStopwatch()
                binding.root.announceForAccessibility("Stopwatch reset")
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes, reset stopwatch"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No, cancel"
        binding.root.announceForAccessibility("Reset stopwatch dialog. Are you sure you want to reset? Choose Yes or No.")
    }

    private fun updateButtonsState(isRunning: Boolean, hasTime: Boolean) {
        if (isRunning) {
            binding.btnStartStop.text = "Stop"
            binding.btnStartStop.contentDescription = "Stop stopwatch"
            binding.btnLap.isEnabled = true
            binding.btnLap.contentDescription = "Record lap"
            binding.btnReset.isEnabled = false
            binding.btnReset.contentDescription = "Reset stopwatch"
        } else {
            binding.btnStartStop.text = "Start"
            binding.btnStartStop.contentDescription = "Start stopwatch"
            binding.btnLap.isEnabled = false
            binding.btnLap.contentDescription = "Record lap"
            binding.btnReset.isEnabled = hasTime
            binding.btnReset.contentDescription = "Reset stopwatch"
        }
    }

    private fun renderLaps(laps: List<String>) {
        binding.layoutLapsList.removeAllViews()
        laps.forEach { lapText ->
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8.dpToPx())
                }
                text = lapText
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                setBackgroundColor(android.graphics.Color.parseColor("#FF1E1E1E"))
                isFocusable = true
                
                // Read formatted lap text like "Lap 1, 45 seconds"
                val parts = lapText.split("—")
                val lapNum = parts.firstOrNull()?.trim() ?: "Lap"
                val lapTimeStr = parts.lastOrNull()?.trim() ?: "00:00"
                contentDescription = "$lapNum, $lapTimeStr"
            }
            binding.layoutLapsList.addView(tv)
        }
    }

    // --- StopwatchListener Callbacks ---
    override fun onTick(elapsedSec: Int) {
        runOnUiThread {
            val formatted = timerService?.formatTimeMMSS(elapsedSec) ?: "00:00"
            binding.tvStopwatchDisplay.text = formatted
            binding.tvStopwatchDisplay.contentDescription = "$formatted elapsed"
            updateButtonsState(timerService?.isStopwatchRunning ?: false, elapsedSec > 0)
        }
    }

    override fun onLapAdded(laps: List<String>) {
        runOnUiThread {
            renderLaps(laps)
            timerService?.let { service ->
                val elapsed = service.stopwatchElapsedSeconds
                val lapNum = laps.size
                val elapsedText = service.formatDurationForTTS(elapsed)
                binding.root.announceForAccessibility("Lap $lapNum recorded at $elapsedText")
            }
        }
    }

    override fun onStateChanged(isRunning: Boolean) {
        runOnUiThread {
            val elapsed = timerService?.stopwatchElapsedSeconds ?: 0
            updateButtonsState(isRunning, elapsed > 0)
            timerService?.let { service ->
                if (isRunning) {
                    binding.root.announceForAccessibility("Stopwatch started")
                } else {
                    val elapsedText = service.formatDurationForTTS(elapsed)
                    binding.root.announceForAccessibility("Stopwatch stopped at $elapsedText")
                }
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        if (isBound) {
            timerService?.stopwatchListener = null
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}
