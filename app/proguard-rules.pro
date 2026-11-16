# Nova Assistant - ProGuard rules
# Keep app entry points referenced by the manifest
-keep class com.nova.assistant.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jetbrains.annotations.**