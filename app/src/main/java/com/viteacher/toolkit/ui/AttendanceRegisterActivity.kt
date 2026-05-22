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
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.AttendanceRecord
import com.viteacher.toolkit.data.Student
import com.viteacher.toolkit.databinding.ActivityAttendanceRegisterBinding
import kotlinx.coroutines.flow.first
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
    private var selectedSession = "Forenoon" // "Forenoon" or "Afternoon"
    
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

        setupDate()
        setupSessionToggles()
        setupRecyclerView()
        setupMenu()
        setupBottomButtons()

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Fetch students from local database
        loadStudentList()
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

    private fun setupSessionToggles() {
        binding.btnForenoon.setOnClickListener {
            if (selectedSession != "Forenoon") {
                selectedSession = "Forenoon"
                updateSessionToggleUI()
            }
        }

        binding.btnAfternoon.setOnClickListener {
            if (selectedSession != "Afternoon") {
                selectedSession = "Afternoon"
                updateSessionToggleUI()
            }
        }

        updateSessionToggleUI()
    }

    private fun updateSessionToggleUI() {
        if (selectedSession == "Forenoon") {
            // Visual highlight for Forenoon (lavender background, black text)
            binding.btnForenoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.lavender_dark, theme))
            binding.btnForenoon.setTextColor(Color.BLACK)
            
            // Inactive style for Afternoon (dark grey background, white text)
            binding.btnAfternoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.grey_dark, theme))
            binding.btnAfternoon.setTextColor(Color.WHITE)

            binding.root.announceForAccessibility("Forenoon session selected")
        } else {
            // Visual highlight for Afternoon (lavender background, black text)
            binding.btnAfternoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.lavender_dark, theme))
            binding.btnAfternoon.setTextColor(Color.BLACK)
            
            // Inactive style for Forenoon (dark grey background, white text)
            binding.btnForenoon.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.grey_dark, theme))
            binding.btnForenoon.setTextColor(Color.WHITE)

            binding.root.announceForAccessibility("Afternoon session selected")
        }
    }

    private fun setupRecyclerView() {
        studentAdapter = StudentAttendanceAdapter(studentList) { position ->
            toggleStudentAttendance(position)
        }
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter = studentAdapter
    }

    private fun toggleStudentAttendance(position: Int) {
        val item = studentList[position]
        item.isPresent = !item.isPresent
        studentAdapter.notifyItemChanged(position)

        // Generate accessible announcement
        val statusAnnouncement = if (item.isPresent) "marked present" else "marked absent"
        val announcement = "${item.student.name} $statusAnnouncement"
        binding.root.announceForAccessibility(announcement)

        // Vibrate to provide haptic feedback
        triggerHapticFeedback()
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
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun loadStudentList() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbStudents = db.studentDao().getAllStudentsOnce()
            runOnUiThread {
                studentList.clear()
                if (dbStudents.isEmpty()) {
                    binding.rvStudents.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                    
                    // Voice guide for visually impaired teachers
                    val voiceGuide = "No students found in your register. To add your students, tap the 'More Options' button at the top-right of your screen. You can select 'Download Sample Excel Template' to save a pre-formatted spreadsheet to your Downloads folder. Fill in your student roll numbers and names in that sheet, and then select 'Import Student List' to upload it."
                    binding.root.announceForAccessibility(voiceGuide)
                } else {
                    binding.rvStudents.visibility = View.VISIBLE
                    binding.tvEmptyState.visibility = View.GONE
                    dbStudents.forEach { studentList.add(StudentAttendanceItem(it, isPresent = true)) }
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
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }
    }

    // PART 5 - IMPORT EXCEL STUDENT LIST
    private fun triggerImportStudentList() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val currentStudents = db.studentDao().getAllStudentsOnce()
            runOnUiThread {
                if (currentStudents.isNotEmpty()) {
                    // Show replacement warning dialog
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
                    
                    // Set custom button content descriptions inside dialog
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes button"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No button"
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
                
                // Read rows starting from row 1 (Row 0 is title row, Row 1 is description note, student data starts from Row 2)
                // Wait! Let's check Excel format requirement:
                // "Row 1 is heading row with Roll Number in A and Name in B. From Row 2 onwards, each row contains roll number in A and name in B."
                // Remember POI row indexing is 0-based. So Row 1 of template is index 0. Row 2 of template is index 1 (which holds the instruction note).
                // Wait! Let's check: "Row 1 is heading. From Row 2 onwards, each row contains student details." But wait, in the template:
                // "Row 2: A note or instruction written in column A. Rows 3 to 5: Three sample rows with example data."
                // So if the template has the instruction note in row 2, and student details starting from row 3 (index 2), does the import logic check for the note?
                // Let's handle it gracefully: if column A is not an integer or is an instruction note, we skip it! That way, we support both templates (with/without the note row)!
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
                                // If this cell contains instructions or empty text, skip
                                if (text.isEmpty() || text.contains("Fill student details", ignoreCase = true) || text.contains("Roll", ignoreCase = true)) {
                                    continue
                                }
                                rollNum = text.toInt()
                            }
                            
                            val name = cellB.stringCellValue.trim()
                            if (name.isNotEmpty()) {
                                parsedStudents.add(Student(rollNum, name))
                            }
                        } catch (e: Exception) {
                            // Skip rows that fail to parse (e.g. instruction note row)
                        }
                    }
                }
                
                workbook.close()
                
                if (parsedStudents.isEmpty()) {
                    throw Exception("No valid students found in the file.")
                }
                
                // Save to DB
                val db = AppDatabase.getDatabase(applicationContext)
                db.studentDao().deleteAllStudents()
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

    // PART 6 - DOWNLOAD SAMPLE EXCEL TEMPLATE
    private fun triggerDownloadSampleTemplate() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Students")
        
        // Row 0: Headers (Roll Number, Name) - Bold
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
        
        // Row 1: Instruction
        val instructionRow = sheet.createRow(1)
        val instructionCell = instructionRow.createCell(0)
        instructionCell.setCellValue("Fill student details from this row onwards. Do not change the heading row.")
        
        // Rows 2 to 4: Sample rows
        val sample1 = sheet.createRow(2)
        sample1.createCell(0).setCellValue(1.0)
        sample1.createCell(1).setCellValue("Sample Student 1")
        
        val sample2 = sheet.createRow(3)
        sample2.createCell(0).setCellValue(2.0)
        sample2.createCell(1).setCellValue("Sample Student 2")
        
        val sample3 = sheet.createRow(4)
        sample3.createCell(0).setCellValue(3.0)
        sample3.createCell(1).setCellValue("Sample Student 3")
        
        // Auto-fit columns
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

    // PART 7 - SAVING ATTENDANCE
    private fun saveAttendanceFlow() {
        if (studentList.isEmpty()) {
            val emptyMsg = "No students in the list. Please import a student list first."
            Toast.makeText(this, emptyMsg, Toast.LENGTH_LONG).show()
            binding.root.announceForAccessibility(emptyMsg)
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // Check if already saved for today + session
            val existing = db.attendanceDao().getAttendanceForDateAndSession(savedDate, selectedSession)
            
            runOnUiThread {
                if (existing.isNotEmpty()) {
                    // Show overwrite dialog
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

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes button"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No button"
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
                // TalkBack announcements
                val announceSuccess = "Attendance saved successfully"
                val announceSummary = "$selectedSession attendance saved. Present: $totalPresent, Absent: $totalAbsent."
                
                Toast.makeText(this@AttendanceRegisterActivity, announceSummary, Toast.LENGTH_LONG).show()
                binding.root.announceForAccessibility("$announceSuccess. $announceSummary")
            }
        }
    }

    // PART 8 - SHARING ABSENTEES LIST
    private fun shareAbsenteesFlow() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val records = db.attendanceDao().getAttendanceForDateAndSession(savedDate, selectedSession)
            
            runOnUiThread {
                if (records.isEmpty()) {
                    // Block sharing
                    val msg = "Please save attendance before sharing."
                    Toast.makeText(this@AttendanceRegisterActivity, msg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(msg)
                    return@runOnUiThread
                }
                
                val absentees = records.filter { !it.isPresent }
                val totalAbsent = absentees.size
                val totalPresent = records.size - totalAbsent
                
                val message = StringBuilder()
                if (totalAbsent == 0) {
                    message.append("No absentees for $selectedSession on $savedDate. Full attendance present.")
                } else {
                    message.append("Absentees Report\n")
                    message.append("Date: $savedDate\n")
                    message.append("Session: $selectedSession\n\n")
                    
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
    private val onItemClick: (Int) -> Unit
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

        holder.view.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount(): Int = list.size
}
