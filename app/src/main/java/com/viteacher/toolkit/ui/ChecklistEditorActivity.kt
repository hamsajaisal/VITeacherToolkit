package com.viteacher.toolkit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.ChecklistRecord
import com.viteacher.toolkit.data.Student
import com.viteacher.toolkit.databinding.ActivityChecklistEditorBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ChecklistEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChecklistEditorBinding
    private lateinit var studentAdapter: StudentChecklistAdapter
    private var studentList: MutableList<StudentChecklistItem> = mutableListOf()

    private var classId: Int = 1
    private var mode: String = "create" // "create" or "edit"
    private var checklistName: String = ""
    private var displayDate: String = ""
    private var isModified: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)
        mode = intent.getStringExtra("mode") ?: "create"
        checklistName = intent.getStringExtra("checklist_name") ?: ""

        setupDate()
        setupRecyclerView()

        if (mode == "create") {
            showNameSelectionDialog()
        } else {
            binding.tvTitle.text = checklistName
            binding.tvTitle.contentDescription = "$checklistName Register Editor Screen"
            loadExistingChecklist()
        }

        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }

        binding.btnSaveChecklist.setOnClickListener {
            saveChecklist {
                isModified = false
                finish()
            }
        }

        binding.btnSharePending.setOnClickListener {
            sharePendingList()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun setupDate() {
        val today = LocalDate.now()
        val dbFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        displayDate = today.format(dbFormatter)
    }

    private fun setupRecyclerView() {
        studentAdapter = StudentChecklistAdapter(
            studentList,
            onItemClick = { position, isChecked ->
                val item = studentList[position]
                isModified = true
                
                val statusAnnouncement = if (isChecked) "marked completed" else "marked pending"
                val announcement = "${item.name} $statusAnnouncement"
                binding.root.announceForAccessibility(announcement)
                triggerHapticFeedback()
            }
        )
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter = studentAdapter
    }

    private fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun showNameSelectionDialog() {
        val defaultSuggestions = listOf(
            "Homework",
            "Midday Meal",
            "Tour Cash",
            "Assignment",
            "Lab Record",
            "Field Trip Consent"
        )
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val customSuggestions = prefs.getStringSet("custom_checklist_suggestions", emptySet()) ?: emptySet()
        val allSuggestions = (defaultSuggestions + customSuggestions.sorted()).toMutableList().apply {
            add("Other (Type custom...)")
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Register Title")
            .setItems(allSuggestions.toTypedArray()) { _, which ->
                val selection = allSuggestions[which]
                if (selection == "Other (Type custom...)") {
                    showCustomNameInputDialog()
                } else {
                    checkAndSetChecklistName(selection, shouldSave = false)
                }
            }
            .setCancelable(false)
            .create()
            .show()
        binding.root.announceForAccessibility("Choose Register Title dialog. Select a category or choose Other to type a custom title.")
    }

    private fun showCustomNameInputDialog() {
        val container = android.widget.FrameLayout(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            val paddingPx = (24 * density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        val input = EditText(this).apply {
            hint = "e.g., Tour Cash"
            contentDescription = "Type register title here"
            setSingleLine(true)
        }

        val saveCheckbox = android.widget.CheckBox(this).apply {
            text = "Save this title for future use"
            isChecked = true
            contentDescription = "Save this title for future use, checkbox checked"
            setOnCheckedChangeListener { _, isChecked ->
                contentDescription = "Save this title for future use, checkbox ${if (isChecked) "checked" else "not checked"}"
            }
            val density = resources.displayMetrics.density
            val marginPx = (8 * density).toInt()
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginPx
            }
            layoutParams = lp
        }

        layout.addView(input)
        layout.addView(saveCheckbox)
        container.addView(layout)
        container.setupCursorEndForEditTexts()

        AlertDialog.Builder(this)
            .setTitle("Type Custom Title")
            .setView(container)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                val shouldSave = saveCheckbox.isChecked
                if (name.isNotEmpty()) {
                    checkAndSetChecklistName(name, shouldSave)
                } else {
                    Toast.makeText(this, "Title cannot be empty.", Toast.LENGTH_SHORT).show()
                    showCustomNameInputDialog()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
            .show()
        binding.root.announceForAccessibility("Type custom title dialog. Type a custom title, check or uncheck save checkbox, then select Next or Cancel.")
    }

    private fun checkAndSetChecklistName(name: String, shouldSave: Boolean = false) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val existing = db.checklistDao().getChecklist(classId, name)
            if (existing.isNotEmpty()) {
                runOnUiThread {
                    AlertDialog.Builder(this@ChecklistEditorActivity)
                        .setTitle("Register Already Exists")
                        .setMessage("A register named '$name' already exists in this class. Do you want to edit it instead?")
                        .setPositiveButton("Edit Existing") { _, _ ->
                            mode = "edit"
                            checklistName = name
                            binding.tvTitle.text = checklistName
                            binding.tvTitle.contentDescription = "$checklistName Register Editor Screen"
                            loadExistingChecklist()
                        }
                        .setNegativeButton("Choose Different Name") { _, _ ->
                            showNameSelectionDialog()
                        }
                        .setCancelable(false)
                        .create()
                        .show()
                    binding.root.announceForAccessibility("Register already exists warning. Select Edit Existing or Choose Different Name.")
                }
            } else {
                runOnUiThread {
                    if (shouldSave) {
                        val defaultSuggestions = listOf("Homework", "Midday Meal", "Tour Cash", "Assignment", "Lab Record", "Field Trip Consent")
                        if (!defaultSuggestions.contains(name)) {
                            val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                            val currentCustom = prefs.getStringSet("custom_checklist_suggestions", emptySet()) ?: emptySet()
                            if (!currentCustom.contains(name)) {
                                val newCustom = currentCustom.toMutableSet().apply { add(name) }
                                prefs.edit().putStringSet("custom_checklist_suggestions", newCustom).apply()
                            }
                        }
                    }

                    checklistName = name
                    binding.tvTitle.text = checklistName
                    binding.tvTitle.contentDescription = "$checklistName Register Editor Screen"
                    isModified = true
                    loadStudentsForNewChecklist()
                }
            }
        }
    }

    private fun loadStudentsForNewChecklist() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // 1. Try to load from the attendance register (students table)
            var students = db.studentDao().getAllStudentsOnce(classId)

            // 2. Fallback: If empty, try to load from the classroom profile (student_profiles table)
            if (students.isEmpty()) {
                val profiles = db.studentProfileDao().getAllStudentProfiles(classId)
                if (profiles.isNotEmpty()) {
                    // Try to find if any custom profile field represents a roll number (contains "roll" in field name)
                    val fields = db.studentProfileFieldDao().getFieldsForClass(classId)
                    val rollNoFieldName = fields.map { it.fieldName.lowercase() }
                        .firstOrNull { it.contains("roll") }

                    val profileToRollMap = mutableMapOf<String, Int>()
                    if (rollNoFieldName != null) {
                        fields.filter { it.fieldName.lowercase() == rollNoFieldName }.forEach { field ->
                            val rollInt = field.fieldValue.toIntOrNull()
                            if (rollInt != null) {
                                profileToRollMap[field.admissionNumber] = rollInt
                            }
                        }
                    }

                    // Sort profiles: by parsed roll number first (if present), then alphabetically by name
                    val sortedProfiles = profiles.sortedWith(compareBy(
                        { profileToRollMap[it.admissionNumber] ?: Int.MAX_VALUE },
                        { it.name }
                    ))

                    // Map profiles to standard Student records
                    students = sortedProfiles.mapIndexed { index, profile ->
                        val roll = profileToRollMap[profile.admissionNumber] ?: (index + 1)
                        Student(classId = classId, rollNumber = roll, name = profile.name)
                    }
                }
            }

            runOnUiThread {
                studentList.clear()
                if (students.isEmpty()) {
                    Toast.makeText(this@ChecklistEditorActivity, "No students found in class roster or profiles.", Toast.LENGTH_LONG).show()
                    finish()
                    return@runOnUiThread
                }
                students.forEach { student ->
                    studentList.add(StudentChecklistItem(student.rollNumber, student.name, isChecked = true))
                }
                studentAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun loadExistingChecklist() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val records = db.checklistDao().getChecklist(classId, checklistName)
            runOnUiThread {
                studentList.clear()
                if (records.isEmpty()) {
                    Toast.makeText(this@ChecklistEditorActivity, "Error loading checklist.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                records.forEach { record ->
                    studentList.add(StudentChecklistItem(record.rollNumber, record.name, isChecked = record.isChecked))
                }
                studentAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun saveChecklist(onSuccess: () -> Unit) {
        if (studentList.isEmpty()) {
            Toast.makeText(this, "No students to save.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val records = studentList.map {
                ChecklistRecord(
                    classId = classId,
                    checklistName = checklistName,
                    rollNumber = it.rollNumber,
                    name = it.name,
                    isChecked = it.isChecked,
                    date = displayDate
                )
            }
            db.checklistDao().insertChecklistRecords(records)
            runOnUiThread {
                Toast.makeText(this@ChecklistEditorActivity, "Register saved successfully.", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        }
    }

    private fun sharePendingList() {
        val pending = studentList.filter { !it.isChecked }
        val totalPending = pending.size
        
        val message = StringBuilder()
        message.append("$checklistName Pending Report\n")
        message.append("Date: $displayDate\n\n")

        if (totalPending == 0) {
            message.append("All students have completed this register!")
        } else {
            pending.forEach {
                message.append("Roll No. ${it.rollNumber} — ${it.name}\n")
            }
            message.append("\nTotal Pending: $totalPending")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message.toString())
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Pending List"))
        binding.root.announceForAccessibility("Opening share options for pending list.")
    }

    private fun handleBackNavigation() {
        if (isModified) {
            AlertDialog.Builder(this)
                .setTitle("Save Changes?")
                .setMessage("Do you want to save this register for future reference?")
                .setPositiveButton("Save") { _, _ ->
                    saveChecklist {
                        isModified = false
                        finish()
                    }
                }
                .setNegativeButton("Discard") { _, _ ->
                    isModified = false
                    finish()
                }
                .setNeutralButton("Cancel") { d, _ ->
                    d.dismiss()
                }
                .create()
                .show()
            binding.root.announceForAccessibility("Save changes warning. Do you want to save this register for future reference? Select Save, Discard, or Cancel.")
        } else {
            finish()
        }
    }
}

// Model for memory state
data class StudentChecklistItem(
    val rollNumber: Int,
    val name: String,
    var isChecked: Boolean
)

// RecyclerView Adapter
class StudentChecklistAdapter(
    private val list: List<StudentChecklistItem>,
    private val onItemClick: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<StudentChecklistAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvRoll: TextView = view.findViewById(R.id.tvRollNumber)
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStatus: TextView = view.findViewById(R.id.tvChecklistStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_student, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvRoll.text = item.rollNumber.toString()
        holder.tvName.text = item.name

        updateViewHolderUI(holder, item)

        holder.view.setOnClickListener {
            item.isChecked = !item.isChecked
            updateViewHolderUI(holder, item)
            onItemClick(position, item.isChecked)
        }
    }

    private fun updateViewHolderUI(holder: ViewHolder, item: StudentChecklistItem) {
        if (item.isChecked) {
            holder.tvStatus.text = "Completed"
            holder.view.contentDescription = "Roll number ${item.rollNumber}, ${item.name}, Completed. Double tap to mark pending."
        } else {
            holder.tvStatus.text = "Pending"
            holder.view.contentDescription = "Roll number ${item.rollNumber}, ${item.name}, Pending. Double tap to mark completed."
        }
        com.viteacher.toolkit.util.ThemeUtils.styleRosterRow(
            holder.view.context,
            holder.view,
            holder.tvRoll,
            holder.tvName,
            holder.tvStatus,
            item.isChecked
        )
    }

    override fun getItemCount(): Int = list.size
}
