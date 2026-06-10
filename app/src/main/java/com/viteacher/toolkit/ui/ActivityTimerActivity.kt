package com.viteacher.toolkit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.R
import com.viteacher.toolkit.databinding.ActivityActivityTimerBinding
import com.viteacher.toolkit.util.ClassroomTimerService
import java.util.Locale

class ActivityTimerActivity : AppCompatActivity(), ClassroomTimerService.ActivityListener {

    private lateinit var binding: ActivityActivityTimerBinding
    private var timerService: ClassroomTimerService? = null
    private var isBound = false
    private var lastWasRunning = false
    private var lastWasPaused = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ClassroomTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true

            // Restore state if running
            timerService!!.activityListener = this@ActivityTimerActivity
            if (timerService!!.isActivityRunning) {
                showRunningUI()
                updateStateUI(timerService!!.isActivityRunning, timerService!!.isActivityPaused)
                lastWasRunning = timerService!!.isActivityRunning
                lastWasPaused = timerService!!.isActivityPaused
            } else {
                showSetupUI()
                lastWasRunning = false
                lastWasPaused = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivityTimerBinding.inflate(layoutInflater)
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

        // Custom button roles to avoid "button button" TalkBack announcements
        binding.btnIncMinutes.contentDescription = "Increase minutes"
        binding.btnDecMinutes.contentDescription = "Decrease minutes"
        binding.btnIncSeconds.contentDescription = "Increase seconds"
        binding.btnDecSeconds.contentDescription = "Decrease seconds"

        binding.etMinutes.contentDescription = "Minutes input field"
        binding.etSeconds.contentDescription = "Seconds input field"

        // Value changes
        binding.btnIncMinutes.setOnClickListener {
            val current = binding.etMinutes.text.toString().toIntOrNull() ?: 0
            binding.etMinutes.setText((current + 1).toString())
        }

        binding.btnDecMinutes.setOnClickListener {
            val current = binding.etMinutes.text.toString().toIntOrNull() ?: 0
            if (current > 0) {
                binding.etMinutes.setText((current - 1).toString())
            }
        }

        binding.btnIncSeconds.setOnClickListener {
            val current = binding.etSeconds.text.toString().toIntOrNull() ?: 0
            val next = (current + 1) % 60
            binding.etSeconds.setText(String.format(Locale.US, "%02d", next))
        }

        binding.btnDecSeconds.setOnClickListener {
            val current = binding.etSeconds.text.toString().toIntOrNull() ?: 0
            val next = if (current > 0) current - 1 else 59
            binding.etSeconds.setText(String.format(Locale.US, "%02d", next))
        }

        // Start
        binding.btnStartTimer.contentDescription = "Start activity timer"
        binding.btnStartTimer.setOnClickListener {
            val mins = binding.etMinutes.text.toString().toIntOrNull() ?: 0
            val secs = binding.etSeconds.text.toString().toIntOrNull() ?: 0
            val totalSec = mins * 60 + secs

            if (totalSec <= 0) {
                Toast.makeText(this, "Please set a duration greater than zero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            timerService?.startActivityTimer(totalSec)
            showRunningUI()
        }

        // Running actions
        binding.btnPauseResume.setOnClickListener {
            val service = timerService ?: return@setOnClickListener
            if (service.isActivityPaused) {
                service.resumeActivityTimer()
            } else {
                service.pauseActivityTimer()
            }
        }

        binding.btnRestart.contentDescription = "Restart activity timer"
        binding.btnRestart.setOnClickListener {
            confirmRestart()
        }

        binding.btnStop.contentDescription = "Stop activity timer"
        binding.btnStop.setOnClickListener {
            timerService?.stopActivityTimer()
            showSetupUI()
        }

        // Make time display readable on tap
        binding.tvTimeDisplay.setOnClickListener {
            timerService?.let { service ->
                if (service.isActivityRunning) {
                    val remainingText = service.formatDurationForTTS(service.activityRemainingSeconds)
                    binding.tvTimeDisplay.announceForAccessibility("$remainingText remaining")
                }
            }
        }
    }

    private fun showSetupUI() {
        binding.layoutSetup.visibility = View.VISIBLE
        binding.layoutRunning.visibility = View.GONE
    }

    private fun showRunningUI() {
        binding.layoutSetup.visibility = View.GONE
        binding.layoutRunning.visibility = View.VISIBLE
    }

    private fun confirmRestart() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Restart Timer")
            .setMessage("Are you sure you want to restart the timer?")
            .setPositiveButton("Yes") { _, _ ->
                timerService?.restartActivityTimer()
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes, restart timer"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No, cancel"
        binding.root.announceForAccessibility("Restart timer dialog. Are you sure you want to restart? Choose Yes or No.")
    }

    private fun updateStateUI(isRunning: Boolean, isPaused: Boolean) {
        if (isPaused) {
            binding.btnPauseResume.text = "Resume"
            binding.btnPauseResume.contentDescription = "Resume"
        } else {
            binding.btnPauseResume.text = "Pause"
            binding.btnPauseResume.contentDescription = "Pause"
        }
    }

    // --- ActivityListener Callbacks ---
    override fun onTick(remainingSec: Int, totalSec: Int, progress: Float) {
        runOnUiThread {
            val formattedTime = timerService?.formatTimeMMSS(remainingSec) ?: "00:00"
            binding.tvTimeDisplay.text = formattedTime
            binding.tvTimeDisplay.contentDescription = "$formattedTime remaining"
            binding.pbActivity.progress = (progress * 100).toInt()
        }
    }

    override fun onStateChanged(isRunning: Boolean, isPaused: Boolean) {
        runOnUiThread {
            if (!isRunning) {
                showSetupUI()
                if (lastWasRunning) {
                    val remaining = timerService?.activityRemainingSeconds ?: 0
                    if (remaining <= 0) {
                        binding.root.announceForAccessibility("Time is up")
                    } else {
                        binding.root.announceForAccessibility("Timer stopped")
                    }
                }
            } else {
                updateStateUI(isRunning, isPaused)
                if (!lastWasRunning) {
                    binding.root.announceForAccessibility("Timer started")
                } else if (!lastWasPaused && isPaused) {
                    binding.root.announceForAccessibility("Timer paused")
                } else if (lastWasPaused && !isPaused) {
                    binding.root.announceForAccessibility("Timer resumed")
                }
            }
            lastWasRunning = isRunning
            lastWasPaused = isPaused
        }
    }

    override fun onDestroy() {
        if (isBound) {
            timerService?.activityListener = null
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}
