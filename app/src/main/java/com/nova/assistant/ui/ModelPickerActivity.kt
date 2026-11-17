package com.nova.assistant.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nova.assistant.databinding.ActivityModelPickerBinding

class ModelPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.optionLocal.setOnClickListener { select("local", "") }
        binding.optionOpenai.setOnClickListener {
            select("gpt-4o-mini", "https://api.openai.com/v1")
        }
        binding.optionOpenrouter.setOnClickListener {
            select("openai/gpt-4o-mini", "https://openrouter.ai/api/v1")
        }
        binding.optionGroq.setOnClickListener {
            select("llama-3.3-70b-versatile", "https://api.groq.com/openai/v1")
        }
        binding.optionGemini.setOnClickListener {
            select("gemini-2.0-flash", "https://generativelanguage.googleapis.com/v1beta/openai")
        }
    }

    private fun select(model: String, baseUrl: String) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_MODEL, model)
                .putExtra(EXTRA_BASE_URL, baseUrl)
        )
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_MODEL = "model"
        const val EXTRA_BASE_URL = "baseUrl"
    }
}
