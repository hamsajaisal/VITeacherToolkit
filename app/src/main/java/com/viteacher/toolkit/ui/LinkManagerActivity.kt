package com.viteacher.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.LinkFolder
import com.viteacher.toolkit.data.LinkItem
import com.viteacher.toolkit.databinding.ActivityLinkManagerBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LinkManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkManagerBinding
    private lateinit var adapter: FolderAdapter
    private lateinit var db: AppDatabase
    private var allFolders: List<LinkFolder> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(applicationContext)

        // Initialize default folders if database is empty
        lifecycleScope.launch {
            val list = db.linkFolderDao().getAllFoldersOnce()
            if (list.isEmpty()) {
                db.linkFolderDao().insertFolder(LinkFolder(name = "Office Work"))
                db.linkFolderDao().insertFolder(LinkFolder(name = "Classroom Activities"))
            }
        }

        setupRecyclerView()
        setupClickListeners()
        observeFolders()
    }

    private fun setupRecyclerView() {
        adapter = FolderAdapter(
            onFolderClick = { folder ->
                val intent = Intent(this, LinkFolderActivity::class.java).apply {
                    putExtra("folder_id", folder.id)
                    putExtra("folder_name", folder.name)
                }
                startActivity(intent)
            },
            onFolderOptionsClick = { folder ->
                showFolderOptionsDialog(folder)
            },
            onLinkClick = { link ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                startActivity(intent)
            }
        )
        binding.rvFolders.layoutManager = LinearLayoutManager(this)
        binding.rvFolders.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.fabAddFolder.setOnClickListener {
            showAddFolderDialog()
        }

        binding.etSearchLinks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterLinks(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeFolders() {
        lifecycleScope.launch {
            db.linkFolderDao().getAllFoldersFlow().collectLatest { list ->
                allFolders = list
                val query = binding.etSearchLinks.text.toString().trim()
                filterLinks(query)
            }
        }
    }

    private fun filterLinks(query: String) {
        lifecycleScope.launch {
            if (query.isEmpty()) {
                val list = allFolders.map { LinkManagerItem.FolderItem(it) }
                runOnUiThread {
                    adapter.submitList(list)
                    binding.tvEmptyFolders.text = "No folders created yet.\nCreate a folder to start organizing your links."
                    if (list.isEmpty()) {
                        binding.tvEmptyFolders.visibility = View.VISIBLE
                        binding.rvFolders.visibility = View.GONE
                    } else {
                        binding.tvEmptyFolders.visibility = View.GONE
                        binding.rvFolders.visibility = View.VISIBLE
                    }
                }
            } else {
                val matchedFolders = allFolders.filter { it.name.contains(query, ignoreCase = true) }
                val allLinks = db.linkItemDao().getAllLinksOnce()
                val matchedLinks = allLinks.filter { 
                    it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) 
                }
                
                val foldersMap = allFolders.associateBy { it.id }
                val results = mutableListOf<LinkManagerItem>()
                matchedFolders.forEach { results.add(LinkManagerItem.FolderItem(it)) }
                matchedLinks.forEach { link ->
                    val folderName = foldersMap[link.folderId]?.name ?: "Folder"
                    results.add(LinkManagerItem.LinkItemResult(link, folderName))
                }
                
                runOnUiThread {
                    adapter.submitList(results)
                    if (results.isEmpty()) {
                        binding.tvEmptyFolders.text = "No folders or links match your search."
                        binding.tvEmptyFolders.visibility = View.VISIBLE
                        binding.rvFolders.visibility = View.GONE
                    } else {
                        binding.tvEmptyFolders.visibility = View.GONE
                        binding.rvFolders.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showAddFolderDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create Folder")

        val input = EditText(this).apply {
            hint = "Folder Name (e.g. Resources)"
            setSingleLine(true)
        }
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(48, 16, 48, 16)
        }
        input.layoutParams = params
        container.addView(input)
        container.setupCursorEndForEditTexts()
        builder.setView(container)

        builder.setPositiveButton("Create") { dialog, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    val existing = db.linkFolderDao().getFolderByName(name)
                    if (existing != null) {
                        Toast.makeText(this@LinkManagerActivity, "Folder '$name' already exists.", Toast.LENGTH_SHORT).show()
                    } else {
                        db.linkFolderDao().insertFolder(LinkFolder(name = name))
                        Toast.makeText(this@LinkManagerActivity, "Folder created", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun showFolderOptionsDialog(folder: LinkFolder) {
        val options = arrayOf("Rename Folder", "Delete Folder")
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> confirmDeleteFolder(folder)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showRenameFolderDialog(folder: LinkFolder) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Rename Folder")

        val input = EditText(this).apply {
            setText(folder.name)
            setSingleLine(true)
            selectAll()
        }
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(48, 16, 48, 16)
        }
        input.layoutParams = params
        container.addView(input)
        container.setupCursorEndForEditTexts()
        builder.setView(container)

        builder.setPositiveButton("Save") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                lifecycleScope.launch {
                    val existing = db.linkFolderDao().getFolderByName(newName)
                    if (existing != null && existing.id != folder.id) {
                        Toast.makeText(this@LinkManagerActivity, "Folder '$newName' already exists.", Toast.LENGTH_SHORT).show()
                    } else {
                        db.linkFolderDao().updateFolder(folder.copy(name = newName))
                        Toast.makeText(this@LinkManagerActivity, "Folder renamed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun confirmDeleteFolder(folder: LinkFolder) {
        AlertDialog.Builder(this)
            .setTitle("Delete Folder?")
            .setMessage("This will permanently delete the folder '${folder.name}' and all saved links inside it.")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch {
                    db.linkFolderDao().deleteFolder(folder)
                    db.linkItemDao().deleteLinksForFolder(folder.id)
                    Toast.makeText(this@LinkManagerActivity, "Folder deleted", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    sealed class LinkManagerItem {
        data class FolderItem(val folder: LinkFolder) : LinkManagerItem()
        data class LinkItemResult(val link: LinkItem, val folderName: String) : LinkManagerItem()
    }

    private class FolderAdapter(
        private val onFolderClick: (LinkFolder) -> Unit,
        private val onFolderOptionsClick: (LinkFolder) -> Unit,
        private val onLinkClick: (LinkItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var itemsList = emptyList<LinkManagerItem>()

        fun submitList(newList: List<LinkManagerItem>) {
            itemsList = newList
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (itemsList[position]) {
                is LinkManagerItem.FolderItem -> 0
                is LinkManagerItem.LinkItemResult -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_link_folder, parent, false)
            return if (viewType == 0) FolderViewHolder(view) else LinkViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = itemsList[position]
            if (holder is FolderViewHolder && item is LinkManagerItem.FolderItem) {
                holder.bind(item.folder, onFolderClick, onFolderOptionsClick)
            } else if (holder is LinkViewHolder && item is LinkManagerItem.LinkItemResult) {
                holder.bind(item.link, item.folderName, onLinkClick)
            }
        }

        override fun getItemCount(): Int = itemsList.size

        class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvFolderName)
            private val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.ivFolderIcon)

            fun bind(
                folder: LinkFolder,
                onFolderClick: (LinkFolder) -> Unit,
                onFolderOptionsClick: (LinkFolder) -> Unit
            ) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_save)
                tvName.text = folder.name
                itemView.contentDescription = "Folder: ${folder.name}. Double tap to open. Double tap and hold for options."
                itemView.setOnClickListener {
                    onFolderClick(folder)
                }
                itemView.setOnLongClickListener {
                    onFolderOptionsClick(folder)
                    true
                }
            }
        }

        class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvFolderName)
            private val ivIcon: android.widget.ImageView = itemView.findViewById(R.id.ivFolderIcon)

            fun bind(
                link: LinkItem,
                folderName: String,
                onLinkClick: (LinkItem) -> Unit
            ) {
                ivIcon.setImageResource(android.R.drawable.ic_menu_share)
                tvName.text = "${link.title} (in $folderName)"
                itemView.contentDescription = "Link: ${link.title} in folder $folderName. Double tap to open."
                itemView.setOnClickListener {
                    onLinkClick(link)
                }
                itemView.setOnLongClickListener(null)
            }
        }
    }
}
