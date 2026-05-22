package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.UserProfile
import com.viteacher.toolkit.databinding.ActivitySetupProfileBinding
import kotlinx.coroutines.launch

class SetupProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun saveProfile() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val schoolName = binding.etSchoolName.text.toString().trim()
        val pin = binding.etPin.text.toString().trim()
        val confirmPin = binding.etConfirmPin.text.toString().trim()

        if (firstName.isEmpty()) {
            binding.etFirstName.error = "Please enter your first name"
            binding.etFirstName.requestFocus()
            return
        }
        if (lastName.isEmpty()) {
            binding.etLastName.error = "Please enter your last name"
            binding.etLastName.requestFocus()
            return
        }
        if (schoolName.isEmpty()) {
            binding.etSchoolName.error = "Please enter your school name"
            binding.etSchoolName.requestFocus()
            return
        }
        if (pin.length != 4) {
            binding.etPin.error = "PIN must be exactly 4 digits"
            binding.etPin.requestFocus()
            return
        }
        if (pin != confirmPin) {
            binding.etConfirmPin.error = "PINs do not match"
            binding.etConfirmPin.requestFocus()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.userProfileDao().saveProfile(
                UserProfile(
                    firstName = firstName,
                    lastName = lastName,
                    schoolName = schoolName,
                    pin = pin
                )
            )
            runOnUiThread {
                Toast.makeText(
                    this@SetupProfileActivity,
                    "Profile saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@SetupProfileActivity, PinLoginActivity::class.java))
                finish()
            }
        }
    }
}