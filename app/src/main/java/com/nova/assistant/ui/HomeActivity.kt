package com.nova.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nova.assistant.R
import com.nova.assistant.databinding.ActivityHomeBinding
import com.nova.assistant.voice.ListeningState
import com.nova.assistant.voice.Speaker
import com.nova.assistant.voice.SpeechFailure
import com.nova.assistant.voice.SpeechInputController
import com.nova.assistant.voice.VoiceAssistantService
import com.nova.assistant.voice.VoiceTurnHandler
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var speech: SpeechInputController
    private lateinit var turns: VoiceTurnHandler

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                speech.start()
            } else {
                showMessage(getString(R.string.voice_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        turns = VoiceTurnHandler(application) { reply ->
            binding.replyText.text = getString(R.string.voice_reply, reply)
        }
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
                            binding.btnStop.isEnabled = false
                            if (binding.statusText.text.isNullOrBlank()) {
                                binding.statusText.setText(R.string.voice_status_idle)
                            }
                        }
                        ListeningState.LISTENING -> {
                            binding.statusText.setText(R.string.listening)
                            binding.btnStop.isEnabled = true
                        }
                        ListeningState.PROCESSING -> {
                            binding.statusText.setText(R.string.processing)
                            binding.btnStop.isEnabled = true
                        }
                        ListeningState.CANCELLED -> {
                            binding.statusText.setText(R.string.voice_cancelled)
                            binding.btnStop.isEnabled = false
                        }
                    }
                }

                override fun onPartial(text: String) {
                    if (text.isNotBlank()) {
                        binding.statusText.text = getString(R.string.voice_partial, text)
                    }
                }

                override fun onRecognized(text: String) {
                    binding.recognizedText.text = getString(R.string.voice_recognized, text)
                    binding.statusText.setText(R.string.processing)
                    lifecycleScope.launch {
                        if (turns.handle(text) == TurnResult.Ignored) {
                            binding.statusText.setText(R.string.voice_unclear)
                        }
                    }
                }

                override fun onFailure(failure: SpeechFailure) {
                    binding.statusText.text = failureMessage(failure)
                    binding.btnStop.isEnabled = false
                }
            }
        )

        setupNavigation()

        binding.btnSpeak.setOnClickListener { onSpeakClicked() }
        binding.btnStop.setOnClickListener {
            speech.cancel()
            Speaker.stop()
            binding.statusText.setText(R.string.voice_cancelled)
        }

        observeSpeakerErrors()
    }

    private fun setupNavigation() {
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> openChat()
                R.id.nav_voice -> openVoiceMode()
                R.id.nav_settings -> openSettings()
            }
            binding.drawerLayout.closeDrawers()
            true
        }

        binding.cardChat.setOnClickListener { openChat() }
        binding.cardVoice.setOnClickListener { openVoiceMode() }
        binding.cardSettings.setOnClickListener { openSettings() }
    }

    private fun openChat() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openVoiceMode() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_VOICE, true)
            }
        )
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun onSpeakClicked() {
        Speaker.stop()
        VoiceAssistantService.stopIfRunning(this)
        binding.recognizedText.text = ""
        binding.replyText.text = ""
        speech.start()
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

    override fun onStart() {
        super.onStart()
        binding.rgbBorder.start()
    }

    override fun onStop() {
        binding.rgbBorder.stop()
        speech.cancel()
        Speaker.stop(Speaker.SOURCE_VOICE)
        super.onStop()
    }

    override fun onDestroy() {
        binding.rgbBorder.stop()
        speech.destroy()
        Speaker.stop(Speaker.SOURCE_VOICE)
        super.onDestroy()
    }
}
