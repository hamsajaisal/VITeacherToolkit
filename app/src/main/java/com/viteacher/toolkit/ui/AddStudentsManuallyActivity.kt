package com.viteacher.toolkit.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Student
import com.viteacher.toolkit.databinding.ActivityAddStudentsManuallyBinding
import kotlinx.coroutines.launch

class AddStudentsManuallyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddStudentsManuallyBinding
    private var classId: Int = 1
    private var totalStudents: Int = 30
    private val studentNamesMap = mutableMapOf<Int, String>()
    
    private val spinnerList = mutableListOf<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var lastSelectedPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStudentsManuallyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)
        totalStudents = intent.getIntExtra("total_students", 30)

        setupSpinnerAdapter()
        loadExistingStudents()

        binding.btnBack.setOnClickListener {
            showExitConfirmation()
        }

        binding.btnCancel.setOnClickListener {
            showExitConfirmation()
        }

        binding.btnAddExtraRoll.setOnClickListener {
            addExtraRollNumber()
        }

        binding.btnSaveRoster.setOnClickListener {
            saveStudentRoster()
        }

        setupAccessibilityHandlers()
    }

    private fun setupSpinnerAdapter() {
        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerList)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spRollNumber.adapter = spinnerAdapter

        binding.spRollNumber.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // 1. Save current name for previous roll number
                saveCurrentEditTextName()

                // 2. Load name for new roll number
                val currentRoll = position + 1
                val name = studentNamesMap[currentRoll] ?: ""
                binding.etStudentName.setText(name)
                binding.etStudentName.setSelection(name.length)

                binding.etStudentName.hint = "Enter name for Roll Number $currentRoll"
                binding.etStudentName.contentDescription = "Student name edit box for Roll Number $currentRoll. Current name is ${if (name.isEmpty()) "empty" else name}."

                lastSelectedPosition = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadExistingStudents() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbStudents = db.studentDao().getAllStudentsOnce(classId)

            runOnUiThread {
                dbStudents.forEach { student ->
                    studentNamesMap[student.rollNumber] = student.name
                }

                val maxSavedRoll = dbStudents.maxOfOrNull { it.rollNumber } ?: 0
                totalStudents = maxOf(totalStudents, maxSavedRoll)

                rebuildSpinnerList()

                if (totalStudents > 0) {
                    binding.spRollNumber.setSelection(0)
                }
            }
        }
    }

    private fun rebuildSpinnerList() {
        spinnerList.clear()
        for (i in 1..totalStudents) {
            spinnerList.add("Roll Number $i")
        }
        spinnerAdapter.notifyDataSetChanged()
    }

    private fun saveCurrentEditTextName() {
        if (lastSelectedPosition != -1) {
            val prevRoll = lastSelectedPosition + 1
            val name = binding.etStudentName.text.toString().trim()
            studentNamesMap[prevRoll] = name
        }
    }

    private fun addExtraRollNumber() {
        // Save current typing state
        saveCurrentEditTextName()

        totalStudents++
        spinnerList.add("Roll Number $totalStudents")
        spinnerAdapter.notifyDataSetChanged()

        // Programmatically select the newly added roll number
        binding.spRollNumber.setSelection(totalStudents - 1)

        // Focus the name box directly to trigger keyboard
        binding.etStudentName.postDelayed({
            binding.etStudentName.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etStudentName, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        val announce = "Added Roll Number $totalStudents. Dropped down selection updated to Roll Number $totalStudents."
        binding.root.announceForAccessibility(announce)
    }

    private fun saveStudentRoster() {
        // Save active text state first
        saveCurrentEditTextName()

        val filledNames = studentNamesMap.filterValues { it.isNotEmpty() }

        if (filledNames.isEmpty()) {
            val emptyMsg = "Please enter at least one student name before saving."
            Toast.makeText(this, emptyMsg, Toast.LENGTH_SHORT).show()
            binding.root.announceForAccessibility(emptyMsg)
            return
        }

        if (filledNames.size < totalStudents) {
            // Show incomplete roster alert dialog
            val dialog = AlertDialog.Builder(this)
                .setTitle("Save Incomplete Roster?")
                .setMessage("You have only added ${filledNames.size} student names out of $totalStudents. Would you like to save these ${filledNames.size} students and add the rest later?")
                .setPositiveButton("Save ${filledNames.size} Students") { _, _ ->
                    performDatabaseRosterSave(filledNames)
                }
                .setNegativeButton("Cancel") { d, _ ->
                    d.dismiss()
                    binding.root.announceForAccessibility("Save cancelled. You can continue typing student names.")
                }
                .create()
            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Save ${filledNames.size} students button"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel button"
            binding.root.announceForAccessibility("Warning dialog. You have only added ${filledNames.size} student names. Save these students or Cancel?")
        } else {
            performDatabaseRosterSave(filledNames)
        }
    }

    private fun performDatabaseRosterSave(filledNames: Map<Int, String>) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Delete old roster and insert updated manual roster list
            db.studentDao().deleteAllStudents(classId)
            
            val studentsList = filledNames.map { Student(classId = classId, rollNumber = it.key, name = it.value) }
            db.studentDao().insertStudents(studentsList)

            runOnUiThread {
                val successMessage = "${studentsList.size} students roster saved successfully"
                Toast.makeText(this@AddStudentsManuallyActivity, successMessage, Toast.LENGTH_LONG).show()
                binding.root.announceForAccessibility(successMessage)
                finish()
            }
        }
    }

    private fun showExitConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("Are you sure you want to go back? Any unsaved student names will be discarded.")
            .setPositiveButton("Discard & Exit") { _, _ ->
                finish()
            }
            .setNegativeButton("Keep Typing") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Keep typing selected.")
            }
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Discard and exit button"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Keep typing button"
        binding.root.announceForAccessibility("Warning dialog. Unsaved changes will be discarded. Discard and Exit or Keep Typing?")
    }

    private fun setupAccessibilityHandlers() {
        binding.etStudentName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.etStudentName.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etStudentName.windowToken, 0)
                true
            } else false
        }

        val accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                super.sendAccessibilityEvent(host, eventType)
                if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    when (host.id) {
                        binding.etStudentName.id -> {
                            if (!host.isFocused) {
                                host.requestFocus()
                            }
                        }
                        binding.btnSaveRoster.id, binding.btnAddExtraRoll.id -> {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(host.windowToken, 0)
                        }
                    }
                }
            }
        }

        binding.etStudentName.accessibilityDelegate = accessibilityDelegate
        binding.btnSaveRoster.accessibilityDelegate = accessibilityDelegate
        binding.btnAddExtraRoll.accessibilityDelegate = accessibilityDelegate
        binding.btnBack.accessibilityDelegate = accessibilityDelegate
    }

    override fun onBackPressed() {
        showExitConfirmation()
    }
}
