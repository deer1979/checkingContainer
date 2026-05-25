plugins {
    alias(libs.plugins.testo3.android.feature)
}

android {
    namespace = "com.testo3.feature.splash"
}

dependencies {
    implementation(project(":core:designsystem"))
}
