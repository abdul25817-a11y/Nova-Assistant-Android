package com.nova.assistant.automation

import android.accessibilityservice.AccessibilityEvent
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

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun click(node: AccessibilityNodeInfo?): Boolean =
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

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
