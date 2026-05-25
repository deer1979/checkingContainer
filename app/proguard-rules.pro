# Add project-specific ProGuard rules here.
# https://developer.android.com/build/shrink-code

# Keep Compose runtime metadata reachable for tooling.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
