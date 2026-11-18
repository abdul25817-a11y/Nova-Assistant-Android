package com.nova.assistant.automation

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional voice-control surface. Nova can perform simple global actions and
 * click nodes on behalf of the user when this service is enabled by them.
 */
class NovaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event-driven automation is intentionally minimal; actions are invoked
        // directly by voice commands through the companion API below.
    }

    override fun onInterrupt() {}

    fun goHome(): Boolean = safeGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = safeGlobalAction(GLOBAL_ACTION_BACK)

    fun showRecents(): Boolean = safeGlobalAction(GLOBAL_ACTION_RECENTS)

    fun showNotifications(): Boolean = safeGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun showQuickSettings(): Boolean = safeGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun click(node: AccessibilityNodeInfo?): Boolean = try {
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    } catch (e: Exception) {
        false
    }

    private fun safeGlobalAction(action: Int): Boolean = try {
        performGlobalAction(action)
    } catch (e: Exception) {
        false
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: NovaAccessibilityService? = null
            private set

        fun isAvailable(): Boolean = instance != null
    }
}
