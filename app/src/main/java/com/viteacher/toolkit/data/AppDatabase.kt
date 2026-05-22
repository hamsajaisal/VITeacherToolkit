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
        Student::class,
        AttendanceRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun timetableDao(): TimetableDao
    abstract fun credentialDao(): CredentialDao
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vi_teacher_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}