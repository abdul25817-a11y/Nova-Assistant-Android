package com.nova.assistant.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityGuide {

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context, NovaAccessibilityService::class.java)
        val enabled = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
        } catch (e: Exception) {
            return false
        }
        return enabled.split(':').any { entry ->
            entry.equals(component.flattenToString(), ignoreCase = true) ||
                (entry.contains(context.packageName, ignoreCase = true) &&
                    entry.contains(NovaAccessibilityService::class.java.name, ignoreCase = true))
        }
    }
}
