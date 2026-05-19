# Add project-specific ProGuard rules here.
# Compose + Kotlin defaults handle most things.
# Keep AudioTrack/MediaSession callbacks intact.
-keepclassmembers class * extends android.app.Service {
    public *;
}
