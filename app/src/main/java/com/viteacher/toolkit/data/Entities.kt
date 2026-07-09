package com.viteacher.toolkit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val schoolName: String,
    val pin: String
)

@Entity(tableName = "school_periods")
data class SchoolPeriod(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val periodNumber: Int,
    val startTime: String,
    val endTime: String,
    val isException: Boolean = false,
    val exceptionDay: String = ""
)

@Entity(tableName = "timetable_entries")
data class TimetableEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dayOfWeek: String,
    val periodNumber: Int,
    val subject: String,
    val className: String,
    val division: String,
    val reminderMinutesBefore: Int = 0
)

@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val username: String,
    val password: String
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String, // Saved as HTML
    val fontSize: Int, // Saved font size in sp (default 18)
    val category: String, // Subject/Category (default "General")
    val isPinned: Boolean = false,
    val lastEdited: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String // Unique category name
)

@Entity(tableName = "classrooms")
data class Classroom(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val standard: String,
    val division: String,
    val academicYear: String,
    val attendanceType: String, // "DoubleSession", "OnceADay", "HourWise"
    val totalHours: Int = 0
)

@Entity(
    tableName = "students",
    primaryKeys = ["classId", "rollNumber"]
)
data class Student(
    val classId: Int,
    val rollNumber: Int,
    val name: String
)

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["classId", "date", "session", "rollNumber"]
)
data class AttendanceRecord(
    val classId: Int,
    val date: String,          // Format: "22 May 2026"
    val session: String,       // "Forenoon", "Afternoon", "Daily", "Hour X"
    val rollNumber: Int,
    val name: String,
    val isPresent: Boolean
)

@Entity(
    tableName = "student_profiles",
    primaryKeys = ["classId", "admissionNumber"]
)
data class StudentProfile(
    val classId: Int,
    val admissionNumber: String,
    val name: String
)

@Entity(
    tableName = "student_profile_fields",
    primaryKeys = ["classId", "admissionNumber", "fieldName"]
)
data class StudentProfileField(
    val classId: Int,
    val admissionNumber: String,
    val fieldName: String,
    val fieldValue: String
)

@Entity(tableName = "student_remarks")
data class StudentRemark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val classId: Int,
    val admissionNumber: String,
    val date: String,
    val subject: String,
    val remarkText: String,
    val timestamp: Long
)

@Entity(
    tableName = "checklist_records",
    primaryKeys = ["classId", "checklistName", "rollNumber"]
)
data class ChecklistRecord(
    val classId: Int,
    val checklistName: String,
    val rollNumber: Int,
    val name: String,
    val isChecked: Boolean,
    val date: String
)

@Entity(tableName = "link_folders")
data class LinkFolder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "link_items")
data class LinkItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderId: Int,
    val title: String,
    val url: String,
    val isPinned: Boolean = false
)