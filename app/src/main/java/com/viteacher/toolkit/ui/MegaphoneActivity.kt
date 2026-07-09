package com.viteacher.toolkit.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.R
import com.viteacher.toolkit.databinding.ActivityMegaphoneBinding
import com.viteacher.toolkit.util.MegaphoneService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MegaphoneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMegaphoneBinding
    private var megaphoneService: MegaphoneService? = null
    private var isBound = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMegaphoneService()
        } else {
            Toast.makeText(this, "Microphone permission is required to stream your voice.", Toast.LENGTH_LONG).show()
            binding.root.announceForAccessibility("Microphone permission denied. Cannot start megaphone.")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MegaphoneService.MegaphoneBinder
            megaphoneService = binder.getService()
            isBound = true
            setupBoundServiceObservers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            megaphoneService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMegaphoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStaticViews()
        setupClickListeners()
        checkHardwareCapabilities()

        // Bind to service if already running
        lifecycleScope.launch {
            MegaphoneService.isRunningFlow.collectLatest { running ->
                updateUiState(running)
                if (running && !isBound) {
                    bindMegaphoneService()
                } else if (!running && isBound) {
                    unbindMegaphoneService()
                }
            }
        }
    }

    private fun setupStaticViews() {
        binding.sbVoiceBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gain = progress / 10.0f
                binding.tvVoiceBoostLabel.text = String.format("Digital Voice Boost: %.1fx", gain)
                binding.tvVoiceBoostLabel.contentDescription = String.format("Digital Voice Boost is %.1f times", gain)
                megaphoneService?.setGain(gain)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Set initial slider position (progress = 10, which corresponds to 1.0x gain)
        binding.sbVoiceBoost.progress = 10
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnToggleMegaphone.setOnClickListener {
            val isRunning = MegaphoneService.isRunningFlow.value
            if (isRunning) {
                stopMegaphoneService()
            } else {
                checkPermissionAndStart()
            }
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startMegaphoneService()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showPermissionExplanationDialog()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Microphone Permission Required")
            .setMessage("VITeacher Toolkit needs access to your microphone to stream your voice directly to the connected Bluetooth speaker.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun startMegaphoneService() {
        val intent = Intent(this, MegaphoneService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindMegaphoneService()
    }

    private fun stopMegaphoneService() {
        val intent = Intent(this, MegaphoneService::class.java)
        stopService(intent)
        unbindMegaphoneService()
    }

    private fun bindMegaphoneService() {
        val intent = Intent(this, MegaphoneService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindMegaphoneService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            megaphoneService = null
        }
    }

    private fun setupBoundServiceObservers() {
        val service = megaphoneService ?: return
        
        // Sync seekbar gain
        val currentProgress = binding.sbVoiceBoost.progress
        service.setGain(currentProgress / 10.0f)

        // Observe amplitude flow for visualizer
        lifecycleScope.launch {
            service.amplitudeFlow.collectLatest { amplitude ->
                binding.pbVoiceLevel.progress = amplitude
                binding.pbVoiceLevel.contentDescription = "Voice input activity level is $amplitude percent"
            }
        }

        // Observe mute state
        lifecycleScope.launch {
            service.isMutedFlow.collectLatest { muted ->
                if (muted) {
                    binding.btnToggleMegaphone.text = "TURN OFF (MUTED)"
                    binding.btnToggleMegaphone.contentDescription = "Megaphone is muted. Double tap to unmute/turn on sound, or long press to stop megaphone completely."
                } else {
                    binding.btnToggleMegaphone.text = "TURN OFF (ACTIVE)"
                    binding.btnToggleMegaphone.contentDescription = "Megaphone is active. Double tap to mute voice, or long press to stop megaphone completely."
                }
            }
        }
    }

    private fun updateUiState(running: Boolean) {
        if (running) {
            binding.btnToggleMegaphone.text = "TURN OFF"
            binding.btnToggleMegaphone.contentDescription = "Megaphone is streaming. Double tap to turn off."
            binding.btnToggleMegaphone.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            binding.btnToggleMegaphone.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.sbVoiceBoost.isEnabled = true
            binding.pbVoiceLevel.visibility = View.VISIBLE
            binding.tvVoiceLevelLabel.visibility = View.VISIBLE
        } else {
            binding.btnToggleMegaphone.text = "TURN ON"
            binding.btnToggleMegaphone.contentDescription = "Megaphone is off. Double tap to turn on."
            binding.btnToggleMegaphone.backgroundTintList = ContextCompat.getColorStateList(this, R.color.lavender)
            binding.btnToggleMegaphone.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            binding.sbVoiceBoost.isEnabled = false
            binding.pbVoiceLevel.visibility = View.GONE
            binding.tvVoiceLevelLabel.visibility = View.GONE
            binding.pbVoiceLevel.progress = 0
        }
        checkHardwareCapabilities()
    }

    private fun checkHardwareCapabilities() {
        val aecAvailable = AcousticEchoCanceler.isAvailable()
        val nsAvailable = NoiseSuppressor.isAvailable()

        binding.tvEchoCancelerStatus.text = if (aecAvailable) {
            "Acoustic Echo Canceler: Hardware Enabled (Mitigating Howling)"
        } else {
            "Acoustic Echo Canceler: Software Only / Unavailable (Maintain Speaker Distance)"
        }

        binding.tvNoiseSuppressorStatus.text = if (nsAvailable) {
            "Noise Suppressor: Enabled"
        } else {
            "Noise Suppressor: Unavailable"
        }
    }

    override fun onDestroy() {
        unbindMegaphoneService()
        super.onDestroy()
    }
}
