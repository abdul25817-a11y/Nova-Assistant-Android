package com.nova.assistant.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.data.NovaSettings
import com.nova.assistant.data.SettingsStore
import com.nova.assistant.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(applicationContext)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch {
            val current = store.settings.first()
            binding.apiKey.setText(current.apiKey)
            binding.baseUrl.setText(current.baseUrl)
            binding.model.setText(current.model)
            binding.switchWake.isChecked = current.wakeWordEnabled
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val settings = NovaSettings(
            apiKey = binding.apiKey.text?.toString()?.trim().orEmpty(),
            baseUrl = binding.baseUrl.text?.toString()?.trim().orEmpty(),
            model = binding.model.text?.toString()?.trim().orEmpty(),
            wakeWordEnabled = binding.switchWake.isChecked
        )
        lifecycleScope.launch {
            store.save(settings)
            Toast.makeText(this@SettingsActivity, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
