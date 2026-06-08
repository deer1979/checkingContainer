plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.announcements"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    // Coil 3 — mostrar imágenes adjuntas (URLs de Firebase Storage)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
