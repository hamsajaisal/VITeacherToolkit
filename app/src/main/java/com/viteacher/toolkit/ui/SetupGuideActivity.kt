package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivitySetupGuideBinding
import com.viteacher.toolkit.util.ReminderScheduler

class SetupGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupGuideBinding
    private var currentStep = 1
    private val totalSteps = 3

    data class WizardStep(
        val title: String,
        val description: String,
        val buttonLabel: String,
        val action: () -> Unit,
        val checkStatus: () -> String
    )

    private lateinit var steps: List<WizardStep>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSteps()
        showStep(currentStep)

        binding.btnNextStep.setOnClickListener {
            if (currentStep < totalSteps) {
                currentStep++
                showStep(currentStep)
            } else {
                completeSetup()
            }
        }

        binding.btnPreviousStep.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                showStep(currentStep)
            }
        }

        binding.btnDirectAction.setOnClickListener {
            steps[currentStep - 1].action()
        }

        binding.btnSkipWizard.setOnClickListener {
            completeSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupSteps() {
        steps = listOf(
            WizardStep(
                title = "Step 1 — Allow Exact Alarms",
                description = "This allows the app to fire reminders at the exact class time. Without this, reminders may be delayed or not fire at all. Press the button below to open the setting directly.",
                buttonLabel = "Open Alarms Permission",
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            ).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Please open Settings and search for Alarms permission",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        binding.tvPermissionStatus.text =
                            "This permission is not required on your Android version."
                        binding.tvPermissionStatus.announceForAccessibility(
                            "This permission is not required on your Android version."
                        )
                    }
                },
                checkStatus = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ReminderScheduler.canScheduleExactAlarms(this)) {
                            "Alarm permission granted"
                        } else {
                            "Alarm permission not yet granted"
                        }
                    } else {
                        "Not required on your Android version"
                    }
                }
            ),
            WizardStep(
                title = "Step 2 — Disable Battery Optimization",
                description = "Battery optimization can stop reminders from working when the screen is off. Press the button to open battery settings and select Do Not Optimize for VI Teacher Toolkit.",
                buttonLabel = "Open Battery Settings",
                action = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        ).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        binding.root.announceForAccessibility(
                            "Battery settings opened. Select Do Not Optimize."
                        )
                    } catch (e: Exception) {
                        try {
                            startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        } catch (ex: Exception) {
                            Toast.makeText(
                                this,
                                "Please open Settings, go to Battery, find VI Teacher Toolkit and select Do Not Optimize",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                checkStatus = {
                    val pm = getSystemService(android.os.PowerManager::class.java)
                    if (pm.isIgnoringBatteryOptimizations(packageName)) {
                        "Battery optimization disabled"
                    } else {
                        "Battery optimization still active"
                    }
                }
            ),
            WizardStep(
                title = "Step 3 — Allow Background Activity",
                description = "This step allows the app to run in the background so reminders work even when you are not using the app. Press the button to open the app settings directly.",
                buttonLabel = "Open App Settings",
                action = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    binding.root.announceForAccessibility(
                        "App settings opened. Look for Background activity and turn it on."
                    )
                },
                checkStatus = {
                    "Check that Background activity is turned on in app settings"
                }
            )
        )
    }

    private fun showStep(step: Int) {
        val wizardStep = steps[step - 1]

        binding.tvStepIndicator.text = "Step $step of $totalSteps"
        binding.tvStepTitle.text = wizardStep.title
        binding.tvStepDescription.text = wizardStep.description
        binding.btnDirectAction.text = wizardStep.buttonLabel
        binding.btnDirectAction.contentDescription = wizardStep.buttonLabel

        updatePermissionStatus()

        binding.btnPreviousStep.visibility =
            if (step > 1) View.VISIBLE else View.GONE

        binding.btnNextStep.text =
            if (step == totalSteps) "Finish Setup" else "Next"
        binding.btnNextStep.contentDescription =
            if (step == totalSteps) "Finish setup and go to app" else "Go to next step"

        binding.root.announceForAccessibility(
            "Step $step of $totalSteps. ${wizardStep.title}. ${wizardStep.description}"
        )
    }

    private fun updatePermissionStatus() {
        if (::steps.isInitialized && currentStep <= steps.size) {
            val status = steps[currentStep - 1].checkStatus()
            binding.tvPermissionStatus.text = status
        }
    }

    private fun completeSetup() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("setup_guide_shown", true).apply()
        binding.root.announceForAccessibility("Setup complete. Opening the app.")
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}