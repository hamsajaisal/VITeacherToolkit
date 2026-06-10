package com.viteacher.toolkit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.StudentProfile
import com.viteacher.toolkit.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast


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
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val list = db.classroomDao().getAllClassroomsOnce()
                runOnUiThread {
                    if (list.size == 1) {
                        val intent = Intent(this@HomeActivity, MyClassActivity::class.java).apply {
                            putExtra("class_id", list[0].id)
                        }
                        startActivity(intent)
                    } else if (list.size > 1) {
                        startActivity(Intent(this@HomeActivity, ClassSelectionActivity::class.java))
                    }
                }
            }
        }


        binding.btnClassroomTimer.contentDescription = "Classroom Timer"
        binding.btnClassroomTimer.setOnClickListener {
            startActivity(Intent(this, ClassroomTimerActivity::class.java))
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
        checkStudentBirthdays()
        rescheduleTimetableReminders()
    }

    private fun rescheduleTimetableReminders() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entries = db.timetableDao().getAllEntriesOnce()
                val periods = db.timetableDao().getAllPeriodsOnce()
                val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
                val language = prefs.getString("reminder_language", "en") ?: "en"

                entries.forEach { entry ->
                    if (entry.reminderMinutesBefore >= 0) {
                        val matchingPeriod = periods.find {
                            it.periodNumber == entry.periodNumber
                        }
                        if (matchingPeriod != null) {
                            com.viteacher.toolkit.util.ReminderScheduler.scheduleReminder(
                                applicationContext,
                                entry,
                                matchingPeriod,
                                language
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkClassSettings() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val list = db.classroomDao().getAllClassroomsOnce()
            runOnUiThread {
                if (list.isNotEmpty()) {
                    binding.btnMyClass.visibility = android.view.View.VISIBLE
                } else {
                    binding.btnMyClass.visibility = android.view.View.GONE
                }
            }
        }
    }



    // --- STUDENT BIRTHDAYS & PREMIUM TEMPLATES INTEGRATION ---

    private val birthdayWishTemplates = arrayOf(
        "Wishing you a very Happy Birthday, [Name]! May this year bring you joy and success!",
        "Many many happy returns of the day, [Name]! Hope your special day is filled with wonderful surprises!",
        "Happy Birthday, [Name]! Wishing you a fantastic day and a wonderful year ahead!",
        "Have a wonderful birthday, [Name]! May all your dreams and wishes come true!",
        "Warmest wishes on your birthday, [Name]! May you have a great day filled with happiness!",
        "Happy Birthday to a wonderful student, [Name]! Wishing you a bright and beautiful year!",
        "Wishing you a joyful and blessed birthday, [Name]! Enjoy your special day to the fullest!",
        "Happy Birthday, [Name]! May your day be as special and amazing as you are!",
        "A very Happy Birthday to you, [Name]! Wishing you success, good health, and joy in all you do!",
        "Hope your birthday is full of fun and laughter, [Name]! Have an amazing celebration!",
        "Wishing you a memorable and happy birthday, [Name]! May this year be your best one yet!",
        "Happy Birthday, [Name]! May your life be filled with endless smiles and bright moments!",
        "Sending you warm smiles and best wishes, [Name], on your special day! Happy Birthday!",
        "Happy Birthday, [Name]! May today bring you lots of reasons to smile and be happy!",
        "Wishing you a very special birthday, [Name]! May your future be bright and full of wonderful opportunities!",
        "Happy Birthday, [Name]! Wishing you endless happiness, joy, and wonderful memories today!",
        "May this birthday be the start of a year filled with good luck, health, and much success, [Name]! Happy Birthday!",
        "Warmest birthday wishes, [Name]! Hope your day is filled with everything that makes you smile!",
        "Happy Birthday to an excellent student, [Name]! Keep shining bright and reaching for the stars!",
        "Wishing you a beautiful day of celebration, [Name]! May you have a wonderful year ahead!",
        "Happy Birthday, [Name]! May your special day bring you as much happiness as you bring to everyone around you!",
        "Sending you joy, laughter, and warm wishes on your special day, [Name]! Happy Birthday!",
        "Happy Birthday, [Name]! Hope your day is as bright and wonderful as your future is sure to be!",
        "Wishing you a very Happy Birthday, [Name]! May today be filled with joy and sweet surprises!",
        "Happy Birthday, [Name]! Wishing you a year ahead full of adventure, learning, and fun!",
        "Many happy returns to a wonderful student, [Name]! Have an awesome birthday celebration!",
        "Happy Birthday, [Name]! May all your hard work be rewarded with success and happiness this year!",
        "Wishing you a fantastic birthday, [Name]! May your day be packed with fun and special moments!",
        "Happy Birthday, [Name]! Wishing you a day of pure joy and a lifetime of happiness!",
        "Sending you my best wishes on your birthday, [Name]! Have a wonderful day full of celebration!"
    )

    private fun checkStudentBirthdays() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val classrooms = db.classroomDao().getAllClassroomsOnce()
            val birthdayStudents = mutableListOf<Pair<StudentProfile, String?>>()

            val calendar = java.util.Calendar.getInstance()
            val todayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val todayMonth = calendar.get(java.util.Calendar.MONTH) + 1
            val todayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            classrooms.forEach { classroom ->
                val birthdayEnabled = prefs.getBoolean("birthday_enabled_class_${classroom.id}", false)
                if (birthdayEnabled) {
                    val students = db.studentProfileDao().getAllStudentProfiles(classroom.id)
                    students.forEach { student ->
                        val fields = db.studentProfileFieldDao().getFieldsForStudent(classroom.id, student.admissionNumber)
                        
                        val dobField = fields.firstOrNull { 
                            val name = it.fieldName.lowercase()
                            name.contains("birth") || name.contains("dob")
                        }
                        
                        if (dobField != null) {
                            try {
                                val parts = dobField.fieldValue.split("/")
                                if (parts.size >= 2) {
                                    val dobDay = parts[0].trim().toInt()
                                    val dobMonth = parts[1].trim().toInt()
                                    
                                    if (dobDay == todayDay && dobMonth == todayMonth) {
                                        val keyStatus = "bday_status_${classroom.id}_${student.admissionNumber}_$todayFormat"
                                        val keyRemind = "bday_remind_${classroom.id}_${student.admissionNumber}_$todayFormat"
                                        
                                        val status = prefs.getString(keyStatus, "")
                                        val remindTime = prefs.getLong(keyRemind, 0L)
                                        
                                        val currentMs = System.currentTimeMillis()
                                        if (status != "dismissed" && status != "sent" && (status != "remind_later" || currentMs >= remindTime)) {
                                            val phoneField = fields.firstOrNull { 
                                                val name = it.fieldName.lowercase()
                                                name.contains("phone") || name.contains("mobile")
                                            }
                                            birthdayStudents.add(Pair(student, phoneField?.fieldValue))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            if (birthdayStudents.isNotEmpty()) {
                runOnUiThread {
                    showBirthdayPopupsSequentially(birthdayStudents, 0)
                }
            }
        }
    }

    private fun showBirthdayPopupsSequentially(list: List<Pair<StudentProfile, String?>>, index: Int) {
        if (index >= list.size) return

        val pair = list[index]
        val student = pair.first
        val rawPhone = pair.second

        val todayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val keyStatus = "bday_status_${student.classId}_${student.admissionNumber}_$todayFormat"
        val keyRemind = "bday_remind_${student.classId}_${student.admissionNumber}_$todayFormat"
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🎂 Happy Birthday!")
            .setMessage("Today is ${student.name}'s Birthday!\n\nWould you like to send a wish?")
            .setCancelable(false)
            .setPositiveButton("Send Wish") { _, _ ->
                prefs.edit().putString(keyStatus, "sent").apply()
                
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }

                val template = birthdayWishTemplates.random()
                val wish = template.replace("[Name]", student.name)
                
                if (!rawPhone.isNullOrEmpty()) {
                    var cleanPhone = rawPhone.filter { it.isDigit() }
                    if (cleanPhone.length == 10) {
                        cleanPhone = "91$cleanPhone"
                    }
                    val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(wish)}"
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(url)
                        })
                    } catch (e: Exception) {
                        Toast.makeText(this@HomeActivity, "WhatsApp not installed. Wish copied to clipboard.", Toast.LENGTH_LONG).show()
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Birthday Wish", wish))
                    }
                } else {
                    Toast.makeText(this@HomeActivity, "No parent phone number found. Wish copied to clipboard.", Toast.LENGTH_LONG).show()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Birthday Wish", wish))
                }

                showBirthdayPopupsSequentially(list, index + 1)
            }
            .setNegativeButton("Remind Me Later") { d, _ ->
                val nextPromptTime = System.currentTimeMillis() + 3600000L
                prefs.edit().putString(keyStatus, "remind_later").putLong(keyRemind, nextPromptTime).apply()
                d.dismiss()
                showBirthdayPopupsSequentially(list, index + 1)
            }
            .setNeutralButton("Dismiss") { d, _ ->
                prefs.edit().putString(keyStatus, "dismissed").apply()
                d.dismiss()
                showBirthdayPopupsSequentially(list, index + 1)
            }
            .create()

        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).contentDescription = "Dismiss"
        
        binding.root.announceForAccessibility("Birthday Alert dialog. Today is ${student.name}'s birthday. Would you like to send a wish? Select Send Wish, Remind Me Later, or Dismiss.")
    }

}