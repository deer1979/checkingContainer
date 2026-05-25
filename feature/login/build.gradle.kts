plugins {
    alias(libs.plugins.testo3.android.feature)
}

android {
    namespace = "com.testo3.feature.login"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
}
