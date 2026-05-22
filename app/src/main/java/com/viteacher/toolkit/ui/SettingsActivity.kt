package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

        binding.btnSchoolHours.setOnClickListener {
            startActivity(Intent(this, SchoolHoursActivity::class.java))
        }

        binding.btnSaveLanguage.setOnClickListener {
            saveLanguage()
        }

        binding.btnClassroomSettings.setOnClickListener {
            startActivity(Intent(this, ClassroomSettingsActivity::class.java))
        }
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