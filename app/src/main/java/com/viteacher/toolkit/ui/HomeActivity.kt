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

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

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
}