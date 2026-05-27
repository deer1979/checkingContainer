plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.checkingcontainer.core.network"
}

dependencies {
    // Supabase BOM pins all supabase-kt sub-module versions
    api(platform(libs.supabase.bom))
    api(libs.supabase.postgrest)
    // Ktor HTTP engine (OkHttp-backed) required by the Supabase SDK
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
