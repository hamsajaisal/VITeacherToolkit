package com.viteacher.toolkit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.LinkItem
import com.viteacher.toolkit.databinding.ActivityLinkFolderBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LinkFolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkFolderBinding
    private lateinit var adapter: LinkAdapter
    private lateinit var db: AppDatabase
    private var folderId: Int = -1
    private var folderName: String = ""
    private var allLinks: List<LinkItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(applicationContext)

        folderId = intent.getIntExtra("folder_id", -1)
        folderName = intent.getStringExtra("folder_name") ?: "Folder Details"

        binding.tvFolderNameTitle.text = folderName

        setupRecyclerView()
        setupClickListeners()
        observeLinks()
    }

    private fun setupRecyclerView() {
        adapter = LinkAdapter(
            onLinkClick = { link ->
                openLinkInBrowser(link.url)
            },
            onLinkOptionsClick = { link ->
                showLinkOptionsDialog(link)
            }
        )
        binding.rvLinks.layoutManager = LinearLayoutManager(this)
        binding.rvLinks.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.fabAddLink.setOnClickListener {
            showAddLinkDialog()
        }

        binding.etSearchLinksLocal.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterLocalLinks(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun observeLinks() {
        if (folderId != -1) {
            lifecycleScope.launch {
                db.linkItemDao().getLinksForFolderFlow(folderId).collectLatest { list ->
                    allLinks = list
                    val query = binding.etSearchLinksLocal.text.toString().trim()
                    filterLocalLinks(query)
                }
            }
        }
    }

    private fun filterLocalLinks(query: String) {
        val filtered = if (query.isEmpty()) {
            allLinks
        } else {
            allLinks.filter { 
                it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        if (filtered.isEmpty()) {
            binding.tvEmptyLinks.text = if (query.isEmpty()) "No links in this folder.\nAdd a link to get started." else "No links match your search."
            binding.tvEmptyLinks.visibility = View.VISIBLE
            binding.rvLinks.visibility = View.GONE
        } else {
            binding.tvEmptyLinks.visibility = View.GONE
            binding.rvLinks.visibility = View.VISIBLE
        }
    }

    private fun openLinkInBrowser(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            Toast.makeText(this, "URL is empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)) {
            cleanUrl = "https://$cleanUrl"
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open browser. Please check the URL format.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddLinkDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Link")

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val etTitle = EditText(context).apply {
            hint = "Title (e.g. YouTube)"
            setSingleLine(true)
        }

        val etUrl = EditText(context).apply {
            hint = "URL (e.g. www.youtube.com)"
            setSingleLine(true)
        }

        layout.addView(etTitle)
        val space = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24)
        }
        layout.addView(space)
        layout.addView(etUrl)

        val container = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(48, 16, 48, 16)
        }
        layout.layoutParams = params
        container.addView(layout)
        container.setupCursorEndForEditTexts()
        builder.setView(container)

        builder.setPositiveButton("Add") { dialog, _ ->
            val title = etTitle.text.toString().trim()
            val url = etUrl.text.toString().trim()

            if (title.isNotEmpty() && url.isNotEmpty()) {
                lifecycleScope.launch {
                    db.linkItemDao().insertLink(
                        LinkItem(folderId = folderId, title = title, url = url)
                    )
                    runOnUiThread {
                        Toast.makeText(this@LinkFolderActivity, "Link added", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Title and URL are required", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun showLinkOptionsDialog(link: LinkItem) {
        val pinOption = if (link.isPinned) "Unpin Link" else "Pin Link"
        val options = arrayOf("Open Link", "Copy Link", "Share Link", pinOption, "Edit Title/URL", "Remove")
        AlertDialog.Builder(this)
            .setTitle(link.title)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openLinkInBrowser(link.url)
                    1 -> copyToClipboard(link.url)
                    2 -> shareLink(link)
                    3 -> togglePinLink(link)
                    4 -> showEditLinkDialog(link)
                    5 -> confirmRemoveLink(link)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun togglePinLink(link: LinkItem) {
        val updated = link.copy(isPinned = !link.isPinned)
        lifecycleScope.launch {
            db.linkItemDao().updateLink(updated)
            runOnUiThread {
                val msg = if (updated.isPinned) "Link pinned to top" else "Link unpinned"
                Toast.makeText(this@LinkFolderActivity, msg, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(msg)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Saved Link", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        binding.root.announceForAccessibility("Link copied to clipboard")
    }

    private fun shareLink(link: LinkItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, link.title)
            putExtra(Intent.EXTRA_TEXT, "${link.title}: ${link.url}")
        }
        startActivity(Intent.createChooser(intent, "Share Link"))
    }

    private fun showEditLinkDialog(link: LinkItem) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Link")

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val etTitle = EditText(context).apply {
            setText(link.title)
            hint = "Title (e.g. YouTube)"
            setSingleLine(true)
        }

        val etUrl = EditText(context).apply {
            setText(link.url)
            hint = "URL (e.g. www.youtube.com)"
            setSingleLine(true)
        }

        layout.addView(etTitle)
        val space = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24)
        }
        layout.addView(space)
        layout.addView(etUrl)

        val container = FrameLayout(context)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(48, 16, 48, 16)
        }
        layout.layoutParams = params
        container.addView(layout)
        container.setupCursorEndForEditTexts()
        builder.setView(container)

        builder.setPositiveButton("Save") { dialog, _ ->
            val title = etTitle.text.toString().trim()
            val url = etUrl.text.toString().trim()

            if (title.isNotEmpty() && url.isNotEmpty()) {
                lifecycleScope.launch {
                    db.linkItemDao().updateLink(
                        link.copy(title = title, url = url)
                    )
                    runOnUiThread {
                        Toast.makeText(this@LinkFolderActivity, "Link updated", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Title and URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun confirmRemoveLink(link: LinkItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove Link?")
            .setMessage("Are you sure you want to delete '${link.title}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch {
                    db.linkItemDao().deleteLink(link)
                    runOnUiThread {
                        Toast.makeText(this@LinkFolderActivity, "Link deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private class LinkAdapter(
        private val onLinkClick: (LinkItem) -> Unit,
        private val onLinkOptionsClick: (LinkItem) -> Unit
    ) : RecyclerView.Adapter<LinkAdapter.LinkViewHolder>() {

        private var linksList = emptyList<LinkItem>()

        fun submitList(newList: List<LinkItem>) {
            linksList = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false)
            return LinkViewHolder(view)
        }

        override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
            val link = linksList[position]
            holder.bind(link, onLinkClick, onLinkOptionsClick)
        }

        override fun getItemCount(): Int = linksList.size

        class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tvLinkTitle)
            private val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.ivLinkIcon)

            fun bind(
                link: LinkItem,
                onLinkClick: (LinkItem) -> Unit,
                onLinkOptionsClick: (LinkItem) -> Unit
            ) {
                tvTitle.text = link.title

                if (link.isPinned) {
                    ivIcon.setImageResource(android.R.drawable.btn_star_big_on)
                    tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.lavender_dark))
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD)
                    itemView.contentDescription = "Pinned Link: ${link.title}. Double tap to open in default browser. Double tap and hold for options."
                } else {
                    ivIcon.setImageResource(android.R.drawable.ic_menu_compass)
                    
                    val typedValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    tvTitle.setTextColor(typedValue.data)
                    tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
                    
                    itemView.contentDescription = "Link: ${link.title}. Double tap to open in default browser. Double tap and hold for options."
                }

                itemView.setOnClickListener {
                    onLinkClick(link)
                }
                itemView.setOnLongClickListener {
                    onLinkOptionsClick(link)
                    true
                }
            }
        }
    }
}
