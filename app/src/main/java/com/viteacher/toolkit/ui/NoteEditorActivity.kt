package com.viteacher.toolkit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Category
import com.viteacher.toolkit.data.Note
import com.viteacher.toolkit.databinding.ActivityNoteEditorBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private var noteId: Int = 0
    private var currentFontSize = 18
    private var selectedCategory = "General"
    private var isPinned = false
    private var noteTitle = ""
    private var lastSavedContent: String = ""
    private var autoSaveJob: Job? = null
    
    // Category list for Spinner inside Save Note dialog
    private var categories: List<String> = listOf("General")

    // Text Alignment states
    private var alignmentIndex = 0
    private val alignments = listOf(Layout.Alignment.ALIGN_NORMAL, Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_OPPOSITE)
    private val alignmentNames = listOf("left", "center", "right")

    // Register Advanced Edit result contract
    private val advancedEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val html = result.data?.getStringExtra("formatted_html")
            if (html != null) {
                binding.etNoteContent.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
                binding.root.announceForAccessibility("Advanced formatting applied back to editor")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read intent extras
        noteId = intent.getIntExtra("note_id", 0)

        // Set up click handlers
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnAdvancedEdit.setOnClickListener {
            openAdvancedEditMode()
        }

        binding.btnMoreOptions.setOnClickListener {
            showEditorOptionsMenu()
        }

        setupFontSizeControls()
        setupFormattingToolbar()

        // Load existing note if editing
        if (noteId != 0) {
            loadNoteData()
        } else {
            binding.etNoteContent.setText("")
            lastSavedContent = ""
        }

        // Fetch categories list for dialog
        loadCategories()

        // Start Auto-Save Timer
        startAutoSaveTimer()

        // Handle Back Press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentText = binding.etNoteContent.text.toString()
                val currentHtml = HtmlCompat.toHtml(binding.etNoteContent.text, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                if (currentHtml != lastSavedContent && currentText.isNotEmpty()) {
                    showDiscardWarningDialog()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveJob?.cancel()
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .categoryDao()
                .getAllCategoriesFlow()
                .collectLatest { catList ->
                    val names = catList.map { it.name }.toMutableList()
                    if (!names.contains("General")) {
                        names.add("General")
                    }
                    categories = names
                }
        }
    }

    private fun loadNoteData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val note = db.noteDao().getNoteById(noteId)
            if (note != null) {
                runOnUiThread {
                    noteTitle = note.title
                    selectedCategory = note.category
                    isPinned = note.isPinned
                    currentFontSize = note.fontSize
                    binding.tvFontSizeLabel.text = "Font Size: $currentFontSize"
                    binding.etNoteContent.textSize = currentFontSize.toFloat()
                    
                    val spanned = HtmlCompat.fromHtml(note.content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    binding.etNoteContent.setText(spanned)
                    lastSavedContent = note.content
                }
            }
        }
    }

    private fun setupFontSizeControls() {
        binding.tvFontSizeLabel.text = "Font Size: $currentFontSize"
        binding.etNoteContent.textSize = currentFontSize.toFloat()

        binding.btnIncreaseFontSize.setOnClickListener {
            changeFontSize(1)
        }

        binding.btnIncreaseFontSize.setOnLongClickListener {
            changeFontSize(2)
            true
        }

        binding.btnDecreaseFontSize.setOnClickListener {
            changeFontSize(-1)
        }

        binding.btnDecreaseFontSize.setOnLongClickListener {
            changeFontSize(-2)
            true
        }
    }

    private fun changeFontSize(delta: Int) {
        val newSize = (currentFontSize + delta).coerceIn(12, 48)
        if (newSize != currentFontSize) {
            currentFontSize = newSize
            binding.tvFontSizeLabel.text = "Font Size: $currentFontSize"
            binding.etNoteContent.textSize = currentFontSize.toFloat()
            val text = if (delta > 0) "increased to $currentFontSize" else "decreased to $currentFontSize"
            binding.root.announceForAccessibility("Font size $text")
        }
    }

    private fun setupFormattingToolbar() {
        binding.btnBold.setOnClickListener {
            triggerHapticFeedback(it)
            toggleStyleSpan(Typeface.BOLD, "Bold")
        }

        binding.btnItalic.setOnClickListener {
            triggerHapticFeedback(it)
            toggleStyleSpan(Typeface.ITALIC, "Italic")
        }

        binding.btnUnderline.setOnClickListener {
            triggerHapticFeedback(it)
            toggleSpan(UnderlineSpan(), "Underline")
        }

        binding.btnStrikethrough.setOnClickListener {
            triggerHapticFeedback(it)
            toggleSpan(StrikethroughSpan(), "Strikethrough")
        }

        binding.btnHighlight.setOnClickListener {
            triggerHapticFeedback(it)
            openHighlightPicker()
        }

        binding.btnTextColor.setOnClickListener {
            triggerHapticFeedback(it)
            openTextColorPicker()
        }

        binding.btnBulletList.setOnClickListener {
            triggerHapticFeedback(it)
            applyListFormatting(isNumbered = false)
        }

        binding.btnNumberedList.setOnClickListener {
            triggerHapticFeedback(it)
            applyListFormatting(isNumbered = true)
        }

        binding.btnAlignment.setOnClickListener {
            triggerHapticFeedback(it)
            cycleAlignment()
        }

        binding.btnAllCaps.setOnClickListener {
            triggerHapticFeedback(it)
            applyAllCaps()
        }
    }

    private fun triggerHapticFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun toggleStyleSpan(style: Int, name: String) {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        if (actualStart == actualEnd && editable.isEmpty()) return

        val spans = editable.getSpans(actualStart, actualEnd, StyleSpan::class.java)
        var removed = false
        for (span in spans) {
            if (span.style == style) {
                editable.removeSpan(span)
                removed = true
            }
        }
        if (!removed) {
            editable.setSpan(StyleSpan(style), actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.root.announceForAccessibility("$name applied")
        } else {
            binding.root.announceForAccessibility("$name removed")
        }
    }

    private fun toggleSpan(spanToToggle: Any, name: String) {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        if (actualStart == actualEnd && editable.isEmpty()) return

        val spans = editable.getSpans(actualStart, actualEnd, spanToToggle::class.java)
        if (spans.isNotEmpty()) {
            spans.forEach { editable.removeSpan(it) }
            binding.root.announceForAccessibility("$name removed")
        } else {
            editable.setSpan(spanToToggle, actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.root.announceForAccessibility("$name applied")
        }
    }

    private fun openHighlightPicker() {
        val colors = listOf(Color.YELLOW, Color.GREEN, Color.parseColor("#FFC0CB")/*Pink*/, Color.CYAN/*Blue*/)
        val colorNames = arrayOf("Yellow highlight", "Green highlight", "Pink highlight", "Blue highlight")

        AlertDialog.Builder(this)
            .setTitle("Select Highlight Color")
            .setItems(colorNames) { _, which ->
                applyHighlightColor(colors[which], colorNames[which])
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Highlight color options. Select Yellow highlight, Green highlight, Pink highlight, or Blue highlight.")
    }

    private fun applyHighlightColor(color: Int, name: String) {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        editable.setSpan(BackgroundColorSpan(color), actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.root.announceForAccessibility("$name applied")
    }

    private fun openTextColorPicker() {
        val colors = listOf(Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.parseColor("#800080")/*Purple*/)
        val colorNames = arrayOf("Black text color", "Red text color", "Blue text color", "Green text color", "Purple text color")

        AlertDialog.Builder(this)
            .setTitle("Select Text Color")
            .setItems(colorNames) { _, which ->
                applyTextColor(colors[which], colorNames[which])
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Text color options. Select Black, Red, Blue, Green, or Purple.")
    }

    private fun applyTextColor(color: Int, name: String) {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        editable.setSpan(ForegroundColorSpan(color), actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.root.announceForAccessibility("$name applied")
    }

    private fun applyListFormatting(isNumbered: Boolean) {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        val textToFormat = editable.substring(actualStart, actualEnd)
        val lines = textToFormat.split("\n")
        val formatted = StringBuilder()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isNotEmpty()) {
                if (isNumbered) {
                    formatted.append("${i + 1}. $line")
                } else {
                    formatted.append("• $line")
                }
            } else {
                formatted.append(line)
            }
            if (i < lines.size - 1) formatted.append("\n")
        }
        editable.replace(actualStart, actualEnd, formatted.toString())
        val name = if (isNumbered) "Numbered list" else "Bullet list"
        binding.root.announceForAccessibility("$name applied")
    }

    private fun cycleAlignment() {
        alignmentIndex = (alignmentIndex + 1) % alignments.size
        val alignment = alignments[alignmentIndex]
        val name = alignmentNames[alignmentIndex]

        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        editable.setSpan(AlignmentSpan.Standard(alignment), actualStart, actualEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.root.announceForAccessibility("Text alignment changed to $name")
    }

    private fun applyAllCaps() {
        val editable = binding.etNoteContent.text
        val start = binding.etNoteContent.selectionStart
        val end = binding.etNoteContent.selectionEnd
        val actualStart = if (start != end) minOf(start, end) else 0
        val actualEnd = if (start != end) maxOf(start, end) else editable.length

        if (actualStart == actualEnd && editable.isEmpty()) return

        val text = editable.substring(actualStart, actualEnd)
        editable.replace(actualStart, actualEnd, text.uppercase())
        binding.root.announceForAccessibility("All caps applied")
    }

    private fun startAutoSaveTimer() {
        autoSaveJob = lifecycleScope.launch {
            while (isActive) {
                delay(30000) // 30 seconds
                val currentHtml = HtmlCompat.toHtml(binding.etNoteContent.text, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                if (currentHtml != lastSavedContent && binding.etNoteContent.text.isNotEmpty()) {
                    autoSaveNote(currentHtml)
                }
            }
        }
    }

    private suspend fun autoSaveNote(htmlContent: String) {
        val db = AppDatabase.getDatabase(applicationContext)
        val noteDao = db.noteDao()
        val currentNoteId = noteId

        if (currentNoteId == 0) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val draftTitle = "Draft Note - " + sdf.format(Date())
            val newNote = Note(
                title = draftTitle,
                content = htmlContent,
                fontSize = currentFontSize,
                category = selectedCategory,
                isPinned = isPinned,
                lastEdited = System.currentTimeMillis()
            )
            val newId = noteDao.insertNote(newNote).toInt()
            noteId = newId
            noteTitle = draftTitle
            lastSavedContent = htmlContent
            runOnUiThread {
                binding.root.announceForAccessibility("Draft note auto saved")
            }
        } else {
            val existing = noteDao.getNoteById(currentNoteId)
            if (existing != null) {
                val updatedNote = existing.copy(
                    content = htmlContent,
                    fontSize = currentFontSize,
                    lastEdited = System.currentTimeMillis()
                )
                noteDao.updateNote(updatedNote)
                lastSavedContent = htmlContent
                runOnUiThread {
                    binding.root.announceForAccessibility("Changes auto saved")
                }
            }
        }
    }

    private fun openAdvancedEditMode() {
        val html = HtmlCompat.toHtml(binding.etNoteContent.text, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        val intent = Intent(this, EditModeActivity::class.java).apply {
            putExtra("content_html", html)
        }
        advancedEditLauncher.launch(intent)
    }

    private fun showEditorOptionsMenu() {
        val options = arrayOf("Save Note", "Discard")
        AlertDialog.Builder(this)
            .setTitle("Options")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showSaveNoteDialog()
                } else {
                    finish()
                }
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Editor options dialog. Select Save Note or Discard.")
    }

    private fun showSaveNoteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_note, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etNoteTitle)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerNoteCategory)
        val btnCreateCategory = dialogView.findViewById<Button>(R.id.btnDialogCreateCategory)

        etTitle.setText(noteTitle)
        if (noteTitle.isNotEmpty()) {
            etTitle.setSelection(noteTitle.length)
        }

        // Set up spinner
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter
        
        val selectedIdx = categories.indexOf(selectedCategory)
        if (selectedIdx >= 0) {
            spinnerCategory.setSelection(selectedIdx)
        }

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = categories[position]
                spinnerCategory.contentDescription = "Select note category, $selectedCategory, combo box"
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        var saveDialog: AlertDialog? = null

        btnCreateCategory.setOnClickListener {
            showDialogCreateCategory(spinnerCategory)
        }

        saveDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    noteTitle = title
                    saveNoteAndExit()
                } else {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        saveDialog.show()
        binding.root.announceForAccessibility("Save note dialog. Enter title, select category, then choose Save or Cancel.")
    }

    private fun showDialogCreateCategory(spinner: Spinner) {
        val input = EditText(this)
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
                    saveCategoryFromDialog(name, spinner)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun saveCategoryFromDialog(name: String, spinner: Spinner) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val existing = db.categoryDao().getCategoryByName(name)
            if (existing != null) {
                runOnUiThread {
                    Toast.makeText(this@NoteEditorActivity, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            db.categoryDao().insertCategory(Category(name = name))
            
            // Refresh categories list
            val updated = db.categoryDao().getAllCategoriesFlow().firstOrNull() ?: emptyList()
            val names = updated.map { it.name }.toMutableList()
            if (!names.contains("General")) {
                names.add("General")
            }
            categories = names

            runOnUiThread {
                val spinnerAdapter = ArrayAdapter(this@NoteEditorActivity, android.R.layout.simple_spinner_item, categories)
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = spinnerAdapter
                val newIdx = categories.indexOf(name)
                if (newIdx >= 0) {
                    spinner.setSelection(newIdx)
                    selectedCategory = name
                }
                Toast.makeText(this@NoteEditorActivity, "Category $name created", Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility("Category $name created and selected")
            }
        }
    }

    private fun saveNoteAndExit() {
        val html = HtmlCompat.toHtml(binding.etNoteContent.text, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val noteDao = db.noteDao()

            if (noteId == 0) {
                val newNote = Note(
                    title = noteTitle,
                    content = html,
                    fontSize = currentFontSize,
                    category = selectedCategory,
                    isPinned = isPinned,
                    lastEdited = System.currentTimeMillis()
                )
                noteDao.insertNote(newNote)
            } else {
                val existing = noteDao.getNoteById(noteId)
                if (existing != null) {
                    val updatedNote = existing.copy(
                        title = noteTitle,
                        content = html,
                        fontSize = currentFontSize,
                        category = selectedCategory,
                        isPinned = isPinned,
                        lastEdited = System.currentTimeMillis()
                    )
                    noteDao.updateNote(updatedNote)
                }
            }

            runOnUiThread {
                val message = "Note saved successfully"
                Toast.makeText(this@NoteEditorActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }

    private fun showDiscardWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle("Save changes?")
            .setMessage("Do you want to save this note before leaving?")
            .setPositiveButton("Save") { _, _ ->
                showSaveNoteDialog()
            }
            .setNegativeButton("Discard") { _, _ ->
                finish()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Do you want to save this note before leaving? Choose Save, Discard, or Cancel.")
    }
}
