package com.viteacher.toolkit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viteacher.toolkit.R
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Credential
import com.viteacher.toolkit.databinding.ActivityPasswordSaverBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PasswordSaverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordSaverBinding
    private lateinit var adapter: CredentialAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordSaverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CredentialAdapter(emptyList(),
            onView = { credential -> showPassword(credential) },
            onEdit = { credential -> editCredential(credential) },
            onDelete = { credential -> confirmDelete(credential) },
            onCopyUsername = { credential -> copyToClipboard("Username", credential.username) },
            onCopyPassword = { credential -> copyToClipboard("Password", credential.password) }
        )

        binding.rvCredentials.layoutManager = LinearLayoutManager(this)
        binding.rvCredentials.adapter = adapter

        loadCredentials()

        binding.btnAddCredential.setOnClickListener {
            startActivity(Intent(this, AddCredentialActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadCredentials()
    }

    private fun loadCredentials() {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .credentialDao()
                .getAllCredentials()
                .collectLatest { credentials ->
                    adapter.updateList(credentials)
                }
        }
    }

    private fun showPassword(credential: Credential) {
        val message =
            "Title: ${credential.title}. " +
                    "Username: ${credential.username}. " +
                    "Password: ${credential.password}"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.root.announceForAccessibility(message)
    }

    private fun editCredential(credential: Credential) {
        val intent = Intent(this, EditCredentialActivity::class.java)
        intent.putExtra("credential_id", credential.id)
        startActivity(intent)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        val message = "$label copied to clipboard"
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        binding.root.announceForAccessibility(message)
    }

    private fun confirmDelete(credential: Credential) {
        AlertDialog.Builder(this)
            .setTitle("Delete Credential")
            .setMessage(
                "Are you sure you want to delete ${credential.title}? " +
                        "This cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteCredential(credential)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.root.announceForAccessibility("Delete cancelled")
            }
            .create()
            .show()
        binding.root.announceForAccessibility(
            "Confirm delete. Are you sure you want to delete ${credential.title}? " +
                    "Choose Delete or Cancel."
        )
    }

    private fun deleteCredential(credential: Credential) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .credentialDao()
                .deleteCredential(credential)
            runOnUiThread {
                val message = "${credential.title} deleted successfully"
                Toast.makeText(this@PasswordSaverActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
        }
    }

    inner class CredentialAdapter(
        private var credentials: List<Credential>,
        private val onView: (Credential) -> Unit,
        private val onEdit: (Credential) -> Unit,
        private val onDelete: (Credential) -> Unit,
        private val onCopyUsername: (Credential) -> Unit,
        private val onCopyPassword: (Credential) -> Unit
    ) : RecyclerView.Adapter<CredentialAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvCredentialTitle)
            val tvUsername: TextView = itemView.findViewById(R.id.tvCredentialUsername)
            val btnView: Button = itemView.findViewById(R.id.btnViewCredential)
            val btnEdit: Button = itemView.findViewById(R.id.btnEditCredential)
            val btnDelete: Button = itemView.findViewById(R.id.btnDeleteCredential)
            val btnCopyUsername: Button = itemView.findViewById(R.id.btnCopyUsername)
            val btnCopyPassword: Button = itemView.findViewById(R.id.btnCopyPassword)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_credential, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val credential = credentials[position]
            holder.tvTitle.text = credential.title
            holder.tvUsername.text = credential.username
            holder.itemView.contentDescription =
                "Credential: ${credential.title}, Username: ${credential.username}"
            holder.btnView.setOnClickListener { onView(credential) }
            holder.btnEdit.setOnClickListener { onEdit(credential) }
            holder.btnDelete.setOnClickListener { onDelete(credential) }
            holder.btnCopyUsername.setOnClickListener { onCopyUsername(credential) }
            holder.btnCopyPassword.setOnClickListener { onCopyPassword(credential) }
        }

        override fun getItemCount() = credentials.size

        fun updateList(newCredentials: List<Credential>) {
            credentials = newCredentials
            notifyDataSetChanged()
        }
    }
}