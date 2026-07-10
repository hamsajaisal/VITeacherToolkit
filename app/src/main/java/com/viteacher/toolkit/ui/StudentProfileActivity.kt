package com.viteacher.toolkit.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.StudentProfile
import com.viteacher.toolkit.data.StudentProfileField
import com.viteacher.toolkit.data.StudentRemark
import com.viteacher.toolkit.databinding.ActivityStudentProfileBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileBinding
    private var classId: Int = 1
    private var admissionNumber: String = ""
    
    private var student: StudentProfile? = null
    private var fields = listOf<StudentProfileField>()
    private var remarks = listOf<StudentRemark>()
    private var parentPhoneNumber: String? = null

    // Subjects list for remarks spinner
    private val subjects = arrayOf(
        "General", "Malayalam", "English", "Mathematics", "Science",
        "Social Science", "Hindi", "Urdu", "Arabic", "Physical Education", "Arts"
    )

    // Permission request launcher for calling phone
    private val requestCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val phone = parentPhoneNumber
        if (phone != null) {
            if (isGranted) {
                makeDirectCall(phone)
            } else {
                makeDialCall(phone)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)
        admissionNumber = intent.getStringExtra("admission_number") ?: ""

        setupUI()
        loadStudentDetails()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCallParent.setOnClickListener {
            val phone = parentPhoneNumber
            if (phone.isNullOrEmpty()) {
                val announceMsg = "No phone number available for this student"
                Toast.makeText(this, announceMsg, Toast.LENGTH_SHORT).show()
                binding.btnCallParent.announceForAccessibility(announceMsg)
                triggerHapticFeedback(true)
            } else {
                triggerCallFlow(phone)
            }
        }

        binding.btnAddRemark.setOnClickListener {
            showAddRemarkDialog()
        }

        binding.btnDeleteProfile.setOnClickListener {
            showDeleteProfileConfirmationDialog()
        }

        binding.btnMore.setOnClickListener {
            showMoreOptions()
        }
    }

    private fun showMoreOptions() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnMore)
        popup.menu.add(0, 1, 0, "Add Custom Info")
        popup.menu.add(0, 2, 1, "Delete Profile")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showAddCustomInfoDialog()
                    true
                }
                2 -> {
                    showDeleteProfileConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAddCustomInfoDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val inputHeader = EditText(context).apply {
            hint = "e.g., APL/BPL, Blood Group"
            contentDescription = "Type information heading"
            setSingleLine(true)
        }
        layout.addView(inputHeader)

        val inputVal = EditText(context).apply {
            hint = "Value"
            contentDescription = "Type value here"
            setSingleLine(true)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            layoutParams = lp
        }
        layout.addView(inputVal)

        val container = FrameLayout(context)
        container.addView(layout)
        container.setupCursorEndForEditTexts()

        AlertDialog.Builder(context)
            .setTitle("Add Custom Info")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val heading = inputHeader.text.toString().trim()
                val value = inputVal.text.toString().trim()
                if (heading.isNotEmpty()) {
                    saveProfileValue(heading, value)
                } else {
                    Toast.makeText(context, "Heading cannot be empty.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadStudentDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            student = db.studentProfileDao().getStudentProfile(classId, admissionNumber)
            remarks = db.studentRemarkDao().getRemarksForStudent(classId, admissionNumber)

            val studentFields = db.studentProfileFieldDao().getFieldsForStudent(classId, admissionNumber)
            val studentFieldMap = studentFields.associate { it.fieldName to it.fieldValue }

            val classFields = db.studentProfileFieldDao().getFieldsForClass(classId)
            val uniqueFieldNames = classFields.map { it.fieldName }.distinct().sorted()

            fields = uniqueFieldNames.map { fieldName ->
                val valStr = studentFieldMap[fieldName] ?: ""
                StudentProfileField(classId, admissionNumber, fieldName, valStr)
            }

            runOnUiThread {
                if (student == null) {
                    Toast.makeText(this@StudentProfileActivity, "Student profile not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }

                binding.tvStudentNameTitle.text = student?.name
                binding.tvStudentNameTitle.contentDescription = "${student?.name} profile screen"
                
                checkBirthdayToday()
                renderDynamicDetails()
                renderRemarks()
            }
        }
    }

    private fun checkBirthdayToday() {
        val dobField = fields.firstOrNull { 
            val name = it.fieldName.lowercase()
            name.contains("birth") || name.contains("dob")
        } ?: return

        val dobValue = dobField.fieldValue
        try {
            // Formats: standard is dd/MM/yyyy or d/M/yyyy
            val parts = dobValue.split("/")
            if (parts.size >= 2) {
                val dobDay = parts[0].toInt()
                val dobMonth = parts[1].toInt()

                val calendar = java.util.Calendar.getInstance()
                val todayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                val todayMonth = calendar.get(java.util.Calendar.MONTH) + 1 // 0-indexed month

                if (dobDay == todayDay && dobMonth == todayMonth) {
                    binding.layoutBirthdayIndicator.visibility = View.VISIBLE
                    val bDesc = "Today is ${student?.name}'s birthday"
                    binding.layoutBirthdayIndicator.contentDescription = bDesc
                    binding.tvBirthdayLabel.text = "Today is their birthday!"
                } else {
                    binding.layoutBirthdayIndicator.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun renderDynamicDetails() {
        binding.layoutDetails.removeAllViews()

        var street = ""
        var postoffice = ""
        var pincode = ""

        addDetailRow("Name", student?.name ?: "")
        addDetailRow("Admission Number", student?.admissionNumber ?: "")

        fields.forEach { field ->
            val fieldName = field.fieldName.lowercase()
            val value = field.fieldValue

            if (value.isNotEmpty()) {
                if (fieldName.contains("street")) street = value
                if (fieldName.contains("post")) postoffice = value
                if (fieldName.contains("pincode") || fieldName.contains("pin code")) pincode = value
                
                if (fieldName.contains("phone") || fieldName.contains("mobile")) {
                    parentPhoneNumber = value
                }
            }

            val displayValue = if (value.isEmpty()) "Not Specified" else value
            addDetailRow(field.fieldName, displayValue)
        }

        if (street.isNotEmpty() || postoffice.isNotEmpty() || pincode.isNotEmpty()) {
            val combinedAddress = listOf(street, postoffice, pincode).filter { it.isNotEmpty() }.joinToString(", ")
            addDetailRow("Full Address", combinedAddress, isCombinedAddress = true)
        }

        // 3. Configure the Call Parent button based on phone availability
        val phone = parentPhoneNumber
        if (phone.isNullOrEmpty()) {
            binding.btnCallParent.backgroundTintList = ContextCompat.getColorStateList(this, R.color.grey_dark)
            binding.btnCallParent.setTextColor(ContextCompat.getColor(this, R.color.grey_light))
            binding.btnCallParent.contentDescription = "No phone number available for this student"
        } else {
            binding.btnCallParent.backgroundTintList = ContextCompat.getColorStateList(this, R.color.lavender)
            binding.btnCallParent.setTextColor(ContextCompat.getColor(this, R.color.black))
            binding.btnCallParent.contentDescription = "Call parent"
        }
    }

    private fun addDetailRow(label: String, value: String, isCombinedAddress: Boolean = false) {
        val context = this
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
            isFocusable = true
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setBackgroundResource(R.drawable.bg_history_item)
            
            // Margins between rows
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            layoutParams = params
        }

        val tvLabel = TextView(context).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.remark_subject_color))
            textSize = 14f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val tvValue = TextView(context).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, R.color.profile_value_color))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        row.addView(tvLabel)
        row.addView(tvValue)

        val cleanVal = if (value == "Not Specified") "" else value

        row.contentDescription = "$label column: $value value. Double tap to focus, long press for options."
        row.setOnLongClickListener {
            showRowActionDialog(label, cleanVal)
            true
        }

        binding.layoutDetails.addView(row)
    }

    private fun showRowActionDialog(label: String, value: String) {
        val options = if (label.equals("Admission Number", ignoreCase = true) || label.equals("Full Address", ignoreCase = true)) {
            arrayOf("Copy to Clipboard")
        } else {
            arrayOf("Copy to Clipboard", "Edit Info")
        }

        AlertDialog.Builder(this)
            .setTitle("$label Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyToClipboard(label, value)
                    1 -> showEditRowDialog(label, value)
                }
            }
            .show()
    }

    private fun showEditRowDialog(label: String, currentValue: String) {
        val input = EditText(this).apply {
            setText(currentValue)
            hint = "Value for '$label'"
            contentDescription = "Enter new value for $label"
            setSingleLine(true)
        }
        val container = FrameLayout(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(input)
        }
        container.addView(layout)
        container.setupCursorEndForEditTexts()

        AlertDialog.Builder(this)
            .setTitle("Edit $label")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newValue = input.text.toString().trim()
                saveProfileValue(label, newValue)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveProfileValue(label: String, newValue: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            if (label.equals("Name", ignoreCase = true)) {
                val oldName = student?.name ?: ""
                db.studentProfileDao().insertStudentProfiles(listOf(
                    StudentProfile(classId, admissionNumber, newValue)
                ))
                if (oldName.isNotEmpty() && newValue.isNotEmpty()) {
                    val rosterStudent = db.studentDao().getAllStudentsOnce(classId)
                        .firstOrNull { it.name.lowercase() == oldName.lowercase() }
                    if (rosterStudent != null) {
                        db.studentDao().insertStudents(listOf(
                            com.viteacher.toolkit.data.Student(classId, rosterStudent.rollNumber, newValue)
                        ))
                    }
                }
            } else {
                db.studentProfileFieldDao().insertStudentProfileFields(listOf(
                    StudentProfileField(classId, admissionNumber, label, newValue)
                ))
            }
            runOnUiThread {
                Toast.makeText(this@StudentProfileActivity, "Updated successfully", Toast.LENGTH_SHORT).show()
                loadStudentDetails()
            }
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)

        val successAnnouncement = "$label copied"
        Toast.makeText(this, successAnnouncement, Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility(successAnnouncement)
        triggerHapticFeedback(false) // Small haptic click for successful copy
    }

    private fun triggerCallFlow(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            makeDirectCall(phoneNumber)
        } else {
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun makeDirectCall(phoneNumber: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(callIntent)
            triggerHapticFeedback(false)
        } catch (e: SecurityException) {
            // Fallback if permission revoked suddenly
            makeDialCall(phoneNumber)
        }
    }

    private fun makeDialCall(phoneNumber: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(dialIntent)
    }

    // --- REMARKS SECTION WORKFLOWS ---

    private fun renderRemarks() {
        binding.layoutRemarks.removeAllViews()

        if (remarks.isEmpty()) {
            binding.tvNoRemarks.visibility = View.VISIBLE
            binding.layoutRemarks.visibility = View.GONE
        } else {
            binding.tvNoRemarks.visibility = View.GONE
            binding.layoutRemarks.visibility = View.VISIBLE

            remarks.forEach { remark ->
                val view = LayoutInflater.from(this).inflate(R.layout.item_remark, binding.layoutRemarks, false)
                
                val tvSubject = view.findViewById<TextView>(R.id.tvRemarkSubject)
                val tvDate = view.findViewById<TextView>(R.id.tvRemarkDate)
                val tvText = view.findViewById<TextView>(R.id.tvRemarkText)

                tvSubject.text = remark.subject
                tvDate.text = remark.date
                tvText.text = remark.remarkText

                // TalkBack content description
                view.contentDescription = "Remark for ${remark.subject}, added on ${remark.date}. Remark content: ${remark.remarkText}. Double tap to focus, long press to edit or delete."

                view.setOnLongClickListener {
                    showEditDeleteRemarkDialog(remark)
                    true
                }

                binding.layoutRemarks.addView(view)
            }
        }
    }

    private fun showAddRemarkDialog() {
        val context = this
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_remark, null)
        dialogView.setupCursorEndForEditTexts()
        val spinnerSubject = dialogView.findViewById<Spinner>(R.id.spinnerSubject)
        val etRemarkText = dialogView.findViewById<EditText>(R.id.etRemarkText)

        // Setup subject spinner
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubject.adapter = adapter
        
        // Spin accessibility
        spinnerSubject.contentDescription = "Select subject dropdown"

        val dialog = AlertDialog.Builder(context)
            .setTitle("Add Remark")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedSubject = spinnerSubject.selectedItem.toString()
                val remarkText = etRemarkText.text.toString().trim()

                if (remarkText.isNotEmpty()) {
                    saveRemarkToDb(selectedSubject, remarkText)
                } else {
                    Toast.makeText(context, "Remark cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Add remark cancelled.")
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Save"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel"
        binding.root.announceForAccessibility("Add Remark dialog opened. Select subject, enter remark text field, then choose Save or Cancel.")
    }

    private fun saveRemarkToDb(subject: String, text: String) {
        lifecycleScope.launch {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            val dateStr = sdf.format(Date())

            val remark = StudentRemark(
                classId = classId,
                admissionNumber = admissionNumber,
                date = dateStr,
                subject = subject,
                remarkText = text,
                timestamp = System.currentTimeMillis()
            )

            val db = AppDatabase.getDatabase(applicationContext)
            db.studentRemarkDao().insertRemark(remark)

            // Reload remarks
            remarks = db.studentRemarkDao().getRemarksForStudent(classId, admissionNumber)

            runOnUiThread {
                renderRemarks()
                val successAnnouncement = "Remark saved successfully"
                Toast.makeText(this@StudentProfileActivity, successAnnouncement, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(successAnnouncement)
                triggerHapticFeedback(false)
            }
        }
    }

    private fun showEditDeleteRemarkDialog(remark: StudentRemark) {
        val options = arrayOf("Edit Remark", "Delete Remark")
        AlertDialog.Builder(this)
            .setTitle("Manage Remark")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showEditRemarkDialog(remark)
                } else {
                    showDeleteConfirmationDialog(remark)
                }
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Dialog opened. Select Edit Remark or Delete Remark.")
    }

    private fun showEditRemarkDialog(remark: StudentRemark) {
        val context = this
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_remark, null)
        dialogView.setupCursorEndForEditTexts()
        val spinnerSubject = dialogView.findViewById<Spinner>(R.id.spinnerSubject)
        val etRemarkText = dialogView.findViewById<EditText>(R.id.etRemarkText)

        // Setup spinner
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubject.adapter = adapter
        
        // Find saved subject position
        val subIndex = subjects.indexOf(remark.subject)
        if (subIndex >= 0) spinnerSubject.setSelection(subIndex)
        spinnerSubject.contentDescription = "Select subject dropdown"

        etRemarkText.setText(remark.remarkText)
        etRemarkText.contentDescription = "Enter remark text field"

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit Remark")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedSubject = spinnerSubject.selectedItem.toString()
                val remarkText = etRemarkText.text.toString().trim()

                if (remarkText.isNotEmpty()) {
                    updateRemarkInDb(remark.copy(subject = selectedSubject, remarkText = remarkText))
                } else {
                    Toast.makeText(context, "Remark cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Edit remark cancelled.")
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Save"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel"
        binding.root.announceForAccessibility("Edit Remark dialog opened. Modify subject, update remark text field, then choose Save or Cancel.")
    }

    private fun updateRemarkInDb(updatedRemark: StudentRemark) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.studentRemarkDao().updateRemark(updatedRemark)
            remarks = db.studentRemarkDao().getRemarksForStudent(classId, admissionNumber)

            runOnUiThread {
                renderRemarks()
                val successAnnouncement = "Remark updated successfully"
                Toast.makeText(this@StudentProfileActivity, successAnnouncement, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(successAnnouncement)
                triggerHapticFeedback(false)
            }
        }
    }

    private fun showDeleteConfirmationDialog(remark: StudentRemark) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Remark")
            .setMessage("Are you sure you want to delete this remark? This action cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                deleteRemarkFromDb(remark)
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Delete cancelled.")
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No"
        binding.root.announceForAccessibility("Warning dialog. Are you sure you want to delete this remark? Select Yes or No.")
    }

    private fun deleteRemarkFromDb(remark: StudentRemark) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.studentRemarkDao().deleteRemark(remark)
            remarks = db.studentRemarkDao().getRemarksForStudent(classId, admissionNumber)

            runOnUiThread {
                renderRemarks()
                val successAnnouncement = "Remark deleted successfully"
                Toast.makeText(this@StudentProfileActivity, successAnnouncement, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(successAnnouncement)
                triggerHapticFeedback(true) // Slightly distinct vibe for deleting
            }
        }
    }

    private fun triggerHapticFeedback(longer: Boolean) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val ms = if (longer) 120L else 50L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    private fun showDeleteProfileConfirmationDialog() {
        val studentName = student?.name ?: "this student"
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Student Profile")
            .setMessage("Are you sure you want to delete '$studentName'? This will also remove them from the attendance register and update other students' roll numbers. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteProfileFromDb()
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Deletion cancelled.")
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Delete"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel"
        binding.root.announceForAccessibility("Warning dialog. Delete student profile '$studentName'? Select Delete or Cancel.")
    }

    private fun deleteProfileFromDb() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Try to find matching student roll number in roster
            var matchedRollNumber: Int? = null
            
            // Check dynamic roll number field first
            val rollFieldName = fields.find {
                val lower = it.fieldName.lowercase()
                lower.contains("roll") || lower == "sl. no" || lower == "sl no" || lower == "sl.no" || lower.contains("serial")
            }
            if (rollFieldName != null) {
                matchedRollNumber = rollFieldName.fieldValue.toDoubleOrNull()?.toInt() ?: rollFieldName.fieldValue.toIntOrNull()
            }
            
            // Fallback: Try via name match
            if (matchedRollNumber == null) {
                val rosterStudents = db.studentDao().getAllStudentsOnce(classId)
                val match = rosterStudents.find { it.name.trim().equals(student?.name?.trim(), ignoreCase = true) }
                if (match != null) {
                    matchedRollNumber = match.rollNumber
                }
            }

            // Perform deletion in database transaction
            db.withTransaction {
                // 1. Delete student profile tables
                db.studentProfileDao().deleteStudentProfile(classId, admissionNumber)
                db.studentProfileFieldDao().deleteStudentProfileFields(classId, admissionNumber)
                db.studentRemarkDao().deleteRemarksForStudent(classId, admissionNumber)
                
                // 2. Delete roster student and shift roll numbers if matched
                if (matchedRollNumber != null) {
                    db.studentDao().deleteStudent(classId, matchedRollNumber)
                    db.studentDao().shiftRollNumbers(classId, matchedRollNumber)
                    
                    db.attendanceDao().deleteAttendanceForStudent(classId, matchedRollNumber)
                    db.attendanceDao().shiftAttendanceRollNumbers(classId, matchedRollNumber)
                    
                    db.checklistDao().deleteChecklistRecordsForStudent(classId, matchedRollNumber)
                    db.checklistDao().shiftChecklistRollNumbers(classId, matchedRollNumber)

                    // Shift any dynamic roll number fields in student_profile_fields as well
                    val allClassFields = db.studentProfileFieldDao().getFieldsForClass(classId)
                    val rollFieldsToShift = allClassFields.filter { f ->
                        val lower = f.fieldName.lowercase()
                        (lower.contains("roll") || lower == "sl. no" || lower == "sl no" || lower == "sl.no" || lower.contains("serial"))
                    }
                    val updatedFields = mutableListOf<StudentProfileField>()
                    rollFieldsToShift.forEach { f ->
                        val rollVal = f.fieldValue.toDoubleOrNull()?.toInt() ?: f.fieldValue.toIntOrNull()
                        if (rollVal != null && rollVal > matchedRollNumber) {
                            updatedFields.add(f.copy(fieldValue = (rollVal - 1).toString()))
                        }
                    }
                    if (updatedFields.isNotEmpty()) {
                        db.studentProfileFieldDao().insertStudentProfileFields(updatedFields)
                    }
                }
            }

            runOnUiThread {
                val successAnnouncement = "Student profile deleted successfully"
                Toast.makeText(this@StudentProfileActivity, successAnnouncement, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(successAnnouncement)
                triggerHapticFeedback(true)
                finish()
            }
        }
    }
}
