package com.nova.assistant.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.nova.assistant.voice.VoiceCommand

class ActionRouter(private val context: Context) {

    sealed interface Result {
        data class Launched(val target: AppTarget) : Result
        data class NotInstalled(val target: AppTarget) : Result
        data class Failed(val target: AppTarget) : Result
        data object NoAction : Result
    }

    fun execute(command: VoiceCommand): Result = when (command) {
        is VoiceCommand.OpenApp -> launch(command.target)
    }

    private fun launch(target: AppTarget): Result {
        val intent: Intent
        if (target.isSystemSettings) {
            intent = Intent(Settings.ACTION_SETTINGS)
        } else {
            val packageManager: PackageManager = context.packageManager
            intent = target.packages.firstNotNullOfOrNull { packageName ->
                try {
                    packageManager.getLaunchIntentForPackage(packageName)
                } catch (e: Exception) {
                    null
                }
            } ?: return Result.NotInstalled(target)
        }

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.Launched(target)
        } catch (e: Exception) {
            Result.Failed(target)
        }
    }
}
