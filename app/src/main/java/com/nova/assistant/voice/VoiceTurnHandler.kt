package com.nova.assistant.voice

import android.app.Application
import com.nova.assistant.R
import com.nova.assistant.ai.ConversationEngine
import com.nova.assistant.automation.ActionRouter

sealed interface TurnResult {
    data class Command(val feedback: String) : TurnResult
    data class Answer(val reply: String) : TurnResult
    data object Ignored : TurnResult
}

class VoiceTurnHandler(
    private val app: Application,
    private val onReply: (String) -> Unit
) {

    private val engine = ConversationEngine(app)
    private val router = ActionRouter(app)

    suspend fun handle(raw: String): TurnResult {
        val text = VoiceInputFilter.clean(raw) ?: return TurnResult.Ignored

        val command = VoiceCommandParser.parse(text)
        if (command != null) {
            val feedback = when (val result = router.execute(command)) {
                is ActionRouter.Result.Launched ->
                    app.getString(R.string.command_opening, result.target.displayName)
                is ActionRouter.Result.NotInstalled ->
                    app.getString(R.string.command_not_installed, result.target.displayName)
                is ActionRouter.Result.Failed ->
                    app.getString(R.string.command_failed, result.target.displayName)
                ActionRouter.Result.NoAction -> return TurnResult.Ignored
            }
            engine.recordTurn(text, feedback, Speaker.SOURCE_VOICE)
            onReply(feedback)
            return TurnResult.Command(feedback)
        }

        val reply = engine.send(text, Speaker.SOURCE_VOICE)
        if (reply.isBlank()) return TurnResult.Ignored
        onReply(reply)
        return TurnResult.Answer(reply)
    }
}
