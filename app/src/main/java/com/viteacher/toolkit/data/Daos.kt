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
    @Query("SELECT * FROM timetable_entries ORDER BY CASE dayOfWeek WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6 WHEN 'Sunday' THEN 7 ELSE 8 END, periodNumber")
    fun getAllEntries(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries ORDER BY CASE dayOfWeek WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 WHEN 'Friday' THEN 5 WHEN 'Saturday' THEN 6 WHEN 'Sunday' THEN 7 ELSE 8 END, periodNumber")
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

    @Query("DELETE FROM students WHERE classId = :classId AND rollNumber = :rollNumber")
    suspend fun deleteStudent(classId: Int, rollNumber: Int)

    @Query("UPDATE students SET rollNumber = rollNumber - 1 WHERE classId = :classId AND rollNumber > :deletedRoll")
    suspend fun shiftRollNumbers(classId: Int, deletedRoll: Int)
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

    @Query("DELETE FROM attendance_records WHERE classId = :classId AND rollNumber = :rollNumber")
    suspend fun deleteAttendanceForStudent(classId: Int, rollNumber: Int)

    @Query("UPDATE attendance_records SET rollNumber = rollNumber - 1 WHERE classId = :classId AND rollNumber > :deletedRoll")
    suspend fun shiftAttendanceRollNumbers(classId: Int, deletedRoll: Int)
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

    @Query("DELETE FROM student_profiles WHERE classId = :classId AND admissionNumber = :admissionNumber")
    suspend fun deleteStudentProfile(classId: Int, admissionNumber: String)
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

    @Query("DELETE FROM student_profile_fields WHERE classId = :classId AND admissionNumber = :admissionNumber")
    suspend fun deleteStudentProfileFields(classId: Int, admissionNumber: String)
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

    @Query("DELETE FROM student_remarks WHERE classId = :classId AND admissionNumber = :admissionNumber")
    suspend fun deleteRemarksForStudent(classId: Int, admissionNumber: String)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_records WHERE classId = :classId AND checklistName = :checklistName ORDER BY rollNumber ASC")
    suspend fun getChecklist(classId: Int, checklistName: String): List<ChecklistRecord>

    @Query("SELECT DISTINCT checklistName, date FROM checklist_records WHERE classId = :classId ORDER BY date DESC")
    fun getSavedChecklistsFlow(classId: Int): Flow<List<ChecklistSummaryDto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistRecords(records: List<ChecklistRecord>)

    @Query("DELETE FROM checklist_records WHERE classId = :classId AND checklistName = :checklistName")
    suspend fun deleteChecklist(classId: Int, checklistName: String)

    @Query("SELECT * FROM checklist_records WHERE classId = :classId")
    suspend fun getAllChecklistsForClassOnce(classId: Int): List<ChecklistRecord>

    @Query("DELETE FROM checklist_records WHERE classId = :classId AND rollNumber = :rollNumber")
    suspend fun deleteChecklistRecordsForStudent(classId: Int, rollNumber: Int)

    @Query("UPDATE checklist_records SET rollNumber = rollNumber - 1 WHERE classId = :classId AND rollNumber > :deletedRoll")
    suspend fun shiftChecklistRollNumbers(classId: Int, deletedRoll: Int)
}

@Dao
interface LinkFolderDao {
    @Query("SELECT * FROM link_folders ORDER BY name ASC")
    fun getAllFoldersFlow(): Flow<List<LinkFolder>>

    @Query("SELECT * FROM link_folders ORDER BY name ASC")
    suspend fun getAllFoldersOnce(): List<LinkFolder>

    @Query("SELECT * FROM link_folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): LinkFolder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: LinkFolder): Long

    @Update
    suspend fun updateFolder(folder: LinkFolder)

    @Delete
    suspend fun deleteFolder(folder: LinkFolder)
}

@Dao
interface LinkItemDao {
    @Query("SELECT * FROM link_items WHERE folderId = :folderId ORDER BY isPinned DESC, title ASC")
    fun getLinksForFolderFlow(folderId: Int): Flow<List<LinkItem>>

    @Query("SELECT * FROM link_items WHERE folderId = :folderId ORDER BY isPinned DESC, title ASC")
    suspend fun getLinksForFolderOnce(folderId: Int): List<LinkItem>

    @Query("SELECT * FROM link_items ORDER BY isPinned DESC, title ASC")
    suspend fun getAllLinksOnce(): List<LinkItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkItem): Long

    @Update
    suspend fun updateLink(link: LinkItem)

    @Delete
    suspend fun deleteLink(link: LinkItem)

    @Query("DELETE FROM link_items WHERE folderId = :folderId")
    suspend fun deleteLinksForFolder(folderId: Int)
}

data class ChecklistSummaryDto(
    val checklistName: String,
    val date: String
)