package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.databinding.ActivityClassroomSettingsBinding
import kotlinx.coroutines.launch

class ClassroomSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassroomSettingsBinding
    private var classroomList = mutableListOf<Classroom>()
    private lateinit var classroomAdapter: ClassroomAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupAttendanceTypeSpinner()
        loadClassrooms()
        setupFocusAutoScroll()

        binding.btnBack.setOnClickListener {
            if (binding.layoutAddClassForm.visibility == View.VISIBLE && classroomList.isNotEmpty()) {
                switchToListView()
            } else {
                finish()
            }
        }

        binding.btnAddClass.setOnClickListener {
            switchToFormView()
        }

        binding.btnCancelAddClass.setOnClickListener {
            switchToListView()
        }

        binding.btnSaveClassroomSettings.setOnClickListener {
            saveClassroom()
        }
    }

    private fun setupRecyclerView() {
        classroomAdapter = ClassroomAdapter(classroomList) { classroom ->
            showDeleteConfirmation(classroom)
        }
        binding.rvClassrooms.layoutManager = LinearLayoutManager(this)
        binding.rvClassrooms.adapter = classroomAdapter
    }

    private fun setupAttendanceTypeSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Forenoon & Afternoon (Double Session)", "Once a Day (Daily)", "Hour-wise (Periods)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spAttendanceType.adapter = adapter

        binding.spAttendanceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 2) {
                    binding.layoutHourCountInput.visibility = View.VISIBLE
                } else {
                    binding.layoutHourCountInput.visibility = View.GONE
                    binding.etTotalHours.setText("")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadClassrooms() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbClassrooms = db.classroomDao().getAllClassroomsOnce()
            runOnUiThread {
                classroomList.clear()
                classroomList.addAll(dbClassrooms)
                classroomAdapter.notifyDataSetChanged()

                if (classroomList.isEmpty()) {
                    switchToFormView(showCancel = false)
                } else {
                    switchToListView()
                }
            }
        }
    }

    private fun switchToListView() {
        binding.layoutClassroomList.visibility = View.VISIBLE
        binding.layoutAddClassForm.visibility = View.GONE
        binding.tvTitle.text = "Classrooms List"
        binding.root.announceForAccessibility("Classrooms List loaded. You have ${classroomList.size} classes configured.")
    }

    private fun switchToFormView(showCancel: Boolean = true) {
        binding.layoutClassroomList.visibility = View.GONE
        binding.layoutAddClassForm.visibility = View.VISIBLE
        binding.tvTitle.text = "Add Classroom"
        
        binding.etClassStandard.setText("")
        binding.etClassDivision.setText("")
        binding.etClassAcademicYear.setText("")
        binding.spAttendanceType.setSelection(0)
        binding.etTotalHours.setText("")
        binding.layoutHourCountInput.visibility = View.GONE

        binding.btnCancelAddClass.visibility = if (showCancel && classroomList.isNotEmpty()) View.VISIBLE else View.GONE
        binding.root.announceForAccessibility("Add Classroom form opened. Please enter class standard, division, academic year, and select attendance type.")
    }

    private fun saveClassroom() {
        val standard = binding.etClassStandard.text.toString().trim()
        val division = binding.etClassDivision.text.toString().trim()
        val academicYear = binding.etClassAcademicYear.text.toString().trim()
        val spinnerPosition = binding.spAttendanceType.selectedItemPosition

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

        val type = when (spinnerPosition) {
            0 -> "DoubleSession"
            1 -> "OnceADay"
            else -> "HourWise"
        }

        var totalHours = 0
        if (type == "HourWise") {
            val hoursStr = binding.etTotalHours.text.toString().trim()
            if (hoursStr.isEmpty()) {
                binding.etTotalHours.error = "Please enter number of hours per day"
                binding.etTotalHours.requestFocus()
                binding.root.announceForAccessibility("Error: Hours field is empty. Please enter number of hours per day.")
                return
            }
            try {
                totalHours = hoursStr.toInt()
                if (totalHours <= 0) {
                    throw NumberFormatException()
                }
            } catch (e: Exception) {
                binding.etTotalHours.error = "Please enter a valid positive number"
                binding.etTotalHours.requestFocus()
                binding.root.announceForAccessibility("Error: Invalid hours number. Please enter a valid positive number.")
                return
            }
        }

        lifecycleScope.launch {
            val classroom = Classroom(
                standard = standard,
                division = division,
                academicYear = academicYear,
                attendanceType = type,
                totalHours = totalHours
            )
            val db = AppDatabase.getDatabase(applicationContext)
            db.classroomDao().insertClassroom(classroom)

            runOnUiThread {
                val successMessage = "Class ${standard}${division} saved successfully"
                Toast.makeText(this@ClassroomSettingsActivity, successMessage, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(successMessage)
                
                // Refresh class visibility and reload
                loadClassrooms()
            }
        }
    }

    private fun showDeleteConfirmation(classroom: Classroom) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Class")
            .setMessage("Are you sure you want to delete Class ${classroom.standard}${classroom.division}? This will delete all its student lists and historical attendance records. This cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                deleteClass(classroom)
            }
            .setNegativeButton("No") { d, _ ->
                d.dismiss()
                binding.root.announceForAccessibility("Delete cancelled.")
            }
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "Yes button, confirm delete"
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "No button, cancel delete"
        binding.root.announceForAccessibility("Warning dialog. Are you sure you want to delete Class ${classroom.standard}${classroom.division}? Select Yes or No.")
    }

    private fun deleteClass(classroom: Classroom) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.classroomDao().deleteClassroom(classroom)
            db.studentDao().deleteAllStudents(classroom.id)
            db.attendanceDao().deleteAttendanceByClassId(classroom.id)

            runOnUiThread {
                val deleteMsg = "Class ${classroom.standard}${classroom.division} deleted successfully"
                Toast.makeText(this@ClassroomSettingsActivity, deleteMsg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(deleteMsg)
                loadClassrooms()
            }
        }
    }

    private fun setupFocusAutoScroll() {
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

        binding.etClassAcademicYear.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    binding.root.smoothScrollTo(0, binding.etClassAcademicYear.top - 20)
                    binding.etClassAcademicYear.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null
                    )
                }, 300)
            }
        }

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
                    binding.root.smoothScrollTo(0, binding.etClassDivision.top - 20)
                    binding.etClassDivision.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null
                    )
                }, 300)
            }
        }

        // Resizing scroll force logic
        binding.root.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                val focusedView = currentFocus
                if (focusedView != null) {
                    binding.root.post {
                        when (focusedView.id) {
                            binding.etClassStandard.id -> binding.root.smoothScrollTo(0, 0)
                            binding.etClassDivision.id -> binding.root.smoothScrollTo(0, binding.etClassDivision.top - 20)
                            binding.etClassAcademicYear.id -> binding.root.smoothScrollTo(0, binding.etClassAcademicYear.top - 20)
                        }
                    }
                }
            }
        }

        // Accessibility swiping and keyboard dismissals
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
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(host.windowToken, 0)
                            binding.root.post {
                                binding.root.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                        binding.btnBack.id -> {
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

// Inner ClassroomAdapter class
class ClassroomAdapter(
    private val list: List<Classroom>,
    private val onDeleteClick: (Classroom) -> Unit
) : RecyclerView.Adapter<ClassroomAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvClassName)
        val tvDetails: TextView = view.findViewById(R.id.tvClassDetails)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteClass)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_classroom, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvName.text = "Class ${item.standard}${item.division}"

        val systemDisplay = when (item.attendanceType) {
            "DoubleSession" -> "Double Session"
            "OnceADay" -> "Daily Session"
            else -> "Hour-wise (${item.totalHours} Hours)"
        }
        holder.tvDetails.text = "Academic Year: ${item.academicYear} | $systemDisplay"

        // Roster card TalkBack traversal
        holder.view.contentDescription = "Class ${item.standard}${item.division}, Academic Year ${item.academicYear}, $systemDisplay"

        holder.btnDelete.contentDescription = "Delete Class ${item.standard}${item.division} button"
        holder.btnDelete.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = list.size
}
