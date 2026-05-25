plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.login"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
}
