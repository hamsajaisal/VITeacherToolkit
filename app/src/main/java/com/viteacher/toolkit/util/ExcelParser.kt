package com.viteacher.toolkit.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.StudentProfile
import com.viteacher.toolkit.data.StudentProfileField
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object ExcelParser {

    fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    sdf.format(date)
                } else {
                    val doubleVal = cell.numericCellValue
                    if (doubleVal == doubleVal.toLong().toDouble()) {
                        doubleVal.toLong().toString()
                    } else {
                        doubleVal.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue.trim()
                } catch (e: Exception) {
                    try {
                        val doubleVal = cell.numericCellValue
                        if (doubleVal == doubleVal.toLong().toDouble()) {
                            doubleVal.toLong().toString()
                        } else {
                            doubleVal.toString()
                        }
                    } catch (e2: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    class ImportResult(
        val successCount: Int,
        val errorMessage: String? = null
    )

    suspend fun importExcelData(
        context: Context,
        classId: Int,
        uri: Uri
    ): ImportResult {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return ImportResult(0, "The file could not be read. Please make sure you are using an Excel file downloaded from the Sampoorna portal.")
            }

            val workbook = WorkbookFactory.create(inputStream)
            if (workbook.numberOfSheets == 0) {
                return ImportResult(0, "No sheets found in Excel file.")
            }
            val sheet = workbook.getSheetAt(0)

            // 1. Locate header row (first row with data)
            var headerRow: Row? = null
            var headerRowIndex = -1
            for (r in 0..100) {
                val row = sheet.getRow(r) ?: continue
                var hasData = false
                for (c in 0 until row.lastCellNum) {
                    val cell = row.getCell(c)
                    if (cell != null && getCellValueAsString(cell).isNotEmpty()) {
                        hasData = true
                        break
                    }
                }
                if (hasData) {
                    headerRow = row
                    headerRowIndex = r
                    break
                }
            }

            if (headerRow == null) {
                return ImportResult(0, "No data rows found in the Excel file.")
            }

            // 2. Read headers
            val headers = mutableListOf<String>()
            for (c in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(c)
                headers.add(getCellValueAsString(cell))
            }

            // 3. Match critical columns
            val admissionIndex = headers.indexOfFirst { 
                val lower = it.lowercase()
                lower.contains("admission") || lower.matches(Regex(".*adm.*no.*"))
            }
            val nameIndex = headers.indexOfFirst { 
                val lower = it.lowercase()
                lower.contains("name") 
            }

            if (admissionIndex == -1 || nameIndex == -1) {
                return ImportResult(0, "Required columns 'Admission no' and 'Full name' not found. Please make sure you are using an Excel file downloaded from the Sampoorna portal.")
            }

            // 4. Parse students and dynamic fields
            val studentProfiles = mutableListOf<StudentProfile>()
            val profileFields = mutableListOf<StudentProfileField>()

            val lastRow = sheet.lastRowNum
            for (r in (headerRowIndex + 1)..lastRow) {
                val row = sheet.getRow(r) ?: continue
                
                // Read admission number
                var admNo = getCellValueAsString(row.getCell(admissionIndex))
                if (admNo.isEmpty()) continue
                
                // Strip float decimals if any, e.g. "12418.0" to "12418"
                if (admNo.endsWith(".0")) {
                    admNo = admNo.substring(0, admNo.length - 2)
                }

                val name = getCellValueAsString(row.getCell(nameIndex))

                if (admNo.isNotEmpty() && name.isNotEmpty()) {
                    studentProfiles.add(StudentProfile(classId, admNo, name))

                    // Read all dynamic attributes
                    for (c in 0 until row.lastCellNum) {
                        if (c < headers.size) {
                            val headerName = headers[c]
                            if (headerName.isNotEmpty()) {
                                var fieldValue = getCellValueAsString(row.getCell(c))
                                // Normalize values
                                if (c == admissionIndex && fieldValue.endsWith(".0")) {
                                    fieldValue = fieldValue.substring(0, fieldValue.length - 2)
                                }
                                if (headerName.lowercase().contains("pincode") && fieldValue.endsWith(".0")) {
                                    fieldValue = fieldValue.substring(0, fieldValue.length - 2)
                                }
                                profileFields.add(StudentProfileField(classId, admNo, headerName, fieldValue))
                            }
                        }
                    }
                }
            }

            if (studentProfiles.isEmpty()) {
                return ImportResult(0, "No valid student rows found in the Excel file.")
            }

            // 5. Save to database inside a transaction
            val db = AppDatabase.getDatabase(context.applicationContext)
            db.withTransaction {
                db.studentProfileDao().deleteStudentProfilesForClass(classId)
                db.studentProfileFieldDao().deleteStudentProfileFieldsForClass(classId)
                db.studentProfileDao().insertStudentProfiles(studentProfiles)
                db.studentProfileFieldDao().insertStudentProfileFields(profileFields)
            }

            return ImportResult(studentProfiles.size)

        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(0, "The file could not be read. Please make sure you are using an Excel file downloaded from the Sampoorna portal.")
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {}
        }
    }
}
