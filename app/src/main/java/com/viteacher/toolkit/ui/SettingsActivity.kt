package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.databinding.ActivitySettingsBinding
import com.viteacher.toolkit.util.setAccessibleSelection
import com.viteacher.toolkit.util.StorageUtils
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding


    // Activity Result Launchers for Document Pickers
    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            performBackup(uri)
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            performRestore(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(this, PinLoginActivity::class.java).apply {
                putExtra("target", "edit_profile")
            }
            startActivity(intent)
        }

        binding.btnSchoolHours.setOnClickListener {
            startActivity(Intent(this, SchoolHoursActivity::class.java))
        }

        binding.btnTtsSettings.setOnClickListener {
            startActivity(Intent(this, TtsSettingsActivity::class.java))
        }

        binding.btnClassroomSettings.setOnClickListener {
            startActivity(Intent(this, ClassroomSettingsActivity::class.java))
        }

        binding.btnBackupData.setOnClickListener {
            triggerBackup()
        }

        binding.btnRestoreData.setOnClickListener {
            triggerRestore()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDynamicLabels()
    }

    private fun updateDynamicLabels() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        if (isCollege) {
            binding.btnSchoolHours.text = "Set College Hour Schedule"
            binding.btnSchoolHours.contentDescription = "Set up your college hour timings"
            binding.btnClassroomSettings.text = "Program Settings"
            binding.btnClassroomSettings.contentDescription = "Program Settings button. Configure program, year or semester, and academic year. This is a feature for teachers."
        } else {
            binding.btnSchoolHours.text = "Set School Hour Schedule"
            binding.btnSchoolHours.contentDescription = "Set up your school period timings"
            binding.btnClassroomSettings.text = "Classroom Settings"
            binding.btnClassroomSettings.contentDescription = "Classroom Settings button. Configure class, division, and academic year. This is a feature for class teachers."
        }
    }


    // --- DATABASE BACKUP AND RESTORE WORKFLOWS ---

    private fun triggerBackup() {
        backupLauncher.launch("vtt_backup.db")
        binding.root.announceForAccessibility("File creator opened. Choose where to save your backup file.")
    }

    private fun performBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 1. Checkpoint SQLite and cleanly close the database connection
                val db = AppDatabase.getDatabase(applicationContext)
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
                db.close()

                // 2. Read database file and copy to output stream
                val dbFile = getDatabasePath("vi_teacher_database")
                contentResolver.openOutputStream(uri)?.use { output ->
                    dbFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                // 3. Save copy to the public Downloads/VITeacherToolkit folder
                val dateSuffix = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val backupName = "vtt_backup_$dateSuffix.db"
                val publicBackupFile = StorageUtils.saveInputStreamToPublicDownloads(
                    applicationContext,
                    backupName,
                    "application/octet-stream",
                    dbFile.inputStream()
                )

                runOnUiThread {
                    val successMsg = if (publicBackupFile != null) {
                        "Application database backed up and saved to Downloads folder VITeacherToolkit successfully!"
                    } else {
                        "Application database backed up successfully!"
                    }
                    Toast.makeText(this@SettingsActivity, successMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(successMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    val errorMsg = "Backup failed: ${e.message}"
                    Toast.makeText(this@SettingsActivity, errorMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(errorMsg)
                }
            }
        }
    }

    private fun triggerRestore() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Restore Application Data")
            .setMessage("Warning: Restoring data will overwrite all your current school hours, timetables, classes, student lists, and historical attendance logs. This action cannot be undone. Are you sure you want to proceed?")
            .setPositiveButton("Yes, Select File") { _, _ ->
                // Allow octet-stream, x-sqlite3, or general files to accommodate different backup naming variations
                restoreLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
            }
            .setNegativeButton("No, Cancel") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Restore cancelled.")
            }
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes, select file"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No, cancel"
        binding.root.announceForAccessibility("Warning dialog. Restoring data will overwrite all current settings. Select Yes to choose your backup file, or No to cancel.")
    }

    private fun performRestore(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 1. Close active Room database instance safely
                val db = AppDatabase.getDatabase(applicationContext)
                db.close()

                // 2. Copy backup stream to original database file
                val dbFile = getDatabasePath("vi_teacher_database")
                contentResolver.openInputStream(uri)?.use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. Clear SQLite temporary journal files to prevent state conflicts
                val shmFile = File(dbFile.path + "-shm")
                val walFile = File(dbFile.path + "-wal")
                if (shmFile.exists()) shmFile.delete()
                if (walFile.exists()) walFile.delete()

                runOnUiThread {
                    showRestoreSuccessDialog()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    val errorMsg = "Restore failed: ${e.message}"
                    Toast.makeText(this@SettingsActivity, errorMsg, Toast.LENGTH_LONG).show()
                    binding.root.announceForAccessibility(errorMsg)
                }
            }
        }
    }

    private fun showRestoreSuccessDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Restore Successful")
            .setMessage("Your application data has been successfully restored. To load your restored classroom environments and timetables, the application needs to restart now.")
            .setCancelable(false)
            .setPositiveButton("Restart App") { _, _ ->
                restartApplication()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Restart App"
        binding.root.announceForAccessibility("Restore Successful dialog. Your data has been restored. Select Restart App to finalize.")
    }

    private fun restartApplication() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}