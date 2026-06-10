package com.viteacher.toolkit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivityClassroomTimerBinding
import com.viteacher.toolkit.util.AlertMode
import com.viteacher.toolkit.util.ClassroomTimerService
import com.viteacher.toolkit.util.setAccessibleSelection

class ClassroomTimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassroomTimerBinding
    private var timerService: ClassroomTimerService? = null
    private var isBound = false

    private val alertOptions = listOf(
        "Audio only",
        "Beep only",
        "Vibrate only",
        "Audio and Vibrate",
        "Beep and Vibrate"
    )

    private val alertModes = listOf(
        AlertMode.AUDIO_ONLY,
        AlertMode.BEEP_ONLY,
        AlertMode.VIBRATE_ONLY,
        AlertMode.AUDIO_AND_VIBRATE,
        AlertMode.BEEP_AND_VIBRATE
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ClassroomTimerService.TimerBinder
            timerService = binder.getService()
            isBound = true
            syncAlertModeToService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start service so it stays running
        val intent = Intent(this, ClassroomTimerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.contentDescription = "Go back"
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Configure Dropdown Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, alertOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAlertMode.adapter = adapter

        // Load Alert Mode Setting from Shared Preferences
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val savedModeStr = prefs.getString("classroom_timer_alert_mode", AlertMode.AUDIO_ONLY.name)
        val savedMode = AlertMode.fromString(savedModeStr ?: "")
        val index = alertModes.indexOf(savedMode)
        if (index >= 0) {
            binding.spinnerAlertMode.setSelection(index)
        }

        binding.spinnerAlertMode.setAccessibleSelection("Alert mode selector") { position ->
            val selectedMode = alertModes[position]
            prefs.edit().putString("classroom_timer_alert_mode", selectedMode.name).apply()
            syncAlertModeToService()
        }

        // Setup tool buttons and labels
        binding.btnPeriodWorkflow.contentDescription = "Period Workflow Timer. Divide your class period into phases with automatic announcements."
        binding.btnPeriodWorkflow.setOnClickListener {
            startActivity(Intent(this, PeriodWorkflowTimerActivity::class.java))
        }

        binding.btnActivityTimer.contentDescription = "Activity Timer. Set a countdown for a specific classroom task."
        binding.btnActivityTimer.setOnClickListener {
            startActivity(Intent(this, ActivityTimerActivity::class.java))
        }

        binding.btnStopwatch.contentDescription = "Stopwatch. Measure how long students take to complete a task."
        binding.btnStopwatch.setOnClickListener {
            startActivity(Intent(this, StopwatchActivity::class.java))
        }
    }

    private fun syncAlertModeToService() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val savedModeStr = prefs.getString("classroom_timer_alert_mode", AlertMode.AUDIO_ONLY.name)
        val mode = AlertMode.fromString(savedModeStr ?: "")
        timerService?.alertMode = mode
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}
