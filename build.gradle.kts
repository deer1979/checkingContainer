plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin is built into AGP 9.x — no separate kotlin-android plugin.
    alias(libs.plugins.kotlin.compose) apply false
}
