package com.viteacher.toolkit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.databinding.ActivityEditModeBinding

class EditModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditModeBinding
    private lateinit var masterText: SpannableStringBuilder
    private lateinit var adapter: BlockChecklistAdapter
    private var currentMode = "Characters"
    
    private var alignmentIndex = 0
    private val alignments = listOf(Layout.Alignment.ALIGN_NORMAL, Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_OPPOSITE)
    private val alignmentNames = listOf("left", "center", "right")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read dynamic content html from intent
        val htmlContent = intent.getStringExtra("content_html") ?: ""
        masterText = SpannableStringBuilder(HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY))

        // Set up selection mode spinner
        setupSelectionModeSpinner()

        // Set up recyclerview checklist
        setupChecklistRecyclerView()

        // Set up toolbar action listeners
        setupToolbarActions()

        // Bottom OK / Cancel listeners
        binding.btnBlockOK.setOnClickListener {
            val finalHtml = HtmlCompat.toHtml(masterText, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
            val resultIntent = Intent().apply {
                putExtra("formatted_html", finalHtml)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        binding.btnBlockCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        
        binding.root.announceForAccessibility("Block Editor Mode loaded. Choose split mode, select blocks, and apply formatting.")
    }

    private fun setupSelectionModeSpinner() {
        val modes = arrayOf("Characters", "Words", "Lines", "Paragraphs")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSelectionMode.adapter = spinnerAdapter

        binding.spinnerSelectionMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMode = modes[position]
                binding.spinnerSelectionMode.contentDescription = "Selection mode, $currentMode, combo box"
                rebuildChecklist()
                binding.root.announceForAccessibility("Selection mode changed to $currentMode")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupChecklistRecyclerView() {
        binding.rvBlockChecklist.layoutManager = LinearLayoutManager(this)
        adapter = BlockChecklistAdapter(emptyList()) { _, _ ->
            // Checked state change callback if needed
        }
        binding.rvBlockChecklist.adapter = adapter
        rebuildChecklist()
    }

    private fun rebuildChecklist() {
        val segments = splitMasterText(currentMode)
        adapter.updateSegments(segments)
    }

    private fun splitMasterText(mode: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        val len = masterText.length
        if (len == 0) return segments

        when (mode) {
            "Characters" -> {
                for (i in 0 until len) {
                    segments.add(TextSegment(i, i + 1, masterText.subSequence(i, i + 1)))
                }
            }
            "Words" -> {
                var i = 0
                while (i < len) {
                    if (Character.isWhitespace(masterText[i])) {
                        val start = i
                        while (i < len && Character.isWhitespace(masterText[i])) {
                            i++
                        }
                        segments.add(TextSegment(start, i, masterText.subSequence(start, i)))
                    } else {
                        val start = i
                        while (i < len && !Character.isWhitespace(masterText[i])) {
                            i++
                        }
                        segments.add(TextSegment(start, i, masterText.subSequence(start, i)))
                    }
                }
            }
            "Lines" -> {
                var i = 0
                while (i < len) {
                    val start = i
                    while (i < len && masterText[i] != '\n') {
                        i++
                    }
                    if (i < len && masterText[i] == '\n') {
                        i++
                    }
                    segments.add(TextSegment(start, i, masterText.subSequence(start, i)))
                }
            }
            "Paragraphs" -> {
                var start = 0
                var i = 0
                while (i < len) {
                    if (i + 1 < len && masterText[i] == '\n' && masterText[i + 1] == '\n') {
                        segments.add(TextSegment(start, i + 2, masterText.subSequence(start, i + 2)))
                        i += 2
                        start = i
                    } else {
                        i++
                    }
                }
                if (start < len) {
                    segments.add(TextSegment(start, len, masterText.subSequence(start, len)))
                }
            }
        }
        return segments
    }

    private fun setupToolbarActions() {
        binding.btnBlockBold.setOnClickListener {
            applyStyleSpanToChecked(Typeface.BOLD, "Bold")
        }

        binding.btnBlockItalic.setOnClickListener {
            applyStyleSpanToChecked(Typeface.ITALIC, "Italic")
        }

        binding.btnBlockUnderline.setOnClickListener {
            applyStyleToChecked({ UnderlineSpan() }, UnderlineSpan::class.java, "Underline")
        }

        binding.btnBlockStrikethrough.setOnClickListener {
            applyStyleToChecked({ StrikethroughSpan() }, StrikethroughSpan::class.java, "Strikethrough")
        }

        binding.btnBlockHighlight.setOnClickListener {
            openHighlightPicker()
        }

        binding.btnBlockTextColor.setOnClickListener {
            openTextColorPicker()
        }

        binding.btnBlockBulletList.setOnClickListener {
            applyListFormattingToChecked(isNumbered = false)
        }

        binding.btnBlockNumberedList.setOnClickListener {
            applyListFormattingToChecked(isNumbered = true)
        }

        binding.btnBlockAlignment.setOnClickListener {
            cycleAlignmentForChecked()
        }

        binding.btnBlockAllCaps.setOnClickListener {
            showChangeCaseMenuForChecked()
        }

        binding.btnBlockIncreaseFontSize.setOnClickListener {
            changeFontSizeForChecked(1)
        }

        binding.btnBlockDecreaseFontSize.setOnClickListener {
            changeFontSizeForChecked(-1)
        }

        binding.btnBlockPageBreak.setOnClickListener {
            insertPageBreakToChecked()
        }

        binding.btnBlockDelete.setOnClickListener {
            deleteCheckedSegments(isCut = false)
        }

        binding.btnBlockCut.setOnClickListener {
            deleteCheckedSegments(isCut = true)
        }
    }

    private fun applyStyleSpanToChecked(style: Int, name: String) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }

        var appliedCount = 0
        var removedCount = 0

        for (seg in checked) {
            val spans = masterText.getSpans(seg.start, seg.end, StyleSpan::class.java)
            var found = false
            for (span in spans) {
                if (span.style == style) {
                    masterText.removeSpan(span)
                    found = true
                }
            }
            if (found) {
                removedCount++
            } else {
                masterText.setSpan(StyleSpan(style), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                appliedCount++
            }
        }

        rebuildChecklist()

        val announcementText = name.lowercase()
        val announcement = if (appliedCount > 0 && removedCount > 0) {
            "Selected text $announcementText applied to some and removed from some items"
        } else if (appliedCount > 0) {
            "Selected text $announcementText"
        } else {
            "$name removed from selected text"
        }
        binding.root.announceForAccessibility(announcement)
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun applyStyleToChecked(spanCreator: () -> Any, spanClass: Class<*>, name: String) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }

        var appliedCount = 0
        var removedCount = 0

        for (seg in checked) {
            val spans = masterText.getSpans(seg.start, seg.end, spanClass)
            if (spans.isNotEmpty()) {
                spans.forEach { masterText.removeSpan(it) }
                removedCount++
            } else {
                masterText.setSpan(spanCreator(), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                appliedCount++
            }
        }

        rebuildChecklist()

        val announcementText = if (name.equals("Underline", ignoreCase = true)) "underlined" else name.lowercase()
        val announcement = if (appliedCount > 0 && removedCount > 0) {
            "Selected text $announcementText applied to some and removed from some items"
        } else if (appliedCount > 0) {
            "Selected text $announcementText"
        } else {
            "$name removed from selected text"
        }
        binding.root.announceForAccessibility(announcement)
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
        binding.root.announceForAccessibility("Highlight color options. Select Yellow, Green, Pink, or Blue.")
    }

    private fun applyHighlightColor(color: Int, name: String) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }
        for (seg in checked) {
            val spans = masterText.getSpans(seg.start, seg.end, BackgroundColorSpan::class.java)
            spans.forEach { masterText.removeSpan(it) }
            masterText.setSpan(BackgroundColorSpan(color), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        rebuildChecklist()
        binding.root.announceForAccessibility("$name applied to selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }
        for (seg in checked) {
            val spans = masterText.getSpans(seg.start, seg.end, ForegroundColorSpan::class.java)
            spans.forEach { masterText.removeSpan(it) }
            masterText.setSpan(ForegroundColorSpan(color), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        rebuildChecklist()
        binding.root.announceForAccessibility("$name applied to selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun applyListFormattingToChecked(isNumbered: Boolean) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }

        val sortedChecked = checked.sortedByDescending { it.start }
        for (i in sortedChecked.indices) {
            val seg = sortedChecked[i]
            val chronologicalIndex = sortedChecked.size - 1 - i
            val prefix = if (isNumbered) "${chronologicalIndex + 1}. " else "• "
            masterText.insert(seg.start, prefix)
        }
        rebuildChecklist()
        val listType = if (isNumbered) "Numbered list" else "Bullet list"
        binding.root.announceForAccessibility("$listType applied to selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun cycleAlignmentForChecked() {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }
        alignmentIndex = (alignmentIndex + 1) % alignments.size
        val alignment = alignments[alignmentIndex]
        val name = alignmentNames[alignmentIndex]

        for (seg in checked) {
            val spans = masterText.getSpans(seg.start, seg.end, AlignmentSpan::class.java)
            spans.forEach { masterText.removeSpan(it) }
            masterText.setSpan(AlignmentSpan.Standard(alignment), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        rebuildChecklist()
        binding.root.announceForAccessibility("Text alignment set to $name for selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun showChangeCaseMenuForChecked() {
        val options = arrayOf(
            "UPPERCASE (all letters capitalized)",
            "lowercase (all small letters)",
            "Sentence case (first letter of each sentence capitalized)",
            "Title Case (first letter of each word capitalized)"
        )
        AlertDialog.Builder(this)
            .setTitle("Change Case")
            .setItems(options) { _, which ->
                val caseType = when (which) {
                    0 -> "UPPERCASE"
                    1 -> "lowercase"
                    2 -> "Sentence case"
                    3 -> "Title Case"
                    else -> ""
                }
                applyChangeCaseToChecked(caseType)
            }
            .create()
            .show()
        binding.root.announceForAccessibility("Change Case options. Select UPPERCASE, lowercase, Sentence case, or Title Case.")
    }

    private fun applyChangeCaseToChecked(caseType: String) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }
        val sortedChecked = checked.sortedByDescending { it.start }
        for (seg in sortedChecked) {
            val text = masterText.substring(seg.start, seg.end)
            val converted = when (caseType) {
                "UPPERCASE" -> text.uppercase()
                "lowercase" -> text.lowercase()
                "Sentence case" -> toSentenceCase(text)
                "Title Case" -> toTitleCase(text)
                else -> text
            }
            masterText.replace(seg.start, seg.end, converted)
        }
        rebuildChecklist()
        binding.root.announceForAccessibility("$caseType applied to selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun toSentenceCase(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.lowercase())
        var capitalizeNext = true
        for (i in sb.indices) {
            val c = sb[i]
            if (capitalizeNext && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c))
                capitalizeNext = false
            } else if (c == '.' || c == '?' || c == '!') {
                capitalizeNext = true
            }
        }
        return sb.toString()
    }

    private fun toTitleCase(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.lowercase())
        var capitalizeNext = true
        for (i in sb.indices) {
            val c = sb[i]
            if (Character.isWhitespace(c)) {
                capitalizeNext = true
            } else if (capitalizeNext && Character.isLetter(c)) {
                sb.setCharAt(i, Character.toUpperCase(c))
                capitalizeNext = false
            }
        }
        return sb.toString()
    }

    private fun insertPageBreakToChecked() {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }
        val sortedChecked = checked.sortedByDescending { it.start }
        for (seg in sortedChecked) {
            masterText.insert(seg.end, "\n[Page Break]\n")
        }
        rebuildChecklist()
        binding.root.announceForAccessibility("Page break inserted after selected text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun deleteCheckedSegments(isCut: Boolean) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }

        if (isCut) {
            val combinedText = checked.joinToString(" ") { it.text.toString() }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Cut Notes Block", combinedText)
            clipboard.setPrimaryClip(clip)
            binding.root.announceForAccessibility("Selected blocks cut and copied to clipboard")
        } else {
            binding.root.announceForAccessibility("Selected blocks deleted")
        }

        val sortedChecked = checked.sortedByDescending { it.start }

        for (seg in sortedChecked) {
            masterText.delete(seg.start, seg.end)
        }

        rebuildChecklist()
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun changeFontSizeForChecked(delta: Int) {
        val checked = adapter.getCheckedSegments()
        if (checked.isEmpty()) {
            binding.root.announceForAccessibility("No items selected")
            return
        }

        val firstSeg = checked.first()
        val spans = masterText.getSpans(firstSeg.start, firstSeg.end, AbsoluteSizeSpan::class.java)
        val currentSize = if (spans.isNotEmpty()) {
            val span = spans.first()
            if (span.dip) span.size else (span.size / resources.displayMetrics.density).toInt()
        } else {
            intent.getIntExtra("font_size", 18)
        }

        val newSize = (currentSize + delta).coerceIn(12, 48)

        for (seg in checked) {
            val segSpans = masterText.getSpans(seg.start, seg.end, AbsoluteSizeSpan::class.java)
            segSpans.forEach { masterText.removeSpan(it) }
            masterText.setSpan(AbsoluteSizeSpan(newSize, true), seg.start, seg.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        rebuildChecklist()

        val text = if (delta > 0) "increased to $newSize" else "decreased to $newSize"
        binding.root.announceForAccessibility("Font size $text")
        binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

data class TextSegment(
    val start: Int,
    val end: Int,
    val text: CharSequence
)

class BlockChecklistAdapter(
    private var segments: List<TextSegment>,
    private val onCheckedChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<BlockChecklistAdapter.ViewHolder>() {

    private val checkedStates = mutableMapOf<Int, Boolean>()

    fun updateSegments(newSegments: List<TextSegment>) {
        segments = newSegments
        checkedStates.clear()
        notifyDataSetChanged()
    }

    fun getCheckedSegments(): List<TextSegment> {
        return segments.filterIndexed { index, _ -> checkedStates[index] == true }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val checkBox = CheckBox(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minHeight = dpToPx(48, context) // 48dp minimum touch target
            setPadding(dpToPx(16, context), dpToPx(12, context), dpToPx(16, context), dpToPx(12, context))
            textSize = 18f
        }
        return ViewHolder(checkBox)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val segment = segments[position]
        holder.checkBox.apply {
            setOnCheckedChangeListener(null)

            text = segment.text
            isChecked = checkedStates[position] == true

            val stateText = if (isChecked) "checked" else "unchecked"
            val textStr = segment.text.toString().trim()
            val pieceText = when {
                textStr.isEmpty() -> "whitespace"
                segment.text.toString() == "\n" -> "newline"
                segment.text.toString() == "\n\n" -> "double newline"
                else -> segment.text.toString()
            }
            contentDescription = "Checkbox: $pieceText, $stateText"

            setOnCheckedChangeListener { _, isChecked ->
                checkedStates[position] = isChecked
                val newStateText = if (isChecked) "checked" else "unchecked"
                contentDescription = "Checkbox: $pieceText, $newStateText"
                val announcement = "$pieceText is now $newStateText"
                announceForAccessibility(announcement)
                onCheckedChange(position, isChecked)
            }
        }
    }

    override fun getItemCount(): Int = segments.size

    class ViewHolder(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)

    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
