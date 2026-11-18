package com.nova.assistant.automation

enum class AppTarget(
    val displayName: String,
    val packages: List<String>,
    val isSystemSettings: Boolean = false
) {
    YOUTUBE("YouTube", listOf("com.google.android.youtube")),
    FACEBOOK("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
    WHATSAPP("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    CHROME("Chrome", listOf("com.android.chrome")),
    SETTINGS("Settings", emptyList(), isSystemSettings = true);
}
