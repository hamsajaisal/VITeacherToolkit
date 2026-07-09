package com.viteacher.toolkit.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.ChecklistRecord
import com.viteacher.toolkit.data.ChecklistSummaryDto
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.databinding.ActivityChecklistsListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ChecklistsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChecklistsListBinding
    private var classId: Int = 1
    private var classroom: Classroom? = null
    private var checklistsList: List<ChecklistSummaryDto> = emptyList()
    private lateinit var listAdapter: ChecklistsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classId = intent.getIntExtra("class_id", 1)

        setupHeader()
        setupRecyclerView()
        loadClassroomAndChecklists()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCreateChecklist.setOnClickListener {
            val intent = Intent(this, ChecklistEditorActivity::class.java).apply {
                putExtra("class_id", classId)
                putExtra("mode", "create")
            }
            startActivity(intent)
        }

        binding.btnExportCCE.setOnClickListener {
            exportConsolidatedCCESheet()
        }
    }

    private fun setupHeader() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        val prefix = if (isCollege) "" else "Class "
        
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            classroom = db.classroomDao().getClassroomById(classId)
            classroom?.let {
                runOnUiThread {
                    val classText = if (isCollege) "${it.standard} ${it.division}" else "${it.standard}${it.division}"
                    binding.tvTitle.text = "$prefix$classText Submission Registers"
                    binding.tvTitle.contentDescription = "$prefix$classText Submission Registers screen"
                }
            }
        }
    }

    private fun setupRecyclerView() {
        listAdapter = ChecklistsAdapter(
            checklistsList,
            onItemClick = { summary ->
                val intent = Intent(this, ChecklistEditorActivity::class.java).apply {
                    putExtra("class_id", classId)
                    putExtra("mode", "edit")
                    putExtra("checklist_name", summary.checklistName)
                }
                startActivity(intent)
            },
            onItemLongClick = { summary ->
                showChecklistOptionsDialog(summary)
            }
        )
        binding.rvChecklists.layoutManager = LinearLayoutManager(this)
        binding.rvChecklists.adapter = listAdapter
    }

    private fun loadClassroomAndChecklists() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.checklistDao().getSavedChecklistsFlow(classId).collectLatest { list ->
                checklistsList = list
                runOnUiThread {
                    if (list.isEmpty()) {
                        binding.rvChecklists.visibility = View.GONE
                        binding.tvEmptyState.visibility = View.VISIBLE
                        val voiceGuide = "No registers found. To create a new register, tap the Create New Register button at the top of the screen."
                        binding.root.announceForAccessibility(voiceGuide)
                    } else {
                        binding.rvChecklists.visibility = View.VISIBLE
                        binding.tvEmptyState.visibility = View.GONE
                        listAdapter.updateList(list)
                    }
                }
            }
        }
    }

    private fun showChecklistOptionsDialog(summary: ChecklistSummaryDto) {
        val options = arrayOf("Share Pending List", "Export to Excel", "Delete Register")
        val dialog = AlertDialog.Builder(this)
            .setTitle(summary.checklistName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sharePendingList(summary.checklistName)
                    1 -> exportSingleChecklistToExcel(summary.checklistName)
                    2 -> confirmDeleteChecklist(summary.checklistName)
                }
            }
            .create()
            dialog.show()
        binding.root.announceForAccessibility("Options dialog for ${summary.checklistName}. Select Share Pending List, Export to Excel, or Delete Register.")
    }

    private fun sharePendingList(checklistName: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val records = db.checklistDao().getChecklist(classId, checklistName)
            runOnUiThread {
                val pending = records.filter { !it.isChecked }
                val totalPending = pending.size
                
                val message = StringBuilder()
                message.append("$checklistName Pending Report\n")
                message.append("Date: ${records.firstOrNull()?.date ?: ""}\n\n")

                if (totalPending == 0) {
                    message.append("All students have completed this checklist!")
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
        }
    }

    private fun exportSingleChecklistToExcel(checklistName: String) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val records = db.checklistDao().getChecklist(classId, checklistName)
                
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Checklist")

                // Styling
                val headerFont = workbook.createFont().apply { bold = true }
                val headerStyle = workbook.createCellStyle().apply { setFont(headerFont) }

                // Headers
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).apply {
                    setCellValue("Roll Number")
                    setCellStyle(headerStyle)
                }
                headerRow.createCell(1).apply {
                    setCellValue("Student Name")
                    setCellStyle(headerStyle)
                }
                headerRow.createCell(2).apply {
                    setCellValue("Status")
                    setCellStyle(headerStyle)
                }

                records.forEachIndexed { index, record ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(record.rollNumber.toDouble())
                    row.createCell(1).setCellValue(record.name)
                    row.createCell(2).setCellValue(if (record.isChecked) "Completed" else "Pending")
                }

                sheet.setColumnWidth(0, 4000)
                sheet.setColumnWidth(1, 8000)
                sheet.setColumnWidth(2, 4000)

                val cleanedName = checklistName.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val filename = "${cleanedName}_Checklist.xlsx"
                saveWorkbookToDownloads(workbook, filename)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ChecklistsListActivity, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportConsolidatedCCESheet() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val allRecords = db.checklistDao().getAllChecklistsForClassOnce(classId)
                val students = db.studentDao().getAllStudentsOnce(classId)

                if (allRecords.isEmpty()) {
                    runOnUiThread {
                        val msg = "No register data available to export. Create a register first."
                        Toast.makeText(this@ChecklistsListActivity, msg, Toast.LENGTH_LONG).show()
                        binding.root.announceForAccessibility(msg)
                    }
                    return@launch
                }

                val checklistNames = allRecords.map { it.checklistName }.distinct().sorted()

                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("CCE Consolidated Report")

                // Styling
                val headerFont = workbook.createFont().apply { bold = true }
                val headerStyle = workbook.createCellStyle().apply { setFont(headerFont) }

                // Headers
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).apply {
                    setCellValue("Roll Number")
                    setCellStyle(headerStyle)
                }
                headerRow.createCell(1).apply {
                    setCellValue("Student Name")
                    setCellStyle(headerStyle)
                }

                checklistNames.forEachIndexed { index, name ->
                    headerRow.createCell(index + 2).apply {
                        setCellValue(name)
                        setCellStyle(headerStyle)
                    }
                }

                students.forEachIndexed { studIndex, student ->
                    val row = sheet.createRow(studIndex + 1)
                    row.createCell(0).setCellValue(student.rollNumber.toDouble())
                    row.createCell(1).setCellValue(student.name)

                    checklistNames.forEachIndexed { checkIndex, name ->
                        val record = allRecords.find { 
                            it.rollNumber == student.rollNumber && it.checklistName == name 
                        }
                        val status = when (record?.isChecked) {
                            true -> "Done"
                            false -> "Pending"
                            else -> "N/A"
                        }
                        row.createCell(checkIndex + 2).setCellValue(status)
                    }
                }

                sheet.setColumnWidth(0, 4000)
                sheet.setColumnWidth(1, 8000)
                checklistNames.forEachIndexed { index, _ ->
                    sheet.setColumnWidth(index + 2, 5000)
                }

                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val prefix = if (isCollege) "Program" else "Class"
                val classText = classroom?.let {
                    if (isCollege) "${it.standard}_${it.division}" else "${it.standard}${it.division}"
                } ?: "Class"

                val filename = "${prefix}_${classText}_CCE_Report.xlsx"
                saveWorkbookToDownloads(workbook, filename)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ChecklistsListActivity, "Failed to export CCE sheet: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveWorkbookToDownloads(workbook: XSSFWorkbook, filename: String) {
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
                    runOnUiThread {
                        val msg = "Report exported to Downloads folder: $filename"
                        Toast.makeText(this@ChecklistsListActivity, msg, Toast.LENGTH_LONG).show()
                        binding.root.announceForAccessibility(msg)
                    }
                } else {
                    throw IOException("Failed to create MediaStore Downloads entry.")
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
                runOnUiThread {
                    val msg = "Report exported to Downloads folder: $filename"
                    Toast.makeText(this@ChecklistsListActivity, msg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(msg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this@ChecklistsListActivity, "Write failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            try {
                workbook.close()
            } catch (_: IOException) {}
        }
    }

    private fun confirmDeleteChecklist(checklistName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Register")
            .setMessage("Are you sure you want to delete '$checklistName'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                performDeleteChecklist(checklistName)
            }
            .setNegativeButton("Cancel") { d, _ ->
                d.dismiss()
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Warning dialog. Delete register '$checklistName'? This action cannot be undone. Select Delete or Cancel.")
    }

    private fun performDeleteChecklist(checklistName: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.checklistDao().deleteChecklist(classId, checklistName)
            runOnUiThread {
                val msg = "Register deleted: $checklistName"
                Toast.makeText(this@ChecklistsListActivity, msg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(msg)
            }
        }
    }
}

// Recycler Adapter for lists
class ChecklistsAdapter(
    private var list: List<ChecklistSummaryDto>,
    private val onItemClick: (ChecklistSummaryDto) -> Unit,
    private val onItemLongClick: (ChecklistSummaryDto) -> Unit
) : RecyclerView.Adapter<ChecklistsAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvChecklistName)
        val tvDate: TextView = view.findViewById(R.id.tvChecklistDate)
        val tvSummary: TextView = view.findViewById(R.id.tvChecklistSummary)
    }

    fun updateList(newList: List<ChecklistSummaryDto>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_checklist, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvName.text = item.checklistName
        holder.tvDate.text = item.date
        
        // Count totals in background or display loading, but since we are simple we'll get it quickly
        val db = AppDatabase.getDatabase(holder.view.context.applicationContext)
        val classId = (holder.view.context as ChecklistsListActivity).intent.getIntExtra("class_id", 1)
        
        (holder.view.context as ChecklistsListActivity).lifecycleScope.launch {
            val records = db.checklistDao().getChecklist(classId, item.checklistName)
            val pending = records.count { !it.isChecked }
            val total = records.size
            (holder.view.context as ChecklistsListActivity).runOnUiThread {
                holder.tvSummary.text = "$pending Pending of $total"
                holder.view.contentDescription = "${item.checklistName}, edited on ${item.date}, $pending pending of $total students. Double tap to edit, double tap and hold for options."
            }
        }

        holder.view.setOnClickListener {
            onItemClick(item)
        }

        holder.view.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = list.size
}
