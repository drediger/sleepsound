# Project-specific ProGuard rules. Compose + Kotlin defaults handle most
# things; rules below are for entrypoints + privacy hygiene.

# Strip Log.d/v/i from release. Log.w/e still ship for legitimate error
# surfaces (Billing failures, codec errors).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Entry points: keep the concrete class + all members. The AGP-generated
# aapt_rules.txt keeps only no-arg ctors, which is insufficient for
# subclasses whose framework callbacks (onStartCommand, onReceive, etc.)
# we override.
-keep class com.sleepsound.service.SleepAudioService { *; }
-keep class com.sleepsound.MainActivity { *; }
