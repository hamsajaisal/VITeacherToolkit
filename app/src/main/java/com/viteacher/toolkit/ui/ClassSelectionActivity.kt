package com.viteacher.toolkit.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Classroom
import com.viteacher.toolkit.databinding.ActivityClassSelectionBinding
import kotlinx.coroutines.launch

class ClassSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassSelectionBinding
    private var classroomList = mutableListOf<Classroom>()
    private lateinit var selectionAdapter: ClassSelectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vi_teacher_prefs", MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        if (isCollege) {
            binding.tvTitle.text = "Select Program"
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        loadClassrooms()
    }

    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vi_teacher_prefs", MODE_PRIVATE)
        val isCollege = prefs.getString("institution_type", "school") == "college"
        selectionAdapter = ClassSelectionAdapter(classroomList, isCollege) { classroom ->
            val intent = Intent(this, MyClassActivity::class.java).apply {
                putExtra("class_id", classroom.id)
            }
            startActivity(intent)
            finish() // Finish so back button goes directly to home
        }
        binding.rvClassSelection.layoutManager = LinearLayoutManager(this)
        binding.rvClassSelection.adapter = selectionAdapter
    }

    private fun loadClassrooms() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val dbClassrooms = db.classroomDao().getAllClassroomsOnce()
            runOnUiThread {
                classroomList.clear()
                classroomList.addAll(dbClassrooms)
                selectionAdapter.notifyDataSetChanged()
                val prefs = getSharedPreferences("vi_teacher_prefs", MODE_PRIVATE)
                val isCollege = prefs.getString("institution_type", "school") == "college"
                val announceText = if (isCollege) {
                    "Program Selection Screen. You have ${classroomList.size} programs available."
                } else {
                    "Class Selection Screen. You have ${classroomList.size} classes available."
                }
                binding.root.announceForAccessibility(announceText)
            }
        }
    }
}

class ClassSelectionAdapter(
    private val list: List<Classroom>,
    private val isCollege: Boolean,
    private val onItemClick: (Classroom) -> Unit
) : RecyclerView.Adapter<ClassSelectionAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvClassName)
        val tvDetails: TextView = view.findViewById(R.id.tvClassDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context).inflate(R.layout.item_class_selection, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        val nameText = if (isCollege) "${item.standard} ${item.division}" else "Class ${item.standard}${item.division}"
        holder.tvName.text = nameText

        val typeDisplay = when (item.attendanceType) {
            "DoubleSession" -> "Double Session"
            "OnceADay" -> "Daily Session"
            else -> "Hour-wise (${item.totalHours} Hours)"
        }
        holder.tvDetails.text = "Academic Year: ${item.academicYear} | $typeDisplay"

        val selectPrefix = if (isCollege) "Select Program" else "Select Class"
        val spaceOrNot = if (isCollege) " " else ""
        holder.view.contentDescription = "$selectPrefix ${item.standard}$spaceOrNot${item.division}, Academic Year ${item.academicYear}, $typeDisplay"
        holder.view.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = list.size
}
