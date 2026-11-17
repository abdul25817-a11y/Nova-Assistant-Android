package com.nova.assistant.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nova.assistant.R
import com.nova.assistant.databinding.ActivityMainBinding
import com.nova.assistant.voice.VoiceAssistantService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        adapter = MessageAdapter()

        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            this.adapter = this@MainActivity.adapter
            setHasFixedSize(false)
        }

        setupToolbar()
        observe()
        requestNeededPermissions()

        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.btnMic.setOnClickListener { onMicClicked() }
        binding.input.setOnEditorActionListener { _, _, _ ->
            sendCurrentInput()
            true
        }

        if (intent.getBooleanExtra(EXTRA_START_VOICE, false)) {
            toggleVoiceMode()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.app_name)
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_voice -> {
                    toggleVoiceMode()
                    true
                }
                R.id.action_settings -> {
                    settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_clear -> {
                    viewModel.clear()
                    Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    adapter.submit(list)
                    binding.emptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    if (list.isNotEmpty()) binding.recycler.scrollToPosition(list.size - 1)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ChatState.Idle -> {
                            binding.btnSend.isEnabled = true
                            binding.btnMic.isEnabled = true
                            binding.toolbar.subtitle = null
                            binding.input.hint = getString(R.string.hint_message)
                        }
                        ChatState.Thinking -> {
                            binding.btnSend.isEnabled = false
                            binding.btnMic.isEnabled = false
                            binding.toolbar.subtitle = getString(R.string.thinking)
                        }
                        is ChatState.Error -> {
                            binding.btnSend.isEnabled = true
                            binding.btnMic.isEnabled = true
                            binding.toolbar.subtitle = null
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun toggleVoiceMode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            Toast.makeText(this, R.string.mic_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }
        if (isVoiceServiceRunning()) {
            VoiceAssistantService.stop(this)
            Toast.makeText(this, R.string.voice_stopped, Toast.LENGTH_SHORT).show()
        } else {
            VoiceAssistantService.start(this)
            Toast.makeText(this, R.string.voice_started, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isVoiceServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.getRunningServices(Int.MAX_VALUE).any { service ->
            service.service.className == VoiceAssistantService::class.java.name
        }
    }

    private fun sendCurrentInput() {
        val text = binding.input.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.send(text)
        binding.input.text?.clear()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed.toTypedArray())
        }
    }

    private fun onMicClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            Toast.makeText(this, R.string.mic_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.speech_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (listening) {
            stopListening()
            return
        }
        startListening()
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    binding.input.hint = getString(R.string.listening)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }

                override fun onError(error: Int) {
                    listening = false
                    binding.input.hint = getString(R.string.hint_message)
                    Toast.makeText(this@MainActivity, R.string.speech_error, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    binding.input.hint = getString(R.string.hint_message)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) binding.input.setText(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                if (Locale.getDefault().language.lowercase(Locale.ROOT) == "ur") "ur-PK" else "en-US"
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.speech_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopListening() {
        listening = false
        speechRecognizer?.stopListening()
        binding.input.hint = getString(R.string.hint_message)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_VOICE = "startVoice"
    }
}
