plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.settings"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
}
