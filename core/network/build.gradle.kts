plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
    alias(libs.plugins.kotlin.serialization)   // para @Serializable en DTOs internos
}

android {
    namespace = "com.checkingcontainer.core.network"
}

dependencies {
    // HTTP — Sheets REST API + token exchange
    api(libs.okhttp)

    // JSON — serialización de credentials.json y payloads de Sheets
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
