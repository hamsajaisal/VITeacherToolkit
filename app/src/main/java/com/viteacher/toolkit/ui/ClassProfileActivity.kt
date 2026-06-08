package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.data.StudentProfile
import com.viteacher.toolkit.data.StudentProfileField
import com.viteacher.toolkit.databinding.ActivityClassProfileBinding
import com.viteacher.toolkit.util.ExcelParser
import kotlinx.coroutines.launch
import java.util.Locale

class ClassProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassProfileBinding
    private var classId: Int = 1
    private var classroom: Classroom? = null
    
    // In-memory student records
    private var allStudents = listOf<StudentProfile>()
    private var allFields = listOf<StudentProfileField>()
    private var displayedStudents = mutableListOf<StudentProfile>()
    
    private lateinit var studentAdapter: StudentAdapter
    private var activeFilterColumn: String? = null
    private var activeFilterValue: String? = null
    private var activeSortColumn: String? = null
    private var activeSortAscending: Boolean = true

    // Activity Result Launcher for importing Excel documents
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            performImport(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)

        setupUI()
        loadClassroomDetails()
        loadStudentData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMore.setOnClickListener {
            showMoreOptions()
        }

        binding.btnClearFilter.setOnClickListener {
            clearActiveFilter()
        }

        binding.btnShareStudentList.setOnClickListener {
            if (displayedStudents.isEmpty()) {
                val errorMsg = "No student data to share"
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(errorMsg)
            } else {
                showShareChecklistDialog()
            }
        }

        studentAdapter = StudentAdapter(displayedStudents) { student ->
            val intent = Intent(this, StudentProfileActivity::class.java).apply {
                putExtra("class_id", classId)
                putExtra("admission_number", student.admissionNumber)
            }
            startActivity(intent)
        }

        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter = studentAdapter
    }

    private fun loadClassroomDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            classroom = db.classroomDao().getClassroomById(classId)
            
            runOnUiThread {
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val prefix = if (isCollege) "" else "Class "
                
                val standard = classroom?.standard ?: ""
                val division = classroom?.division ?: ""
                val academicYear = classroom?.academicYear ?: ""
                val classText = if (isCollege) "$standard $division" else "$standard$division"
                val display = "$prefix$classText — $academicYear"
                binding.tvClassDetails.text = display
                
                // Content description for screen reader
                val spokenYear = academicYear.replace("-", " to ")
                binding.tvClassDetails.contentDescription = "$prefix$classText, Academic Year $spokenYear"
            }
        }
    }

    private fun loadStudentData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            allStudents = db.studentProfileDao().getAllStudentProfiles(classId)
            allFields = db.studentProfileFieldDao().getFieldsForClass(classId)
            
            runOnUiThread {
                applyFilterAndSort()
            }
        }
    }

    private fun applyFilterAndSort() {
        var filteredList = allStudents.toList()
        
        // 1. Apply Filter
        val filterCol = activeFilterColumn
        val filterVal = activeFilterValue
        if (filterCol != null && filterVal != null) {
            val matchingAdms = allFields.filter { 
                it.fieldName.lowercase() == filterCol.lowercase() && 
                it.fieldValue.lowercase() == filterVal.lowercase() 
            }.map { it.admissionNumber }.toSet()
            
            filteredList = filteredList.filter { matchingAdms.contains(it.admissionNumber) }
            
            val filterText = "Showing: $filterCol — $filterVal (${filteredList.size} students)"
            binding.tvActiveFilter.text = filterText
            binding.tvActiveFilter.contentDescription = filterText
            binding.tvActiveFilter.visibility = View.VISIBLE
            binding.btnClearFilter.visibility = View.VISIBLE
        } else {
            binding.tvActiveFilter.visibility = View.GONE
            binding.btnClearFilter.visibility = View.GONE
        }

        // 2. Apply Sort
        val sortCol = activeSortColumn
        if (sortCol != null) {
            val sortAsc = activeSortAscending
            val fieldsMap = allFields.filter { it.fieldName.lowercase() == sortCol.lowercase() }
                .associateBy { it.admissionNumber }
                
            filteredList = filteredList.sortedWith { s1, s2 ->
                val v1 = fieldsMap[s1.admissionNumber]?.fieldValue ?: ""
                val v2 = fieldsMap[s2.admissionNumber]?.fieldValue ?: ""
                
                val d1 = v1.toDoubleOrNull()
                val d2 = v2.toDoubleOrNull()
                val comp = if (d1 != null && d2 != null) {
                    d1.compareTo(d2)
                } else {
                    v1.lowercase(Locale.getDefault()).compareTo(v2.lowercase(Locale.getDefault()))
                }
                if (sortAsc) comp else -comp
            }
        } else {
            // Default sort by Name
            filteredList = filteredList.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }

        // 3. Update Display
        displayedStudents.clear()
        displayedStudents.addAll(filteredList)
        studentAdapter.notifyDataSetChanged()

        if (allStudents.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvStudents.visibility = View.GONE
            binding.btnShareStudentList.isEnabled = false
            binding.btnShareStudentList.contentDescription = "Share student list button, disabled, please import student data first"
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvStudents.visibility = View.VISIBLE
            binding.btnShareStudentList.isEnabled = true
            binding.btnShareStudentList.contentDescription = "Share student list"
        }
    }

    private fun showMoreOptions() {
        val popup = PopupMenu(this, binding.btnMore)
        popup.menu.add(0, 1, 0, "Import Student Data")
        popup.menu.add(0, 2, 1, "Sort or Filter Students")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    triggerImportFlow()
                    true
                }
                2 -> {
                    if (allStudents.isEmpty()) {
                        val msg = "Please import student data first"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        binding.root.announceForAccessibility(msg)
                    } else {
                        showFilterSortOptionsDialog()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun triggerImportFlow() {
        if (allStudents.isNotEmpty()) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Import Student Data")
                .setMessage("This will replace your existing student data. Do you want to continue?")
                .setPositiveButton("Yes") { _, _ ->
                    startFilePicker()
                }
                .setNegativeButton("No") { d, _ ->
                    d.dismiss()
                    binding.root.announceForAccessibility("Import cancelled.")
                }
                .create()
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No"
            binding.root.announceForAccessibility("Warning dialog. This will replace your existing student data. Do you want to continue? Select Yes or No.")
        } else {
            startFilePicker()
        }
    }

    private fun startFilePicker() {
        // Open document picker for XLS and XLSX
        importLauncher.launch(
            arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "*/*"
            )
        )
        binding.root.announceForAccessibility("File picker opened. Select the Sampoorna Excel file.")
    }

    private fun performImport(uri: Uri) {
        lifecycleScope.launch {
            binding.root.announceForAccessibility("Reading Excel file. Please wait.")
            val result = ExcelParser.importExcelData(this@ClassProfileActivity, classId, uri)
            
            runOnUiThread {
                if (result.successCount > 0) {
                    val successMsg = "${result.successCount} students imported successfully."
                    Toast.makeText(this@ClassProfileActivity, successMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(successMsg)
                    
                    // Reset filters
                    activeFilterColumn = null
                    activeFilterValue = null
                    activeSortColumn = null
                    
                    loadStudentData()
                } else {
                    val errorMsg = result.errorMessage ?: "The file could not be read. Please make sure you are using an Excel file downloaded from the Sampoorna portal."
                    Toast.makeText(this@ClassProfileActivity, errorMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(errorMsg)
                }
            }
        }
    }

    private fun showFilterSortOptionsDialog() {
        val uniqueColumns = allFields.map { it.fieldName }.distinct().sorted()
        
        val options = arrayOf("Filter Students", "Sort Students")
        AlertDialog.Builder(this)
            .setTitle("Sort or Filter Students")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showFilterColumnSelection(uniqueColumns)
                } else {
                    showSortColumnSelection(uniqueColumns)
                }
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Dialog opened. Select Filter Students or Sort Students.")
    }

    private fun showFilterColumnSelection(columns: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Filter by Column")
            .setItems(columns.toTypedArray()) { _, which ->
                val selectedCol = columns[which]
                showFilterValueSelection(selectedCol)
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Select a column to filter by.")
    }

    private fun showFilterValueSelection(column: String) {
        val uniqueValues = allFields.filter { it.fieldName.lowercase() == column.lowercase() }
            .map { it.fieldValue }
            .distinct()
            .sorted()
            
        AlertDialog.Builder(this)
            .setTitle("Select value for $column")
            .setItems(uniqueValues.toTypedArray()) { _, which ->
                val selectedVal = uniqueValues[which]
                activeFilterColumn = column
                activeFilterValue = selectedVal
                
                applyFilterAndSort()
                
                val announceMsg = "Filter applied: showing only students where $column is $selectedVal. Total matches: ${displayedStudents.size}."
                binding.root.announceForAccessibility(announceMsg)
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Select a value to filter by.")
    }

    private fun showSortColumnSelection(columns: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Sort by Column")
            .setItems(columns.toTypedArray()) { _, which ->
                val selectedCol = columns[which]
                showSortDirectionSelection(selectedCol)
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Select a column to sort by.")
    }

    private fun showSortDirectionSelection(column: String) {
        val directions = arrayOf("Ascending Order", "Descending Order")
        AlertDialog.Builder(this)
            .setTitle("Sort Direction for $column")
            .setItems(directions) { _, which ->
                activeSortColumn = column
                activeSortAscending = (which == 0)
                
                applyFilterAndSort()
                
                val directionText = if (which == 0) "ascending" else "descending"
                val announceMsg = "Sorted roster by $column in $directionText order."
                binding.root.announceForAccessibility(announceMsg)
            }
            .create()
            .show()
            
        binding.root.announceForAccessibility("Select sorting order: Ascending or Descending.")
    }

    private fun clearActiveFilter() {
        activeFilterColumn = null
        activeFilterValue = null
        applyFilterAndSort()
        
        val announceMsg = "Filter cleared. Now showing all ${displayedStudents.size} students."
        binding.root.announceForAccessibility(announceMsg)
        Toast.makeText(this, "Filter cleared", Toast.LENGTH_SHORT).show()
    }

    private fun showShareChecklistDialog() {
        val uniqueColumns = allFields.map { it.fieldName }.distinct().sorted()
        
        // Checklist container
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val scroll = ScrollView(context).apply {
            addView(container)
        }

        // Header with Select All / Deselect All
        val buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.height = (56 * resources.displayMetrics.density).toInt()
        }

        val checkboxList = mutableListOf<CheckBox>()

        val btnSelectAll = Button(context).apply {
            text = "Select All"
            minHeight = (48 * resources.displayMetrics.density).toInt()
            contentDescription = "Select all fields"
            setOnClickListener {
                checkboxList.forEach { it.isChecked = true }
                announceForAccessibility("All fields selected.")
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val btnDeselectAll = Button(context).apply {
            text = "Deselect All"
            minHeight = (48 * resources.displayMetrics.density).toInt()
            contentDescription = "Deselect all fields"
            setOnClickListener {
                checkboxList.forEach { it.isChecked = false }
                announceForAccessibility("All fields deselected.")
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        buttonBar.addView(btnSelectAll)
        buttonBar.addView(btnDeselectAll)
        container.addView(buttonBar)

        // Add Only Basic details option checkbox
        val cbOnlyBasic = CheckBox(context).apply {
            text = "Only share basic details (Name and Admission Number)"
            isChecked = false
            minHeight = (48 * resources.displayMetrics.density).toInt()
            contentDescription = "Only share basic details checkbox"
        }
        container.addView(cbOnlyBasic)

        // Add standard fields
        val stdCheckboxes = listOf("Admission Number", "Full Name")
        stdCheckboxes.forEach { name ->
            val cb = CheckBox(context).apply {
                text = name
                isChecked = true // Checked by default
                minHeight = (48 * resources.displayMetrics.density).toInt()
                contentDescription = "Include $name checkbox"
            }
            checkboxList.add(cb)
            container.addView(cb)
        }

        // Add dynamic fields
        uniqueColumns.forEach { colName ->
            // Skip name / admission duplicates in dynamic attributes if matching
            val lower = colName.lowercase()
            if (!lower.contains("name") && !lower.contains("admission")) {
                val cb = CheckBox(context).apply {
                    text = colName
                    isChecked = false // Unchecked by default
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    contentDescription = "Include $colName checkbox"
                }
                checkboxList.add(cb)
                container.addView(cb)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Share Student List Details")
            .setView(scroll)
            .setPositiveButton("Share") { _, _ ->
                val selectedFields = checkboxList.filter { it.isChecked }.map { it.text.toString() }
                val onlyBasic = cbOnlyBasic.isChecked
                if (selectedFields.isEmpty() && !onlyBasic) {
                    Toast.makeText(context, "Please select at least one field to share.", Toast.LENGTH_SHORT).show()
                } else {
                    dispatchShare(selectedFields, onlyBasic)
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Share cancelled.")
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Share"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "Cancel"
        binding.root.announceForAccessibility("Share checklist dialog opened. Use Select All or Deselect All, check options, then choose Share or Cancel.")
    }

    private fun dispatchShare(fields: List<String>, onlyBasic: Boolean) {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        val prefix = if (isCollege) "" else "Class "
        
        val standard = classroom?.standard ?: ""
        val division = classroom?.division ?: ""
        val classText = if (isCollege) "$standard $division" else "$standard$division"
        val displayText = "$prefix$classText"
        
        val builder = java.lang.StringBuilder()
        builder.append("$displayText — Student List\n")
        
        val filterCol = activeFilterColumn
        val filterVal = activeFilterValue
        if (filterCol != null && filterVal != null) {
            builder.append("Filter: $filterCol — $filterVal\n")
        }
        
        builder.append("Total Students: ${displayedStudents.size}\n\n")

        // Map dynamic attributes per student for rapid fetching
        val dynamicFieldsMap = allFields.groupBy { it.admissionNumber }

        displayedStudents.forEachIndexed { index, student ->
            builder.append("${index + 1}. ")
            
            val detailsList = mutableListOf<String>()
            
            if (onlyBasic) {
                detailsList.add("Admission No: ${student.admissionNumber}")
                detailsList.add("Name: ${student.name}")
            } else {
                fields.forEach { field ->
                    when (field) {
                        "Admission Number" -> detailsList.add("Admission No: ${student.admissionNumber}")
                        "Full Name" -> detailsList.add("Name: ${student.name}")
                        else -> {
                            val value = dynamicFieldsMap[student.admissionNumber]?.firstOrNull { it.fieldName.lowercase() == field.lowercase() }?.fieldValue ?: "N/A"
                            detailsList.add("$field: $value")
                        }
                    }
                }
            }
            
            builder.append(detailsList.joinToString("\n   "))
            builder.append("\n\n")
        }

        val shareMessage = builder.toString().trim()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Student List via"))
    }

    // Inner Adapter Class
    class StudentAdapter(
        private val list: List<StudentProfile>,
        private val onClick: (StudentProfile) -> Unit
    ) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val tvIndex: TextView = view.findViewById(R.id.tvRosterIndex)
            val tvName: TextView = view.findViewById(R.id.tvStudentName)
            val tvAdm: TextView = view.findViewById(R.id.tvStudentAdmission)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_student_row, parent, false)
            return ViewHolder(layout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val student = list[position]
            holder.tvIndex.text = (position + 1).toString()
            holder.tvName.text = student.name
            holder.tvAdm.text = "Admission Number: ${student.admissionNumber}"
            
            // Set dynamic content description for TalkBack traversal
            holder.view.contentDescription = "Admission number ${student.admissionNumber}, ${student.name}"
            
            holder.view.setOnClickListener {
                onClick(student)
            }
            
            // Accessibility swiping helper
            holder.view.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun sendAccessibilityEvent(host: View, eventType: Int) {
                    super.sendAccessibilityEvent(host, eventType)
                    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                        // Can pre-announce if needed, or rely on standard TalkBack contentDescription
                    }
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
