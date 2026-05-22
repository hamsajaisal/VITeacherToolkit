package com.viteacher.toolkit.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.viteacher.toolkit.databinding.ActivityClassroomSettingsBinding

class ClassroomSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassroomSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadClassSettings()
        setupFocusAutoScroll()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSaveClassroomSettings.setOnClickListener {
            saveClassSettings()
        }
    }

    private fun loadClassSettings() {
        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        binding.etClassStandard.setText(prefs.getString("class_standard", ""))
        binding.etClassDivision.setText(prefs.getString("class_division", ""))
        binding.etClassAcademicYear.setText(prefs.getString("class_academic_year", ""))
    }

    private fun saveClassSettings() {
        val standard = binding.etClassStandard.text.toString().trim()
        val division = binding.etClassDivision.text.toString().trim()
        val academicYear = binding.etClassAcademicYear.text.toString().trim()

        if (standard.isEmpty()) {
            binding.etClassStandard.error = "Please enter standard or class"
            binding.etClassStandard.requestFocus()
            binding.root.announceForAccessibility("Error: Standard or Class field is empty. Please enter class.")
            return
        }

        if (division.isEmpty()) {
            binding.etClassDivision.error = "Please enter division"
            binding.etClassDivision.requestFocus()
            binding.root.announceForAccessibility("Error: Division field is empty. Please enter division.")
            return
        }

        if (academicYear.isEmpty()) {
            binding.etClassAcademicYear.error = "Please enter academic year"
            binding.etClassAcademicYear.requestFocus()
            binding.root.announceForAccessibility("Error: Academic year field is empty. Please enter academic year.")
            return
        }

        val prefs = getSharedPreferences("vi_teacher_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("class_standard", standard)
            .putString("class_division", division)
            .putString("class_academic_year", academicYear)
            .apply()

        val message = "Classroom settings saved successfully"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility(message)
        finish()
    }

    private fun setupFocusAutoScroll() {
        // Handle keyboard "Next" and "Done" actions programmatically
        binding.etClassStandard.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etClassDivision.requestFocus()
                true
            } else false
        }

        binding.etClassDivision.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etClassAcademicYear.requestFocus()
                true
            } else false
        }

        binding.etClassAcademicYear.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // Clear focus, hide keyboard, and save settings
                binding.etClassAcademicYear.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etClassAcademicYear.windowToken, 0)
                saveClassSettings()
                true
            } else false
        }

        // Handle auto-scrolling on focus changes to bring views in full sight when keyboard opens
        // Also explicitly request accessibility focus and announce the field to TalkBack.
        binding.etClassStandard.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    binding.root.smoothScrollTo(0, 0)
                    binding.etClassStandard.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null
                    )
                }, 300)
            }
        }

        binding.etClassDivision.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    // Scroll so that the Division field is focused at the top of shrunken ScrollView area
                    binding.root.smoothScrollTo(0, binding.etClassDivision.top - 20)
                    binding.etClassDivision.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null
                    )
                }, 300)
            }
        }

        binding.etClassAcademicYear.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    // Scroll down to make academic year and Save button fully visible
                    binding.root.fullScroll(View.FOCUS_DOWN)
                    binding.etClassAcademicYear.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null
                    )
                }, 300)
            }
        }

        // Set up layout change listener to handle dynamic resizing when soft keyboard opens/closes.
        // This guarantees that if the system's focus scroller resets coordinates during IME resize,
        // we force scroll to the correct positions to keep subsequent fields visible above the keyboard.
        binding.root.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                val focusedView = currentFocus
                if (focusedView != null) {
                    binding.root.post {
                        when (focusedView.id) {
                            binding.etClassStandard.id -> {
                                binding.root.smoothScrollTo(0, 0)
                            }
                            binding.etClassDivision.id -> {
                                binding.root.smoothScrollTo(0, binding.etClassDivision.top - 20)
                            }
                            binding.etClassAcademicYear.id -> {
                                binding.root.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }
            }
        }

        // Set up accessibility delegate to handle swiping in TalkBack.
        // We synchronize Accessibility Focus (TalkBack) with Input Focus (Keyboard).
        // When TalkBack focuses an input view, we request input focus on it to trigger automatic scrolling and announcements.
        // When TalkBack focuses the Save button or Back button, we automatically dismiss the soft keyboard to restore full viewport.
        val accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                super.sendAccessibilityEvent(host, eventType)
                if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    when (host.id) {
                        binding.etClassStandard.id, binding.etClassDivision.id, binding.etClassAcademicYear.id -> {
                            if (!host.isFocused) {
                                host.requestFocus()
                            }
                        }
                        binding.btnSaveClassroomSettings.id -> {
                            // Automatically hide the soft keyboard when the user swipes to the Save button
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(host.windowToken, 0)
                            // Scroll down to make sure the Save button is fully visible
                            binding.root.post {
                                binding.root.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                        binding.btnBack.id -> {
                            // Automatically hide keyboard when swiping to Back button
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(host.windowToken, 0)
                        }
                    }
                }
            }
        }

        binding.etClassStandard.accessibilityDelegate = accessibilityDelegate
        binding.etClassDivision.accessibilityDelegate = accessibilityDelegate
        binding.etClassAcademicYear.accessibilityDelegate = accessibilityDelegate
        binding.btnSaveClassroomSettings.accessibilityDelegate = accessibilityDelegate
        binding.btnBack.accessibilityDelegate = accessibilityDelegate
    }
}
