package com.viteacher.toolkit.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Vibrator
import android.os.VibrationEffect
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.AttendanceRecord
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.data.Student
import com.viteacher.toolkit.data.StudentProfile
import com.viteacher.toolkit.data.StudentProfileField
import com.viteacher.toolkit.databinding.ActivityAttendanceRegisterBinding
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.apache.poi.ss.usermodel.HorizontalAlignment

class AttendanceRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceRegisterBinding
    private lateinit var studentAdapter: StudentAttendanceAdapter
    private var studentList: MutableList<StudentAttendanceItem> = mutableListOf()
    
    private var classId: Int = 1
    private var classroom: Classroom? = null
    private var selectedSession = "Forenoon" // "Forenoon", "Afternoon", "Daily", "Hour X"
    private val sessionAttendanceCache = mutableMapOf<String, MutableMap<Int, Boolean>>()
    
    private lateinit var displayDate: String  // e.g., "Friday, 22 May 2026"
    private lateinit var savedDate: String    // e.g., "22 May 2026"

    private val importExcelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                checkAndImportExcel(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)

        setupDate()
        loadClassroomSettings()
        setupRecyclerView()
        setupMenu()
        setupBottomButtons()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupDate() {
        val today = LocalDate.now()
        val screenFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
        val dbFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        
        displayDate = today.format(screenFormatter)
        savedDate = today.format(dbFormatter)

        binding.tvDate.text = displayDate
        binding.tvDate.contentDescription = "Today is $displayDate"
    }

    private fun loadClassroomSettings() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbClass = db.classroomDao().getClassroomById(classId)
            
            if (dbClass == null) {
                runOnUiThread {
                    Toast.makeText(this@AttendanceRegisterActivity, "Error: Classroom not found.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            classroom = dbClass
            runOnUiThread {
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val prefix = if (isCollege) "" else "Class "
                val classText = if (isCollege) "${dbClass.standard} ${dbClass.division}" else "${dbClass.standard}${dbClass.division}"
                binding.tvTitle.text = "$prefix$classText Register"
                binding.tvTitle.contentDescription = "$prefix$classText Attendance Register Screen"

                when (dbClass.attendanceType) {
                    "DoubleSession" -> {
                        selectedSession = "Forenoon"
                        binding.layoutDoubleSessionToggles.visibility = View.VISIBLE
                        binding.layoutHourSessionSelector.visibility = View.GONE
                        setupDoubleSessionToggles()
                    }
                    "OnceADay" -> {
                        selectedSession = "Daily"
                        binding.layoutDoubleSessionToggles.visibility = View.GONE
                        binding.layoutHourSessionSelector.visibility = View.GONE
                    }
                    "HourWise" -> {
                        selectedSession = "Hour 1"
                        binding.layoutDoubleSessionToggles.visibility = View.GONE
                        binding.layoutHourSessionSelector.visibility = View.VISIBLE
                        setupHourSessionSelector(dbClass.totalHours)
                    }
                }

                // Now load student list (once layouts are fully structured)
                loadStudentList()
            }
        }
    }

    private fun setupDoubleSessionToggles() {
        binding.btnForenoon.setOnClickListener {
            if (selectedSession != "Forenoon") {
                selectedSession = "Forenoon"
                updateDoubleSessionToggleUI()
                loadStudentListFromCache()
            }
        }

        binding.btnAfternoon.setOnClickListener {
            if (selectedSession != "Afternoon") {
                selectedSession = "Afternoon"
                updateDoubleSessionToggleUI()
                loadStudentListFromCache()
            }
        }

        updateDoubleSessionToggleUI()
    }

    private fun updateDoubleSessionToggleUI() {
        if (selectedSession == "Forenoon") {
            binding.btnForenoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.lavender_dark, theme))
            binding.btnForenoon.setTextColor(Color.BLACK)
            
            binding.btnAfternoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.grey_dark, theme))
            binding.btnAfternoon.setTextColor(Color.WHITE)

            binding.btnForenoon.isSelected = true
            binding.btnAfternoon.isSelected = false
            binding.btnForenoon.contentDescription = "Forenoon session, selected"
            binding.btnAfternoon.contentDescription = "Afternoon session, not selected"

            binding.root.announceForAccessibility("Forenoon session selected")
        } else {
            binding.btnAfternoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.lavender_dark, theme))
            binding.btnAfternoon.setTextColor(Color.BLACK)
            
            binding.btnForenoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.grey_dark, theme))
            binding.btnForenoon.setTextColor(Color.WHITE)

            binding.btnForenoon.isSelected = false
            binding.btnAfternoon.isSelected = true
            binding.btnForenoon.contentDescription = "Forenoon session, not selected"
            binding.btnAfternoon.contentDescription = "Afternoon session, selected"

            binding.root.announceForAccessibility("Afternoon session selected")
        }
    }

    private fun setupHourSessionSelector(totalHours: Int) {
        val hoursList = mutableListOf<String>()
        for (i in 1..totalHours) {
            hoursList.add("Hour $i")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hoursList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spSessionHour.adapter = adapter

        binding.spSessionHour.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hourKey = "Hour ${position + 1}"
                if (selectedSession != hourKey) {
                    selectedSession = hourKey
                    loadStudentListFromCache()
                    binding.root.announceForAccessibility("$hourKey selected")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadStudentListFromCache() {
        val cache = sessionAttendanceCache[selectedSession] ?: return
        studentList.forEach { item ->
            item.isPresent = cache[item.student.rollNumber] ?: true
        }
        studentAdapter.notifyDataSetChanged()
    }

    private fun setupRecyclerView() {
        studentAdapter = StudentAttendanceAdapter(studentList) { position, isPresent ->
            val item = studentList[position]
            
            // Sync with cache
            sessionAttendanceCache[selectedSession]?.put(item.student.rollNumber, isPresent)
            
            // Generate accessible announcement
            val statusAnnouncement = if (isPresent) "marked present" else "marked absent"
            val announcement = "${item.student.name} $statusAnnouncement"
            binding.root.announceForAccessibility(announcement)

            // Vibrate to provide tactile feedback
            triggerHapticFeedback()
        }
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

    private fun setupMenu() {
        binding.btnMoreOptions.setOnClickListener {
            val popup = PopupMenu(this, it)
            popup.menu.add("Import Student List")
            popup.menu.add("Download Sample Excel Template")
            popup.menu.add("Fetch from Class Profile")
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.title) {
                    "Import Student List" -> {
                        triggerImportStudentList()
                        true
                    }
                    "Download Sample Excel Template" -> {
                        triggerDownloadSampleTemplate()
                        true
                    }
                    "Fetch from Class Profile" -> {
                        triggerFetchFromClassProfile()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun triggerFetchFromClassProfile() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val profiles = db.studentProfileDao().getAllStudentProfiles(classId)
            
            runOnUiThread {
                if (profiles.isEmpty()) {
                    val msg = "No Class Profile records found for this class. Please import your Sampoorna Excel sheet first."
                    Toast.makeText(this@AttendanceRegisterActivity, msg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(msg)
                    return@runOnUiThread
                }

                AlertDialog.Builder(this@AttendanceRegisterActivity)
                    .setTitle("Fetch from Class Profile")
                    .setMessage("This will replace your current attendance roster with students from your Class Profile. Do you want to continue?")
                    .setPositiveButton("Yes") { _, _ ->
                        performFetchFromClassProfile()
                    }
                    .setNegativeButton("No") { d, _ ->
                        d.dismiss()
                        binding.root.announceForAccessibility("Fetch cancelled.")
                    }
                    .create()
                    .show()
                binding.root.announceForAccessibility("Warning dialog. Fetching from Class Profile will replace your current student list. Continue? Choose Yes or No.")
            }
        }
    }

    private fun performFetchFromClassProfile() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val profiles = db.studentProfileDao().getAllStudentProfiles(classId)
                val fields = db.studentProfileFieldDao().getFieldsForClass(classId)

                // Try to locate a column mapping representing roll number (case-insensitive checks)
                val rollFieldName = fields.map { it.fieldName }.distinct().find {
                    val lower = it.lowercase()
                    lower.contains("roll") || lower == "sl. no" || lower == "sl no" || lower == "sl.no" || lower.contains("serial")
                }

                val studentsToInsert = mutableListOf<Student>()
                if (rollFieldName != null) {
                    val studentRolls = mutableListOf<Pair<StudentProfile, Int>>()
                    var maxRoll = 0
                    profiles.forEach { profile ->
                        val fieldVal = fields.find { it.admissionNumber == profile.admissionNumber && it.fieldName == rollFieldName }?.fieldValue ?: ""
                        val parsedRoll = fieldVal.toDoubleOrNull()?.toInt() ?: fieldVal.toIntOrNull()
                        if (parsedRoll != null && parsedRoll > 0) {
                            studentRolls.add(Pair(profile, parsedRoll))
                            if (parsedRoll > maxRoll) maxRoll = parsedRoll
                        } else {
                            studentRolls.add(Pair(profile, -1))
                        }
                    }

                    // Resolve missing roll numbers sequentially
                    var fallbackRoll = maxRoll + 1
                    val resolvedList = studentRolls.map { (profile, roll) ->
                        val finalRoll = if (roll == -1) fallbackRoll++ else roll
                        Student(classId = classId, rollNumber = finalRoll, name = profile.name)
                    }

                    studentsToInsert.addAll(resolvedList.sortedBy { it.rollNumber })
                } else {
                    // Fallback to alphabetical sorting by student name
                    val sortedProfiles = profiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    sortedProfiles.forEachIndexed { index, profile ->
                        studentsToInsert.add(Student(classId = classId, rollNumber = index + 1, name = profile.name))
                    }
                }

                db.studentDao().deleteAllStudents(classId)
                db.studentDao().insertStudents(studentsToInsert)

                runOnUiThread {
                    val successMsg = "${studentsToInsert.size} students loaded from Class Profile successfully."
                    Toast.makeText(this@AttendanceRegisterActivity, successMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(successMsg)
                    loadStudentList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    val errMsg = "Failed to fetch from Class Profile: ${e.message}"
                    Toast.makeText(this@AttendanceRegisterActivity, errMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(errMsg)
                }
            }
        }
    }

    private fun loadStudentList() {
        val type = classroom?.attendanceType ?: "DoubleSession"
        val hoursLimit = classroom?.totalHours ?: 0

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbStudents = db.studentDao().getAllStudentsOnce(classId)
            
            // Load existing attendance records for today from DB
            val existingRecords = db.attendanceDao().getAttendanceForDateAndClass(classId, savedDate)
            
            runOnUiThread {
                studentList.clear()
                if (dbStudents.isEmpty()) {
                    binding.rvStudents.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    
                    // Onboarding vocal guidelines for visually impaired teachers
                    val voiceGuide = "No students found in your register. To add your students manually, tap 'Add or Edit Student Roster' on the previous screen. Alternatively, tap the 'More Options' button at the top-right of this screen to download an Excel sheet roster template and upload it."
                    binding.root.announceForAccessibility(voiceGuide)
                } else {
                    binding.rvStudents.visibility = View.VISIBLE
                    binding.tvEmptyState.visibility = View.GONE
                    
                    // Initialize cache maps for all available sessions
                    sessionAttendanceCache.clear()
                    when (type) {
                        "DoubleSession" -> {
                            sessionAttendanceCache["Forenoon"] = mutableMapOf()
                            sessionAttendanceCache["Afternoon"] = mutableMapOf()
                        }
                        "OnceADay" -> {
                            sessionAttendanceCache["Daily"] = mutableMapOf()
                        }
                        "HourWise" -> {
                            for (i in 1..hoursLimit) {
                                sessionAttendanceCache["Hour $i"] = mutableMapOf()
                            }
                        }
                    }
                    
                    dbStudents.forEach { student ->
                        sessionAttendanceCache.keys.forEach { sessionKey ->
                            val record = existingRecords.find { it.rollNumber == student.rollNumber && it.session == sessionKey }
                            val isPresent = record?.isPresent ?: true
                            sessionAttendanceCache[sessionKey]?.put(student.rollNumber, isPresent)
                        }

                        val currentPresent = sessionAttendanceCache[selectedSession]?.get(student.rollNumber) ?: true
                        studentList.add(StudentAttendanceItem(student, isPresent = currentPresent))
                    }
                    studentAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupBottomButtons() {
        binding.btnSaveAttendance.setOnClickListener {
            saveAttendanceFlow()
        }

        binding.btnShareAbsentees.setOnClickListener {
            shareAbsenteesFlow()
        }

        binding.btnViewHistory.setOnClickListener {
            val intent = Intent(this, AttendanceHistoryActivity::class.java).apply {
                putExtra("class_id", classId)
            }
            startActivity(intent)
        }
    }

    private fun triggerImportStudentList() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val currentStudents = db.studentDao().getAllStudentsOnce(classId)
            runOnUiThread {
                if (currentStudents.isNotEmpty()) {
                    val dialog = AlertDialog.Builder(this@AttendanceRegisterActivity)
                        .setTitle("Replace Student List")
                        .setMessage("This will replace your existing student list. Do you want to continue?")
                        .setPositiveButton("Yes") { _, _ ->
                            launchFilePicker()
                        }
                        .setNegativeButton("No") { d, _ ->
                            d.dismiss()
                            binding.root.announceForAccessibility("Import cancelled. Existing student list kept.")
                        }
                        .create()
                    dialog.show()
                    
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No"
                    binding.root.announceForAccessibility("Warning dialog. This will replace your existing student list. Do you want to continue? Select Yes or No.")
                } else {
                    launchFilePicker()
                }
            }
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        importExcelLauncher.launch(intent)
        binding.root.announceForAccessibility("File picker opened. Select an Excel file in xlsx format.")
    }

    private fun checkAndImportExcel(uri: Uri) {
        lifecycleScope.launch {
            var inputStream: InputStream? = null
            try {
                inputStream = contentResolver.openInputStream(uri)
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)
                
                val parsedStudents = mutableListOf<Student>()
                val rowIterator = sheet.rowIterator()
                
                // Skip the first row (headers)
                if (rowIterator.hasNext()) rowIterator.next()
                
                while (rowIterator.hasNext()) {
                    val row = rowIterator.next()
                    val cellA = row.getCell(0)
                    val cellB = row.getCell(1)
                    
                    if (cellA != null && cellB != null) {
                        try {
                            val rollNum: Int
                            if (cellA.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                                rollNum = cellA.numericCellValue.toInt()
                            } else {
                                val text = cellA.stringCellValue.trim()
                                if (text.isEmpty() || text.contains("Fill student details", ignoreCase = true) || text.contains("Roll", ignoreCase = true)) {
                                    continue
                                }
                                rollNum = text.toInt()
                            }
                            
                            val name = cellB.stringCellValue.trim()
                            if (name.isNotEmpty()) {
                                parsedStudents.add(Student(classId = classId, rollNumber = rollNum, name = name))
                            }
                        } catch (e: Exception) {
                            // Skip invalid rows
                        }
                    }
                }
                
                workbook.close()
                
                if (parsedStudents.isEmpty()) {
                    throw Exception("No valid students found in the file.")
                }
                
                val db = AppDatabase.getDatabase(applicationContext)
                db.studentDao().deleteAllStudents(classId)
                db.studentDao().insertStudents(parsedStudents)
                
                runOnUiThread {
                    val successMsg = "${parsedStudents.size} students imported successfully."
                    Toast.makeText(this@AttendanceRegisterActivity, successMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(successMsg)
                    loadStudentList()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    val errMsg = "The file could not be read. Please make sure you are using the correct Excel template format."
                    Toast.makeText(this@AttendanceRegisterActivity, errMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(errMsg)
                }
            } finally {
                try {
                    inputStream?.close()
                } catch (_: IOException) {}
            }
        }
    }

    private fun triggerDownloadSampleTemplate() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Students")
        
        val headerFont = workbook.createFont().apply {
            bold = true
        }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            alignment = HorizontalAlignment.LEFT
        }
        
        val headerRow = sheet.createRow(0)
        val cellA1 = headerRow.createCell(0)
        cellA1.setCellValue("Roll Number")
        cellA1.setCellStyle(headerStyle)
        
        val cellB1 = headerRow.createCell(1)
        cellB1.setCellValue("Name")
        cellB1.setCellStyle(headerStyle)
        
        val instructionRow = sheet.createRow(1)
        val instructionCell = instructionRow.createCell(0)
        instructionCell.setCellValue("Fill student details from this row onwards. Do not change the heading row.")
        
        val sample1 = sheet.createRow(2)
        sample1.createCell(0).setCellValue(1.0)
        sample1.createCell(1).setCellValue("Sample Student 1")
        
        val sample2 = sheet.createRow(3)
        sample2.createCell(0).setCellValue(2.0)
        sample2.createCell(1).setCellValue("Sample Student 2")
        
        val sample3 = sheet.createRow(4)
        sample3.createCell(0).setCellValue(3.0)
        sample3.createCell(1).setCellValue("Sample Student 3")
        
        sheet.setColumnWidth(0, 4000)
        sheet.setColumnWidth(1, 6000)
        
        saveTemplateToDownloads(workbook)
    }

    private fun saveTemplateToDownloads(workbook: XSSFWorkbook) {
        val filename = "StudentListTemplate.xlsx"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { output ->
                        if (output != null) {
                            workbook.write(output)
                        }
                    }
                    val msg = "Sample template saved to your Downloads folder."
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(msg)
                } else {
                    throw IOException("Failed to create MediaStore file entry.")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, filename)
                FileOutputStream(file).use { output ->
                    workbook.write(output)
                }
                val msg = "Sample template saved to your Downloads folder."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errMsg = "Failed to download template. Please check permissions."
            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show()
            binding.root.announceForAccessibility(errMsg)
        } finally {
            try {
                workbook.close()
            } catch (_: IOException) {}
        }
    }

    private fun saveAttendanceFlow() {
        if (studentList.isEmpty()) {
            val emptyMsg = "No students in the list. Please setup your student roster first."
            Toast.makeText(this, emptyMsg, Toast.LENGTH_LONG).show()
            binding.root.announceForAccessibility(emptyMsg)
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val existing = db.attendanceDao().getAttendanceForDateAndSession(classId, savedDate, selectedSession)
            
            runOnUiThread {
                if (existing.isNotEmpty()) {
                    val dialog = AlertDialog.Builder(this@AttendanceRegisterActivity)
                        .setTitle("Overwrite Attendance")
                        .setMessage("$selectedSession attendance for today is already saved. Do you want to overwrite it?")
                        .setPositiveButton("Yes") { _, _ ->
                            performSaveAttendance()
                        }
                        .setNegativeButton("No") { d, _ ->
                            d.dismiss()
                            binding.root.announceForAccessibility("Save cancelled.")
                        }
                        .create()
                    dialog.show()

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No"
                    binding.root.announceForAccessibility("Warning dialog. $selectedSession attendance for today is already saved. Do you want to overwrite it? Select Yes or No.")
                } else {
                    performSaveAttendance()
                }
            }
        }
    }

    private fun performSaveAttendance() {
        lifecycleScope.launch {
            val records = studentList.map {
                AttendanceRecord(
                    classId = classId,
                    date = savedDate,
                    session = selectedSession,
                    rollNumber = it.student.rollNumber,
                    name = it.student.name,
                    isPresent = it.isPresent
                )
            }
            
            val db = AppDatabase.getDatabase(applicationContext)
            db.attendanceDao().insertAttendanceRecords(records)
            
            val totalAbsent = studentList.count { !it.isPresent }
            val totalPresent = studentList.count { it.isPresent }
            
            runOnUiThread {
                val announceSuccess = "Attendance saved successfully"
                val announceSummary = "$selectedSession attendance saved. Present: $totalPresent, Absent: $totalAbsent."
                
                Toast.makeText(this@AttendanceRegisterActivity, announceSummary, Toast.LENGTH_LONG).show()
                binding.root.announceForAccessibility("$announceSuccess. $announceSummary")
            }
        }
    }

    private fun shareAbsenteesFlow() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val records = db.attendanceDao().getAttendanceForDateAndSession(classId, savedDate, selectedSession)
            
            runOnUiThread {
                if (records.isEmpty()) {
                    val msg = "Please save attendance before sharing."
                    Toast.makeText(this@AttendanceRegisterActivity, msg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(msg)
                    return@runOnUiThread
                }
                
                val absentees = records.filter { !it.isPresent }
                val totalAbsent = absentees.size
                val totalPresent = records.size - totalAbsent
                
                val message = StringBuilder()
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val className = if (classroom != null) {
                    if (isCollege) "${classroom!!.standard} ${classroom!!.division}" else "Class ${classroom!!.standard}${classroom!!.division}"
                } else "My Class"

                if (totalAbsent == 0) {
                    message.append("No absentees for $className on $savedDate during $selectedSession. Full attendance present.")
                } else {
                    message.append("$className Absentees Report\n")
                    message.append("Date: $savedDate\n")
                    message.append("Session/Period: $selectedSession\n\n")
                    
                    absentees.forEach {
                        message.append("Roll No. ${it.rollNumber} — ${it.name}\n")
                    }
                    
                    message.append("\nTotal Absent: $totalAbsent\n")
                    message.append("Total Present: $totalPresent")
                }
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, message.toString())
                    type = "text/plain"
                }
                
                val chooser = Intent.createChooser(shareIntent, "Share Absentees Report")
                startActivity(chooser)
                binding.root.announceForAccessibility("Opening share menu for absentees report.")
            }
        }
    }
}

