plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.units"
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit text recognition (unbundled V2)
    implementation(libs.mlkit.text.recognition)

    // Gemini Nano (IA local, beta) — respaldo del OCR cuando la lectura normal falla
    implementation(libs.mlkit.genai.prompt)

    // Coil — thumbnails de fotos en EstimadoScreen
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}