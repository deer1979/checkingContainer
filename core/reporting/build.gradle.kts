plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
}

android {
    namespace = "com.checkingcontainer.core.reporting"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