// Data class for managing list items state in memory
data class StudentAttendanceItem(
    val student: Student,
    var isPresent: Boolean
)

// Custom Recycler View Adapter for Student Attendance
class StudentAttendanceAdapter(
    private val list: List<StudentAttendanceItem>,
    private val onItemClick: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvRoll: TextView = view.findViewById(R.id.tvRollNumber)
        val tvName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStatus: TextView = view.findViewById(R.id.tvAttendanceStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_student_attendance, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvRoll.text = item.student.rollNumber.toString()
        holder.tvName.text = item.student.name
        
        updateViewHolderUI(holder, item)

        holder.view.setOnClickListener {
            item.isPresent = !item.isPresent
            updateViewHolderUI(holder, item)
            onItemClick(position, item.isPresent)
        }
    }

    private fun updateViewHolderUI(holder: ViewHolder, item: StudentAttendanceItem) {
        if (item.isPresent) {
            holder.tvStatus.text = "Present"
            holder.tvStatus.setTextColor(Color.parseColor("#FFCCCCCC")) // light grey
            holder.view.setBackgroundColor(Color.BLACK)
            
            // Set content description for TalkBack
            holder.view.contentDescription = "Roll number ${item.student.rollNumber}, ${item.student.name}, Present"
        } else {
            holder.tvStatus.text = "Absent"
            holder.tvStatus.setTextColor(Color.RED)
            holder.view.setBackgroundColor(Color.parseColor("#FF5C0000")) // dark red background
            
            // Set content description for TalkBack
            holder.view.contentDescription = "Roll number ${item.student.rollNumber}, ${item.student.name}, Absent"
        }
    }

    override fun getItemCount(): Int = list.size
}
