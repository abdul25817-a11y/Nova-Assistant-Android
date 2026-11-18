package com.nova.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
import com.nova.assistant.R
import com.nova.assistant.databinding.ActivityMainBinding
import com.nova.assistant.voice.ListeningState
import com.nova.assistant.voice.Speaker
import com.nova.assistant.voice.SpeechFailure
import com.nova.assistant.voice.SpeechInputController
import com.nova.assistant.voice.TurnResult
import com.nova.assistant.voice.VoiceAssistantService
import com.nova.assistant.voice.VoiceTurnHandler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    private lateinit var speech: SpeechInputController
    private lateinit var turns: VoiceTurnHandler

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startSpeech() else showMessage(getString(R.string.voice_permission_denied))
        }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { !it }) {
                showMessage(getString(R.string.voice_permission_denied))
            }
        }

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

        turns = VoiceTurnHandler(application) { }
        speech = SpeechInputController(
            this,
            hasPermission = {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            },
            onPermissionNeeded = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
            listener = object : SpeechInputController.Listener {
                override fun onState(state: ListeningState) {
                    when (state) {
                        ListeningState.IDLE -> {
                            binding.input.hint = getString(R.string.hint_message)
                            binding.btnMic.isEnabled = true
                        }
                        ListeningState.LISTENING -> {
                            binding.input.hint = getString(R.string.listening)
                            binding.btnMic.isEnabled = true
                        }
                        ListeningState.PROCESSING -> {
                            binding.input.hint = getString(R.string.processing)
                        }
                        ListeningState.CANCELLED -> {
                            binding.input.hint = getString(R.string.voice_cancelled)
                        }
                    }
                }

                override fun onPartial(text: String) {
                    if (text.isNotBlank()) binding.input.hint = getString(R.string.voice_partial, text)
                }

                override fun onRecognized(text: String) {
                    binding.input.setText(text)
                    lifecycleScope.launch {
                        if (turns.handle(text) == TurnResult.Ignored) {
                            showMessage(getString(R.string.voice_unclear))
                        }
                    }
                }

                override fun onFailure(failure: SpeechFailure) {
                    showMessage(failureMessage(failure))
                }
            }
        )

        setupToolbar()
        observe()
        requestNeededPermissions()

        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.btnMic.setOnClickListener { onMicClicked() }
        binding.input.setOnEditorActionListener { _, _, _ ->
            sendCurrentInput()
            true
        }

        observeSpeakerErrors()

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
                R.id.action_stop_speech -> {
                    Speaker.stop()
                    speech.cancel()
                    showMessage(getString(R.string.voice_cancelled))
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
                            showMessage(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun observeSpeakerErrors() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Speaker.errors.collect {
                    showMessage(getString(R.string.speech_output_error))
                }
            }
        }
    }

    private fun toggleVoiceMode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
            showMessage(getString(R.string.mic_permission_needed))
            return
        }
        if (VoiceAssistantService.isRunning(this)) {
            VoiceAssistantService.stop(this)
            Toast.makeText(this, R.string.voice_stopped, Toast.LENGTH_SHORT).show()
        } else {
            VoiceAssistantService.start(this)
            Toast.makeText(this, R.string.voice_started, Toast.LENGTH_SHORT).show()
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
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
            showMessage(getString(R.string.mic_permission_needed))
            return
        }
        startSpeech()
    }

    private fun startSpeech() {
        Speaker.stop()
        VoiceAssistantService.stopIfRunning(this)
        speech.start()
    }

    private fun failureMessage(failure: SpeechFailure): String = getString(
        when (failure) {
            SpeechFailure.NETWORK -> R.string.voice_network_error
            SpeechFailure.NO_SPEECH -> R.string.voice_empty
            SpeechFailure.UNCLEAR -> R.string.voice_unclear
            SpeechFailure.BUSY -> R.string.voice_busy
            SpeechFailure.PERMISSION -> R.string.voice_permission_denied
            SpeechFailure.AUDIO -> R.string.voice_audio_error
            SpeechFailure.SERVER -> R.string.voice_server_error
            SpeechFailure.UNAVAILABLE -> R.string.speech_unavailable
            SpeechFailure.CANCELLED -> R.string.voice_cancelled
            SpeechFailure.UNKNOWN -> R.string.speech_error
        }
    )

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onStop() {
        speech.cancel()
        Speaker.stop(Speaker.SOURCE_VOICE)
        super.onStop()
    }

    override fun onDestroy() {
        speech.destroy()
        Speaker.stop(Speaker.SOURCE_CHAT)
        Speaker.stop(Speaker.SOURCE_VOICE)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_VOICE = "startVoice"
    }
}
