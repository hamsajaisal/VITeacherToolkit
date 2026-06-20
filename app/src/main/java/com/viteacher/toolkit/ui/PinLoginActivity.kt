package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.databinding.ActivityPinLoginBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch

class PinLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinLoginBinding
    var targetScreen: String = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setupCursorEndForEditTexts()

        targetScreen = intent.getStringExtra("target") ?: "home"

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val profile = db.userProfileDao().getProfile()
            runOnUiThread {
                if (profile != null) {
                    binding.tvWelcome.text = "Hi ${profile.firstName}"
                    binding.tvSchoolName.text = profile.schoolName
                    checkBiometricAvailability()
                } else {
                    startActivity(Intent(this@PinLoginActivity, SetupProfileActivity::class.java))
                    finish()
                }
            }
        }

        binding.cbShowPin.setOnCheckedChangeListener { _, isChecked ->
            val inputType = if (isChecked) {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_NORMAL
            } else {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            binding.etPin.inputType = inputType
            binding.etPin.setSelection(binding.etPin.text.length)
        }

        binding.etPin.addTextChangedListener(object : android.text.TextWatcher {
            private var lastLength = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                lastLength = s?.length ?: 0
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val currentLength = s?.length ?: 0
                if (currentLength > lastLength) {
                    val isShowPinChecked = binding.cbShowPin.isChecked
                    if (isShowPinChecked) {
                        val addedChar = s?.lastOrNull() ?: ""
                        binding.etPin.announceForAccessibility("$addedChar")
                    } else {
                        binding.etPin.announceForAccessibility("bullet")
                    }
                } else if (currentLength < lastLength) {
                    binding.etPin.announceForAccessibility("deleted")
                }
                lastLength = currentLength
            }
        })

        binding.btnLogin.setOnClickListener {
            verifyPin()
        }
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                binding.btnFingerprint.visibility = android.view.View.VISIBLE
                binding.btnFingerprint.setOnClickListener {
                    showBiometricPrompt()
                }
            }
            else -> {
                binding.btnFingerprint.visibility = android.view.View.GONE
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val message = "Login successful"
                    Toast.makeText(this@PinLoginActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                    navigateToTarget()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val message = "Authentication error: $errString"
                    Toast.makeText(this@PinLoginActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    val message = "Fingerprint not recognized. Please try again."
                    Toast.makeText(this@PinLoginActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Login")
            .setSubtitle("Touch the fingerprint sensor to login")
            .setNegativeButtonText("Use PIN instead")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun verifyPin() {
        val enteredPin = binding.etPin.text.toString().trim()

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val profile = db.userProfileDao().getProfile()

            runOnUiThread {
                if (profile != null && enteredPin == profile.pin) {
                    val message = "Login successful"
                    Toast.makeText(this@PinLoginActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                    navigateToTarget()
                } else {
                    val message = "Incorrect PIN. Please try again."
                    binding.etPin.error = message
                    binding.etPin.text?.clear()
                    binding.etPin.requestFocus()
                    Toast.makeText(this@PinLoginActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility("Incorrect PIN. PIN cleared. Please try again.")
                }
            }
        }
    }

    private fun navigateToTarget() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val setupDone = prefs.getBoolean("setup_guide_shown", false)

        val intent = when {
            targetScreen == "password_saver" ->
                Intent(this@PinLoginActivity, PasswordSaverActivity::class.java)
            targetScreen == "edit_profile" ->
                Intent(this@PinLoginActivity, SetupProfileActivity::class.java).apply {
                    putExtra("EXTRA_EDIT_MODE", true)
                }
            !setupDone ->
                Intent(this@PinLoginActivity, SetupGuideActivity::class.java)
            else ->
                Intent(this@PinLoginActivity, HomeActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}