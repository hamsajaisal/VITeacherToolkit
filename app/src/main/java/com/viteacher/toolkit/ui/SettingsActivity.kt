package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivitySettingsBinding
import com.viteacher.toolkit.util.setAccessibleSelection

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val languages = listOf("English", "Malayalam")
    private val languageCodes = listOf("en", "ml")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageSpinner()
        loadClassSettings()

        binding.btnSchoolHours.setOnClickListener {
            startActivity(Intent(this, SchoolHoursActivity::class.java))
        }

        binding.btnSaveLanguage.setOnClickListener {
            saveLanguage()
        }

        binding.btnSaveClassSettings.setOnClickListener {
            saveClassSettings()
        }
    }

    private fun loadClassSettings() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        binding.etClassStandard.setText(prefs.getString("class_standard", ""))
        binding.etClassDivision.setText(prefs.getString("class_division", ""))
        binding.etClassAcademicYear.setText(prefs.getString("class_academic_year", ""))
    }

    private fun saveClassSettings() {
        val standard = binding.etClassStandard.text.toString().trim()
        val division = binding.etClassDivision.text.toString().trim()
        val academicYear = binding.etClassAcademicYear.text.toString().trim()

        if (standard.isEmpty()) {
            binding.etClassStandard.error = "Please enter standard or class"
            binding.etClassStandard.requestFocus()
            binding.root.announceForAccessibility("Error: Standard or Class field is empty. Please enter class.")
            return
        }

        if (division.isEmpty()) {
            binding.etClassDivision.error = "Please enter division"
            binding.etClassDivision.requestFocus()
            binding.root.announceForAccessibility("Error: Division field is empty. Please enter division.")
            return
        }

        if (academicYear.isEmpty()) {
            binding.etClassAcademicYear.error = "Please enter academic year"
            binding.etClassAcademicYear.requestFocus()
            binding.root.announceForAccessibility("Error: Academic year field is empty. Please enter academic year.")
            return
        }

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("class_standard", standard)
            .putString("class_division", division)
            .putString("class_academic_year", academicYear)
            .apply()

        val message = "Class settings saved successfully"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility(message)
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter
        binding.spinnerLanguage.setAccessibleSelection("Reminder language")

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val savedCode = prefs.getString("reminder_language", "en")
        val index = languageCodes.indexOf(savedCode)
        if (index >= 0) binding.spinnerLanguage.setSelection(index)
    }

    private fun saveLanguage() {
        val selectedIndex = binding.spinnerLanguage.selectedItemPosition
        val selectedCode = languageCodes[selectedIndex]
        val selectedName = languages[selectedIndex]

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("reminder_language", selectedCode).apply()

        val message = "Reminder language set to $selectedName"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility(message)
    }
}