package com.nova.assistant.ui
import com.nova.assistant.R

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private val modelPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ModelPickerActivity.EXTRA_MODEL)?.let {
                    binding.model.setText(it)
                }
                result.data?.getStringExtra(ModelPickerActivity.EXTRA_BASE_URL)?.let {
                    binding.baseUrl.setText(it)
                }
            }
        }

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

        binding.btnPickModel.setOnClickListener {
            modelPickerLauncher.launch(Intent(this, ModelPickerActivity::class.java))
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
