package com.viteacher.toolkit.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [
        UserProfile::class,
        TimetableEntry::class,
        SchoolPeriod::class,
        Credential::class,
        Note::class,
        Category::class,
        Classroom::class,
        Student::class,
        AttendanceRecord::class,
        StudentProfile::class,
        StudentProfileField::class,
        StudentRemark::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun timetableDao(): TimetableDao
    abstract fun credentialDao(): CredentialDao
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun classroomDao(): ClassroomDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun studentProfileFieldDao(): StudentProfileFieldDao
    abstract fun studentRemarkDao(): StudentRemarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `fontSize` INTEGER NOT NULL, `category` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `lastEdited` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `students` (`rollNumber` INTEGER NOT NULL, PRIMARY KEY(`rollNumber`), `name` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `attendance_records` (`date` TEXT NOT NULL, `session` TEXT NOT NULL, `rollNumber` INTEGER NOT NULL, `name` TEXT NOT NULL, `isPresent` INTEGER NOT NULL, PRIMARY KEY(`date`, `session`, `rollNumber`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create classrooms table
                db.execSQL("CREATE TABLE IF NOT EXISTS `classrooms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `standard` TEXT NOT NULL, `division` TEXT NOT NULL, `academicYear` TEXT NOT NULL, `attendanceType` TEXT NOT NULL, `totalHours` INTEGER NOT NULL)")
                
                // 2. Insert a default classroom using previous/default values (6B 2026-27, DoubleSession)
                db.execSQL("INSERT INTO `classrooms` (`id`, `standard`, `division`, `academicYear`, `attendanceType`, `totalHours`) VALUES (1, '6', 'B', '2026-27', 'DoubleSession', 0)")
                
                // 3. Migrate students table (sqlite doesn't allow changing PK easily, so we re-create)
                db.execSQL("ALTER TABLE `students` RENAME TO `students_old`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `students` (`classId` INTEGER NOT NULL, `rollNumber` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`classId`, `rollNumber`))")
                db.execSQL("INSERT INTO `students` (`classId`, `rollNumber`, `name`) SELECT 1, `rollNumber`, `name` FROM `students_old`")
                db.execSQL("DROP TABLE `students_old`")
                
                // 4. Migrate attendance_records table
                db.execSQL("ALTER TABLE `attendance_records` RENAME TO `attendance_records_old`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `attendance_records` (`classId` INTEGER NOT NULL, `date` TEXT NOT NULL, `session` TEXT NOT NULL, `rollNumber` INTEGER NOT NULL, `name` TEXT NOT NULL, `isPresent` INTEGER NOT NULL, PRIMARY KEY(`classId`, `date`, `session`, `rollNumber`))")
                db.execSQL("INSERT INTO `attendance_records` (`classId`, `date`, `session`, `rollNumber`, `name`, `isPresent`) SELECT 1, `date`, `session`, `rollNumber`, `name`, `isPresent` FROM `attendance_records_old`")
                db.execSQL("DROP TABLE `attendance_records_old`")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `student_profiles` (`classId` INTEGER NOT NULL, `admissionNumber` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`classId`, `admissionNumber`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `student_profile_fields` (`classId` INTEGER NOT NULL, `admissionNumber` TEXT NOT NULL, `fieldName` TEXT NOT NULL, `fieldValue` TEXT NOT NULL, PRIMARY KEY(`classId`, `admissionNumber`, `fieldName`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `student_remarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `classId` INTEGER NOT NULL, `admissionNumber` TEXT NOT NULL, `date` TEXT NOT NULL, `subject` TEXT NOT NULL, `remarkText` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vi_teacher_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}