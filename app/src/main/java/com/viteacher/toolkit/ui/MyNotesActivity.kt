package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Category
import com.viteacher.toolkit.data.Note
import com.viteacher.toolkit.databinding.ActivityMyNotesBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MyNotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyNotesBinding
    private lateinit var noteAdapter: NoteAdapter
    private var allNotes: List<Note> = emptyList()
    private var allCategories: List<String> = listOf("All", "General")
    private var selectedCategory = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setupCursorEndForEditTexts()

        // Ensure default "General" category exists
        ensureGeneralCategory()

        // Set up recyclerview
        noteAdapter = NoteAdapter(emptyList(),
            onEdit = { note -> editNote(note) },
            onPinToggle = { note -> togglePin(note) },
            onShare = { note -> shareNote(note) },
            onDelete = { note -> confirmDeleteNote(note) }
        )
        binding.rvNotes.layoutManager = LinearLayoutManager(this)
        binding.rvNotes.adapter = noteAdapter

        // Set up click listeners
        binding.btnCreateNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }

        binding.btnCreateCategory.setOnClickListener {
            showCreateCategoryDialog()
        }

        binding.btnFilterCategory.setOnClickListener {
            showCategorySelectionDialog()
        }

        // Fetch data
        loadNotes()
        loadCategories()
    }

    private fun ensureGeneralCategory() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val general = db.categoryDao().getCategoryByName("General")
            if (general == null) {
                db.categoryDao().insertCategory(Category(name = "General"))
            }
        }
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .noteDao()
                .getAllNotesFlow()
                .collectLatest { notes ->
                    allNotes = notes
                    applyFilter()
                }
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .categoryDao()
                .getAllCategoriesFlow()
                .collectLatest { categories ->
                    val catList = mutableListOf("All")
                    categories.forEach { catList.add(it.name) }
                    if (!catList.contains("General")) {
                        catList.add("General")
                    }
                    allCategories = catList
                    
                    // Make sure the selected category still exists, otherwise reset to All
                    if (!allCategories.contains(selectedCategory)) {
                        selectedCategory = "All"
                    }
                    
                    runOnUiThread {
                        binding.btnFilterCategory.text = selectedCategory
                        binding.btnFilterCategory.contentDescription = "Filter notes by category, current filter is $selectedCategory, double tap to change"
                    }
                    applyFilter()
                }
        }
    }

    private fun showCategorySelectionDialog() {
        var dialog: AlertDialog? = null
        
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, allCategories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val category = allCategories[position]
                
                view.setOnClickListener {
                    dialog?.dismiss()
                    selectedCategory = category
                    binding.btnFilterCategory.text = category
                    binding.btnFilterCategory.contentDescription = "Filter notes by category, current filter is $category, double tap to change"
                    binding.root.announceForAccessibility("Filter changed to $category")
                    applyFilter()
                }
                
                view.setOnLongClickListener {
                    dialog?.dismiss()
                    showCategoryOptionsDialog(category)
                    true
                }
                
                // Accessibility descriptions for TalkBack
                if (category.equals("General", ignoreCase = true) || category.equals("All", ignoreCase = true)) {
                    view.contentDescription = "$category category"
                } else {
                    view.contentDescription = "$category category, double tap to filter, double tap and hold for options"
                }
                
                return view
            }
        }
        
        dialog = AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setAdapter(adapter, null)
            .create()
            
        dialog.show()
    }

    private fun applyFilter() {
        val filtered = if (selectedCategory == "All") {
            allNotes
        } else {
            allNotes.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        noteAdapter.updateList(filtered)
    }

    private fun showCreateCategoryDialog() {
        val input = EditText(this)
        input.setupCursorEndForEditTexts()
        input.hint = "Example: Maths"
        input.contentDescription = "Category name, required"

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(48, 16, 48, 16)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Create New Category")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveCategory(name)
                } else {
                    Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Category creation cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Create new category dialog. Type a category name, then choose Save or Cancel.")
    }

    private fun saveCategory(name: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val existing = db.categoryDao().getCategoryByName(name)
            if (existing != null) {
                runOnUiThread {
                    val msg = "Category '$name' already exists"
                    Toast.makeText(this@MyNotesActivity, msg, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(msg)
                }
                return@launch
            }

            db.categoryDao().insertCategory(Category(name = name))
            runOnUiThread {
                val msg = "Category $name created successfully"
                Toast.makeText(this@MyNotesActivity, msg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(msg)
            }
        }
    }

    private fun showCategoryOptionsDialog(categoryName: String) {
        if (categoryName.equals("General", ignoreCase = true) || categoryName.equals("All", ignoreCase = true)) {
            Toast.makeText(this, "The General category cannot be renamed or deleted", Toast.LENGTH_SHORT).show()
            binding.root.announceForAccessibility("The General category cannot be renamed or deleted")
            return
        }

        val options = arrayOf("Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Category Options: $categoryName")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showRenameCategoryDialog(categoryName)
                } else {
                    confirmDeleteCategory(categoryName)
                }
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Category options for $categoryName. Choose Rename or Delete.")
    }

    private fun showRenameCategoryDialog(oldName: String) {
        val input = EditText(this)
        input.setupCursorEndForEditTexts()
        input.setText(oldName)
        input.contentDescription = "New category name, required"
        input.setSelection(oldName.length)

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(48, 16, 48, 16)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Rename Category")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    renameCategory(oldName, newName)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Rename cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Rename category dialog. Type new name and select Save or Cancel.")
    }

    private fun renameCategory(oldName: String, newName: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val existing = db.categoryDao().getCategoryByName(newName)
            if (existing != null) {
                runOnUiThread {
                    Toast.makeText(this@MyNotesActivity, "Category '$newName' already exists", Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility("Category already exists")
                }
                return@launch
            }

            val category = db.categoryDao().getCategoryByName(oldName)
            if (category != null) {
                db.categoryDao().updateCategory(category.copy(name = newName))
                val notes = db.noteDao().getAllNotesOnce()
                for (note in notes) {
                    if (note.category.equals(oldName, ignoreCase = true)) {
                        db.noteDao().updateNote(note.copy(category = newName))
                    }
                }
                if (selectedCategory.equals(oldName, ignoreCase = true)) {
                    selectedCategory = newName
                    runOnUiThread {
                        binding.btnFilterCategory.text = newName
                        binding.btnFilterCategory.contentDescription = "Filter notes by category, current filter is $newName, double tap to change"
                    }
                }
                runOnUiThread {
                    val message = "Category renamed to $newName"
                    Toast.makeText(this@MyNotesActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                }
            }
        }
    }

    private fun confirmDeleteCategory(categoryName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Deleting this category will move all its notes to General. Do you want to continue?")
            .setPositiveButton("Yes") { _, _ ->
                deleteCategory(categoryName)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Deletion cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility(
            "Delete category dialog. Deleting this category will move all its notes to General. Do you want to continue? Select Yes or No."
        )
    }

    private fun deleteCategory(categoryName: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val category = db.categoryDao().getCategoryByName(categoryName)
            if (category != null) {
                db.categoryDao().deleteCategory(category)
                val notes = db.noteDao().getAllNotesOnce()
                for (note in notes) {
                    if (note.category.equals(categoryName, ignoreCase = true)) {
                        db.noteDao().updateNote(note.copy(category = "General"))
                    }
                }
                if (selectedCategory.equals(categoryName, ignoreCase = true)) {
                    selectedCategory = "All"
                    runOnUiThread {
                        binding.btnFilterCategory.text = "All"
                        binding.btnFilterCategory.contentDescription = "Filter notes by category, current filter is All, double tap to change"
                    }
                }
                runOnUiThread {
                    val message = "Category $categoryName deleted. Notes migrated to General."
                    Toast.makeText(this@MyNotesActivity, message, Toast.LENGTH_SHORT).show()
                    binding.root.announceForAccessibility(message)
                }
            }
        }
    }

    private fun editNote(note: Note) {
        val intent = Intent(this, NoteEditorActivity::class.java)
        intent.putExtra("note_id", note.id)
        startActivity(intent)
    }

    private fun togglePin(note: Note) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val newPinned = !note.isPinned
            db.noteDao().updateNote(note.copy(isPinned = newPinned, lastEdited = System.currentTimeMillis()))
            runOnUiThread {
                val action = if (newPinned) "pinned" else "unpinned"
                val message = "${note.title} $action successfully"
                Toast.makeText(this@MyNotesActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
        }
    }

    private fun shareNote(note: Note) {
        val plainText = HtmlCompat.fromHtml(note.content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, plainText)
            putExtra(Intent.EXTRA_TITLE, note.title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share note via")
        startActivity(shareIntent)
        binding.root.announceForAccessibility("Sharing note ${note.title}")
    }

    private fun confirmDeleteNote(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Yes") { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Delete cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Are you sure you want to delete this note? Choose Yes or No.")
    }

    private fun deleteNote(note: Note) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext).noteDao().deleteNote(note)
            runOnUiThread {
                val message = "${note.title} deleted successfully"
                Toast.makeText(this@MyNotesActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
        }
    }

    private fun showRenameNoteDialog(note: Note) {
        val input = EditText(this)
        input.setupCursorEndForEditTexts()
        input.setText(note.title)
        input.contentDescription = "New note title, required"
        input.setSelection(note.title.length)

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(48, 16, 48, 16)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Rename Note")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    renameNoteInDb(note, newTitle)
                } else {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Rename cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Rename note dialog. Type new name and select Save or Cancel.")
    }

    private fun renameNoteInDb(note: Note, newTitle: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.noteDao().updateNote(note.copy(title = newTitle, lastEdited = System.currentTimeMillis()))
            runOnUiThread {
                val message = "Note renamed to $newTitle"
                Toast.makeText(this@MyNotesActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
        }
    }

    // RecyclerView Note title list adapter
    inner class NoteAdapter(
        private var notes: List<Note>,
        private val onEdit: (Note) -> Unit,
        private val onPinToggle: (Note) -> Unit,
        private val onShare: (Note) -> Unit,
        private val onDelete: (Note) -> Unit
    ) : RecyclerView.Adapter<NoteAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivPin: ImageView = itemView.findViewById(R.id.ivPinIndicator)
            val tvTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
            val btnOptions: ImageButton = itemView.findViewById(R.id.btnNoteOptions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = notes[position]
            holder.tvTitle.text = note.title
            
            if (note.isPinned) {
                holder.ivPin.visibility = View.VISIBLE
                holder.itemView.contentDescription = "Pinned Note: ${note.title}"
            } else {
                holder.ivPin.visibility = View.GONE
                holder.itemView.contentDescription = "Note: ${note.title}"
            }

            holder.itemView.setOnClickListener { onEdit(note) }
            
            // Long press also triggers options
            holder.itemView.setOnLongClickListener {
                showNoteOptionsPopup(holder.btnOptions, note)
                true
            }

            holder.btnOptions.contentDescription = "Options for ${note.title} note"
            holder.btnOptions.setOnClickListener {
                showNoteOptionsPopup(holder.btnOptions, note)
            }
        }

        private fun showNoteOptionsPopup(anchor: View, note: Note) {
            val options = arrayOf(
                "Edit",
                "Rename",
                if (note.isPinned) "Unpin" else "Pin",
                "Share",
                "Delete"
            )
            AlertDialog.Builder(this@MyNotesActivity)
                .setTitle("Note: ${note.title}")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> onEdit(note)
                        1 -> showRenameNoteDialog(note)
                        2 -> onPinToggle(note)
                        3 -> onShare(note)
                        4 -> onDelete(note)
                    }
                }
                .create()
                .show()
            binding.root.announceForAccessibility("Options for note ${note.title}. Choose Edit, Rename, Pin or Unpin, Share, or Delete.")
        }

        override fun getItemCount() = notes.size

        fun updateList(newNotes: List<Note>) {
            notes = newNotes
            notifyDataSetChanged()
        }
    }
}
