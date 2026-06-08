package com.viteacher.toolkit

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.apache.poi.ss.usermodel.WorkbookFactory
import com.viteacher.toolkit.util.ExcelParser

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testExcelParsingAndValidation() {
        val file = File("C:\\Users\\PRO\\Downloads\\SAMPLE SAMPOORNA.xls")
        assertTrue("Excel file does not exist in Downloads", file.exists())

        val workbook = WorkbookFactory.create(file)
        val sheet = workbook.getSheetAt(0)
        assertEquals("Worksheet1", sheet.sheetName)

        // Find header row
        val headerRow = sheet.getRow(1)
        assertNotNull("Row 1 must be the header row", headerRow)

        val headers = mutableListOf<String>()
        for (c in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(c)
            headers.add(ExcelParser.getCellValueAsString(cell))
        }

        assertTrue(headers.contains("Admission no"))
        assertTrue(headers.contains("Full name"))
        assertTrue(headers.contains("Phone Number/Mobile Number"))

        val admissionIndex = headers.indexOf("Admission no")
        val nameIndex = headers.indexOf("Full name")
        val phoneIndex = headers.indexOf("Phone Number/Mobile Number")

        assertEquals(0, admissionIndex)
        assertEquals(1, nameIndex)
        assertEquals(16, phoneIndex)

        var studentCount = 0
        val lastRow = sheet.lastRowNum
        println("Last row index: $lastRow")
        for (r in 2..lastRow) {
            val row = sheet.getRow(r)
            if (row == null) {
                println("Row $r is null")
                continue
            }
            var admNo = ExcelParser.getCellValueAsString(row.getCell(admissionIndex))
            if (admNo.endsWith(".0")) {
                admNo = admNo.substring(0, admNo.length - 2)
            }
            val name = ExcelParser.getCellValueAsString(row.getCell(nameIndex))
            println("Row $r: admNo='$admNo', name='$name'")
            
            if (admNo.isNotEmpty() && name.isNotEmpty()) {
                studentCount++
                if (studentCount == 1) {
                    assertEquals("12418", admNo)
                    assertEquals("AMJAD KHAN K", name)
                }
            }
        }
        assertEquals(15, studentCount)
        println("Successfully parsed and verified $studentCount student records from Sampoorna Excel file.")
    }
}