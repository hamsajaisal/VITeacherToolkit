package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.UserProfile
import com.viteacher.toolkit.databinding.ActivitySetupProfileBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch

class SetupProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupProfileBinding
    private var isEditMode = false
    private var existingProfileId = 0

    private var initialFirstName = ""
    private var initialLastName = ""
    private var initialSchoolName = ""
    private var initialPin = ""
    private var initialInstitutionTypePosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setupCursorEndForEditTexts()

        val institutionTypes = listOf("School", "College / University")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, institutionTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInstitutionType.adapter = adapter

        binding.spinnerInstitutionType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 1) {
                    binding.etSchoolName.hint = "College/University Name"
                    binding.etSchoolName.contentDescription = "College or University Name, required"
                } else {
                    binding.etSchoolName.hint = "School Name"
                    binding.etSchoolName.contentDescription = "School Name, required"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val savedType = prefs.getString("institution_type", "school")
        val savedTypePos = if (savedType == "college") 1 else 0
        binding.spinnerInstitutionType.setSelection(savedTypePos)
        initialInstitutionTypePosition = savedTypePos

        binding.cbShowPin.setOnCheckedChangeListener { _, isChecked ->
            val inputType = if (isChecked) {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_NORMAL
            } else {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            binding.etPin.inputType = inputType
            binding.etConfirmPin.inputType = inputType
            
            binding.etPin.setSelection(binding.etPin.text.length)
            binding.etConfirmPin.setSelection(binding.etConfirmPin.text.length)
        }

        setupPinTextWatcher(binding.etPin)
        setupPinTextWatcher(binding.etConfirmPin)

        isEditMode = intent.getBooleanExtra("EXTRA_EDIT_MODE", false)
        if (isEditMode) {
            title = "Edit Profile"
            binding.btnSaveProfile.text = "Save Changes"
            binding.btnSaveProfile.contentDescription = "Save changes to your profile"
            
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val profile = db.userProfileDao().getProfile()
                if (profile != null) {
                    existingProfileId = profile.id
                    runOnUiThread {
                        binding.etFirstName.setText(profile.firstName)
                        binding.etLastName.setText(profile.lastName)
                        binding.etSchoolName.setText(profile.schoolName)
                        binding.etPin.setText(profile.pin)
                        binding.etConfirmPin.setText(profile.pin)

                        initialFirstName = profile.firstName
                        initialLastName = profile.lastName
                        initialSchoolName = profile.schoolName
                        initialPin = profile.pin
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupPinTextWatcher(editText: android.widget.EditText) {
        editText.addTextChangedListener(object : android.text.TextWatcher {
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
                        editText.announceForAccessibility("$addedChar")
                    } else {
                        editText.announceForAccessibility("bullet")
                    }
                } else if (currentLength < lastLength) {
                    editText.announceForAccessibility("deleted")
                }
                lastLength = currentLength
            }
        })
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentFirstName = binding.etFirstName.text.toString().trim()
        val currentLastName = binding.etLastName.text.toString().trim()
        val currentSchoolName = binding.etSchoolName.text.toString().trim()
        val currentPin = binding.etPin.text.toString().trim()
        val currentInstitutionTypePosition = binding.spinnerInstitutionType.selectedItemPosition

        return currentFirstName != initialFirstName ||
                currentLastName != initialLastName ||
                currentSchoolName != initialSchoolName ||
                currentPin != initialPin ||
                currentInstitutionTypePosition != initialInstitutionTypePosition
    }

    private fun showUnsavedChangesDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("Do you want to save the changes?")
            .setPositiveButton("Save") { _, _ ->
                saveProfile()
            }
            .setNegativeButton("Discard") { _, _ ->
                finish()
            }
            .setNeutralButton("Cancel", null)
            .show()
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
            val isCollege = binding.spinnerInstitutionType.selectedItemPosition == 1
            binding.etSchoolName.error = if (isCollege) "Please enter your college/university name" else "Please enter your school name"
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

        val selectedType = if (binding.spinnerInstitutionType.selectedItemPosition == 1) "college" else "school"
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val previouslySavedType = prefs.getString("institution_type", "school")
        val isTypeChanged = previouslySavedType != selectedType
        
        prefs.edit().putString("institution_type", selectedType).apply()
 
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.userProfileDao().saveProfile(
                UserProfile(
                    id = existingProfileId,
                    firstName = firstName,
                    lastName = lastName,
                    schoolName = schoolName,
                    pin = pin
                )
            )
            runOnUiThread {
                val message = if (isEditMode && isTypeChanged) {
                    "Profile saved successfully. You need to restart the app in order for these changes to take effect."
                } else {
                    "Profile saved successfully"
                }
                Toast.makeText(this@SetupProfileActivity, message, Toast.LENGTH_LONG).show()
                binding.root.announceForAccessibility(message)
                
                if (isEditMode) {
                    finish()
                } else {
                    startActivity(Intent(this@SetupProfileActivity, PinLoginActivity::class.java))
                    finish()
                }
            }
        }
    }
}