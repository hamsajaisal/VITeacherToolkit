package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.databinding.ActivityMyClassBinding
import kotlinx.coroutines.launch

class MyClassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyClassBinding
    private var classId: Int = 1
    private var classroom: Classroom? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyClassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        if (isCollege) {
            binding.tvTitle.text = "My Program"
            binding.btnClassProfile.text = "Program Profile"
            binding.btnClassProfile.contentDescription = "Program Profile"
        }

        classId = intent.getIntExtra("class_id", 1)

        setupClassDetails()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAttendanceRegister.setOnClickListener {
            val intent = Intent(this, AttendanceRegisterActivity::class.java).apply {
                putExtra("class_id", classId)
            }
            startActivity(intent)
        }

        binding.btnClassProfile.setOnClickListener {
            val intent = Intent(this, ClassProfileActivity::class.java).apply {
                putExtra("class_id", classId)
            }
            startActivity(intent)
        }

        binding.btnCustomChecklists.setOnClickListener {
            val intent = Intent(this, ChecklistsListActivity::class.java).apply {
                putExtra("class_id", classId)
            }
            startActivity(intent)
        }

        binding.btnManageStudents.setOnClickListener {
            showStudentsCountDialog()
        }
    }

    private fun setupClassDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbClass = db.classroomDao().getClassroomById(classId)
            
            if (dbClass == null) {
                runOnUiThread {
                    Toast.makeText(this@MyClassActivity, "Error: Classroom not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            classroom = dbClass
            runOnUiThread {
                val standard = dbClass.standard
                val division = dbClass.division
                val academicYear = dbClass.academicYear

                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"

                val displayText = if (isCollege) {
                    "$standard $division — $academicYear"
                } else {
                    "Class $standard$division — $academicYear"
                }
                binding.tvClassDetails.text = displayText

                // TalkBack announcement matching requirement
                val academicYearSpoken = academicYear.replace("-", " to ")
                val contentDesc = if (isCollege) {
                    "$standard $division, Academic Year $academicYearSpoken"
                } else {
                    "Class $standard$division, Academic Year $academicYearSpoken"
                }
                binding.tvClassDetails.contentDescription = contentDesc
            }
        }
    }

    private fun showStudentsCountDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val currentStudents = db.studentDao().getAllStudentsOnce(classId)
            val defaultCount = if (currentStudents.isNotEmpty()) currentStudents.size.toString() else "30"

            runOnUiThread {
                val input = EditText(this@MyClassActivity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER
                    hint = "example 30"
                    contentDescription = "example 30 edit box"
                }

                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val messageText = if (isCollege) {
                    "How many students are there in your program?"
                } else {
                    "How many students are there in your class?"
                }

                val dialog = AlertDialog.Builder(this@MyClassActivity)
                    .setTitle("Students Count")
                    .setMessage(messageText)
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val countText = input.text.toString().trim()
                        if (countText.isNotEmpty()) {
                            try {
                                val count = countText.toInt()
                                if (count > 0) {
                                    val intent = Intent(this@MyClassActivity, AddStudentsManuallyActivity::class.java).apply {
                                        putExtra("class_id", classId)
                                        putExtra("total_students", count)
                                    }
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this@MyClassActivity, "Please enter a number greater than 0", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MyClassActivity, "Invalid number", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel") { d, _ ->
                        d.dismiss()
                    }
                    .create()

                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "OK"
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel"
                
                val announceMsg = if (isCollege) {
                    "Dialog opened. How many students are there in your program? Double tap to type in the input box, then select OK or Cancel."
                } else {
                    "Dialog opened. How many students are there in your class? Double tap to type in the input box, then select OK or Cancel."
                }
                binding.root.announceForAccessibility(announceMsg)
            }
        }
    }
}
