package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.viteacher.toolkit.util.UpdateInfo
import com.viteacher.toolkit.util.UpdateManager
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var pendingApkFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val profile = db.userProfileDao().getProfile()
            runOnUiThread {
                if (profile != null) {
                    binding.tvWelcome.text = "Hi ${profile.firstName}"
                    binding.tvSchoolName.text = profile.schoolName
                }
            }
        }

        binding.btnTimetable.setOnClickListener {
            startActivity(Intent(this, TimetableActivity::class.java))
        }

        binding.btnPasswordSaver.setOnClickListener {
            val intent = Intent(this, PinLoginActivity::class.java)
            intent.putExtra("target", "password_saver")
            startActivity(intent)
        }

        binding.btnMyNotes.setOnClickListener {
            startActivity(Intent(this, MyNotesActivity::class.java))
        }

        binding.btnMyClass.setOnClickListener {
            startActivity(Intent(this, MyClassActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Exit App")
                    .setMessage("Are you sure you want to exit VI Teacher Toolkit?")
                    .setPositiveButton("Exit") { _, _ ->
                        finishAffinity()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        binding.root.announceForAccessibility("Exit cancelled. You are still in the app.")
                    }
                    .create()
                    .show()
                binding.root.announceForAccessibility(
                    "Exit app dialog. Are you sure you want to exit? Choose Exit or Cancel."
                )
            }
        })
    }

    override fun onResume() {
        super.onResume()
        checkClassSettings()

        val apk = pendingApkFile
        if (apk != null && apk.exists()) {
            if (UpdateManager.canInstallPackages(this)) {
                pendingApkFile = null
                UpdateManager.installApk(this, apk)
            }
        } else {
            checkForUpdates()
        }
    }

    private fun checkClassSettings() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val standard = prefs.getString("class_standard", "")
        val division = prefs.getString("class_division", "")
        val academicYear = prefs.getString("class_academic_year", "")

        if (!standard.isNullOrEmpty() && !division.isNullOrEmpty() && !academicYear.isNullOrEmpty()) {
            binding.btnMyClass.visibility = android.view.View.VISIBLE
        } else {
            binding.btnMyClass.visibility = android.view.View.GONE
        }
    }

    private fun checkForUpdates() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        val lastSuppressedTime = prefs.getLong("update_suppressed_time", 0L)
        val currentTime = System.currentTimeMillis()
        
        // Suppress dialog check for 24 hours
        if (currentTime - lastSuppressedTime < 86400000L) {
            return
        }

        lifecycleScope.launch {
            val currentVersionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "2.0"
            } catch (e: Exception) {
                "2.0"
            }

            val updateInfo = UpdateManager.checkForUpdate(currentVersionName)
            if (updateInfo != null) {
                runOnUiThread {
                    showUpdateDialog(updateInfo)
                }
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val promptTextView = TextView(this).apply {
            text = "A new version (${updateInfo.latestVersion}) is available. Would you like to update?"
            textSize = 16f
            setTextColor(android.graphics.Color.BLACK)
            contentDescription = "A new version ${updateInfo.latestVersion} is available. Would you like to update?"
        }
        container.addView(promptTextView)

        val whatsNewHeader = TextView(this).apply {
            text = "\nWhat's New:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
        }
        container.addView(whatsNewHeader)

        val scrollView = ScrollView(this).apply {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (120 * resources.displayMetrics.density).toInt()
            )
            this.layoutParams = layoutParams
        }

        val changelogTextView = TextView(this).apply {
            text = updateInfo.changelog
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
            contentDescription = "Changelog details: ${updateInfo.changelog}"
        }
        scrollView.addView(changelogTextView)
        container.addView(scrollView)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Update cancelled.")
            }
            .setNeutralButton("Remind Me Later") { dialog, _ ->
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("update_suppressed_time", System.currentTimeMillis()).apply()
                dialog.dismiss()
                val msg = "We will remind you about this update in 24 hours."
                Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(msg)
            }
            .setPositiveButton("Download & Install") { dialog, _ ->
                dialog.dismiss()
                startBackgroundDownload(updateInfo.downloadUrl)
            }
            .create()

        dialog.show()
        binding.root.announceForAccessibility(
            "Update available version ${updateInfo.latestVersion}. Would you like to update? Read the changelog or choose Cancel, Remind Me Later, or Download and Install."
        )
    }

    private fun startBackgroundDownload(downloadUrl: String) {
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setMessage("Please wait while the update is downloading...")
            .setView(progressBar)
            .setCancelable(false)
            .create()

        progressDialog.show()
        binding.root.announceForAccessibility("Starting download. Please wait.")

        lifecycleScope.launch {
            val apkFile = UpdateManager.downloadApk(this@HomeActivity, downloadUrl) { progress ->
                progressBar.progress = progress
                progressDialog.setMessage("Downloading: $progress%")
                if (progress % 10 == 0) {
                    binding.root.announceForAccessibility("Downloading progress $progress percent")
                }
            }

            runOnUiThread {
                progressDialog.dismiss()
                if (apkFile != null && apkFile.exists()) {
                    handleDownloadedApk(apkFile)
                } else {
                    val msg = "Download failed. Please try again later."
                    Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(msg)
                }
            }
        }
    }

    private fun handleDownloadedApk(apkFile: File) {
        if (UpdateManager.canInstallPackages(this)) {
            UpdateManager.installApk(this, apkFile)
        } else {
            pendingApkFile = apkFile
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("To install the update, you need to allow VI Teacher Toolkit to install unknown apps. Please enable this setting in the next screen.")
                .setCancelable(false)
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    binding.root.announceForAccessibility("Update cancelled due to missing installation permission.")
                }
                .setPositiveButton("Go to Settings") { dialog, _ ->
                    dialog.dismiss()
                    UpdateManager.requestInstallPermission(this@HomeActivity)
                }
                .create()
                .show()
            binding.root.announceForAccessibility("Permission required to install update. Press Go to Settings to enable it or Cancel.")
        }
    }
}