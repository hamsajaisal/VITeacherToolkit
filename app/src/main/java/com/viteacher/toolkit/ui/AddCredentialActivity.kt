package com.viteacher.toolkit.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.viteacher.toolkit.data.AppDatabase
import com.viteacher.toolkit.data.Credential
import com.viteacher.toolkit.databinding.ActivityAddCredentialBinding
import com.viteacher.toolkit.util.setupCursorEndForEditTexts
import kotlinx.coroutines.launch

class AddCredentialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCredentialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCredentialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setupCursorEndForEditTexts()

        binding.btnSaveCredential.setOnClickListener {
            saveCredential()
        }

        binding.btnCancelCredential.setOnClickListener {
            finish()
        }
    }

    private fun saveCredential() {
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
            db.credentialDao().insertCredential(
                Credential(
                    title = title,
                    username = username,
                    password = password
                )
            )
            runOnUiThread {
                val message = "Credential saved successfully"
                Toast.makeText(this@AddCredentialActivity, message, Toast.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
                finish()
            }
        }
    }
}