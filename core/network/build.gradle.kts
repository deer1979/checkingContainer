plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
}

android {
    namespace = "com.checkingcontainer.core.network"
}

dependencies {
    // TODO: Agregar dependencias de Google Sheets / Drive API aquí:
    //   implementation("com.google.api-client:google-api-client-android:...")
    //   implementation("com.google.apis:google-api-services-sheets:...")
    //   implementation("com.google.apis:google-api-services-drive:...")

    implementation(libs.kotlinx.coroutines.android)
}
