package com.viteacher.toolkit.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Credential
import com.viteacher.toolkit.databinding.ActivityEditCredentialBinding
import kotlinx.coroutines.launch

class EditCredentialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditCredentialBinding
    private var credentialId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCredentialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialId = intent.getIntExtra("credential_id", 0)
        loadCredential()

        binding.btnUpdateCredential.setOnClickListener {
            updateCredential()
        }

        binding.btnCancelEditCredential.setOnClickListener {
            finish()
        }
    }

    private fun loadCredential() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.credentialDao().getAllCredentials().collect { credentials ->
                val credential = credentials.find { it.id == credentialId }
                runOnUiThread {
                    if (credential != null) {
                        binding.etTitle.setText(credential.title)
                        binding.etUsername.setText(credential.username)
                        binding.etPassword.setText(credential.password)
                    }
                }
                return@collect
            }
        }
    }

    private fun updateCredential() {
        val title = binding.etTitle.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTitle.error = "Please enter a title"
            binding.etTitle.requestFocus()
            return
        }
        if (username.isEmpty()) {
            binding.etUsername.error = "Please enter a username"
            binding.etUsername.requestFocus()
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter a password"
            binding.etPassword.requestFocus()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.credentialDao().updateCredential(
                Credential(
                    id = credentialId,
                    title = title,
                    username = username,
                    password = password
                )
            )
            runOnUiThread {
                val message = "Credential updated successfully"
                Toast.makeText(this@EditCredentialActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }
}