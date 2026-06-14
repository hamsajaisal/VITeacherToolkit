package com.viteacher.toolkit.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries ORDER BY dayOfWeek, periodNumber")
    fun getAllEntries(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries ORDER BY dayOfWeek, periodNumber")
    suspend fun getAllEntriesOnce(): List<TimetableEntry>

    @Query("SELECT * FROM timetable_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): TimetableEntry?

    @Query("SELECT * FROM timetable_entries WHERE dayOfWeek = :day ORDER BY periodNumber")
    fun getEntriesForDay(day: String): Flow<List<TimetableEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TimetableEntry)

    @Update
    suspend fun updateEntry(entry: TimetableEntry)

    @Delete
    suspend fun deleteEntry(entry: TimetableEntry)

    @Query("SELECT * FROM school_periods ORDER BY periodNumber")
    fun getAllPeriods(): Flow<List<SchoolPeriod>>

    @Query("SELECT * FROM school_periods ORDER BY periodNumber")
    suspend fun getAllPeriodsOnce(): List<SchoolPeriod>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriod(period: SchoolPeriod)

    @Delete
    suspend fun deletePeriod(period: SchoolPeriod)
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY title")
    fun getAllCredentials(): Flow<List<Credential>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential)

    @Update
    suspend fun updateCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastEdited DESC")
    fun getAllNotesFlow(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastEdited DESC")
    suspend fun getAllNotesOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface ClassroomDao {
    @Query("SELECT * FROM classrooms ORDER BY id ASC")
    fun getAllClassroomsFlow(): Flow<List<Classroom>>

    @Query("SELECT * FROM classrooms ORDER BY id ASC")
    suspend fun getAllClassroomsOnce(): List<Classroom>

    @Query("SELECT * FROM classrooms WHERE id = :classId LIMIT 1")
    suspend fun getClassroomById(classId: Int): Classroom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassroom(classroom: Classroom): Long

    @Delete
    suspend fun deleteClassroom(classroom: Classroom)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY rollNumber ASC")
    fun getAllStudentsFlow(classId: Int): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY rollNumber ASC")
    suspend fun getAllStudentsOnce(classId: Int): List<Student>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Query("DELETE FROM students WHERE classId = :classId")
    suspend fun deleteAllStudents(classId: Int)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND date = :date AND session = :session ORDER BY rollNumber ASC")
    suspend fun getAttendanceForDateAndSession(classId: Int, date: String, session: String): List<AttendanceRecord>

    @Query("SELECT DISTINCT session FROM attendance_records WHERE classId = :classId AND date = :date")
    suspend fun getSavedSessionsForDate(classId: Int, date: String): List<String>

    @Query("SELECT DISTINCT date, session FROM attendance_records WHERE classId = :classId ORDER BY date DESC, session ASC")
    fun getAllSavedDatesAndSessionsFlow(classId: Int): Flow<List<DateSessionDto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND date = :date")
    suspend fun getAttendanceForDateAndClass(classId: Int, date: String): List<AttendanceRecord>

    @Query("DELETE FROM attendance_records WHERE classId = :classId")
    suspend fun deleteAttendanceByClassId(classId: Int)

    @Query("SELECT * FROM attendance_records WHERE classId = :classId")
    suspend fun getAllAttendanceRecordsForClassOnce(classId: Int): List<AttendanceRecord>
}

data class DateSessionDto(
    val date: String,
    val session: String
)

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profiles WHERE classId = :classId ORDER BY name ASC")
    suspend fun getAllStudentProfiles(classId: Int): List<StudentProfile>

    @Query("SELECT * FROM student_profiles WHERE classId = :classId ORDER BY name ASC")
    fun getAllStudentProfilesFlow(classId: Int): Flow<List<StudentProfile>>

    @Query("SELECT * FROM student_profiles WHERE classId = :classId AND admissionNumber = :admissionNumber LIMIT 1")
    suspend fun getStudentProfile(classId: Int, admissionNumber: String): StudentProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentProfiles(profiles: List<StudentProfile>)

    @Query("DELETE FROM student_profiles WHERE classId = :classId")
    suspend fun deleteStudentProfilesForClass(classId: Int)
}

@Dao
interface StudentProfileFieldDao {
    @Query("SELECT * FROM student_profile_fields WHERE classId = :classId AND admissionNumber = :admissionNumber")
    suspend fun getFieldsForStudent(classId: Int, admissionNumber: String): List<StudentProfileField>

    @Query("SELECT * FROM student_profile_fields WHERE classId = :classId")
    suspend fun getFieldsForClass(classId: Int): List<StudentProfileField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentProfileFields(fields: List<StudentProfileField>)

    @Query("DELETE FROM student_profile_fields WHERE classId = :classId")
    suspend fun deleteStudentProfileFieldsForClass(classId: Int)
}

@Dao
interface StudentRemarkDao {
    @Query("SELECT * FROM student_remarks WHERE classId = :classId AND admissionNumber = :admissionNumber ORDER BY timestamp DESC")
    suspend fun getRemarksForStudent(classId: Int, admissionNumber: String): List<StudentRemark>

    @Query("SELECT * FROM student_remarks WHERE classId = :classId AND admissionNumber = :admissionNumber ORDER BY timestamp DESC")
    fun getRemarksForStudentFlow(classId: Int, admissionNumber: String): Flow<List<StudentRemark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemark(remark: StudentRemark): Long

    @Update
    suspend fun updateRemark(remark: StudentRemark)

    @Delete
    suspend fun deleteRemark(remark: StudentRemark)

    @Query("DELETE FROM student_remarks WHERE classId = :classId")
    suspend fun deleteRemarksForClass(classId: Int)
}