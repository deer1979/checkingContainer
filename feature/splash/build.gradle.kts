plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.splash"
}

dependencies {
    implementation(project(":core:designsystem"))
}
