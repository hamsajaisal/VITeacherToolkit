package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivityMyClassBinding

class MyClassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyClassBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyClassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClassDetails()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAttendanceRegister.setOnClickListener {
            startActivity(Intent(this, AttendanceRegisterActivity::class.java))
        }
    }

    private fun setupClassDetails() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val standard = prefs.getString("class_standard", "") ?: ""
        val division = prefs.getString("class_division", "") ?: ""
        val academicYear = prefs.getString("class_academic_year", "") ?: ""

        val displayText = "Class $standard$division — $academicYear"
        binding.tvClassDetails.text = displayText

        // TalkBack announcement matching requirement
        val academicYearSpoken = academicYear.replace("-", " to ")
        val contentDesc = "Class $standard$division, Academic Year $academicYearSpoken"
        binding.tvClassDetails.contentDescription = contentDesc
    }
}
