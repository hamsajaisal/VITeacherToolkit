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

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val rollNumber: Int,
    val name: String
)

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["date", "session", "rollNumber"]
)
data class AttendanceRecord(
    val date: String,          // Format: "22 May 2026"
    val session: String,       // "Forenoon" or "Afternoon"
    val rollNumber: Int,
    val name: String,
    val isPresent: Boolean
)