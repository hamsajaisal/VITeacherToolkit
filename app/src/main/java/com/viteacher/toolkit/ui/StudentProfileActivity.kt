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
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
    }

    private fun loadStudentDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            student = db.studentProfileDao().getStudentProfile(classId, admissionNumber)
            fields = db.studentProfileFieldDao().getFieldsForStudent(classId, admissionNumber)
            remarks = db.studentRemarkDao().getRemarksForStudent(classId, admissionNumber)

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

        // 1. Find standard values for specific display/copy items
        var street = ""
        var postoffice = ""
        var pincode = ""

        fields.forEach { field ->
            val fieldName = field.fieldName.lowercase()
            val value = field.fieldValue

            if (value.isNotEmpty()) {
                // Track address fields for combined copy
                if (fieldName.contains("street")) street = value
                if (fieldName.contains("post")) postoffice = value
                if (fieldName.contains("pincode") || fieldName.contains("pin code")) pincode = value
                
                // Track phone number
                if (fieldName.contains("phone") || fieldName.contains("mobile")) {
                    parentPhoneNumber = value
                }

                // Render standard details row
                addDetailRow(field.fieldName, value)
            }
        }

        // 2. Render combined Full Address row if available
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
            setTextColor(ContextCompat.getColor(context, R.color.lavender))
            textSize = 14f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val tvValue = TextView(context).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        row.addView(tvLabel)
        row.addView(tvValue)

        // Determine if this field can be long pressed to copy
        val isCopyable = isCombinedAddress || when (label.lowercase()) {
            "admission no", "admission number", "phone number", "mobile number", "phone number/mobile number",
            "account no", "account number", "ifsc", "ifsc code", "father name", "father full name",
            "mother name", "mother full name" -> true
            else -> false
        }

        if (isCopyable) {
            row.contentDescription = "$label column: $value value. Double tap to focus, long press to copy to clipboard."
            
            row.setOnLongClickListener {
                copyToClipboard(label, value)
                true
            }
        } else {
            row.contentDescription = "$label column: $value value."
        }

        binding.layoutDetails.addView(row)
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
}
