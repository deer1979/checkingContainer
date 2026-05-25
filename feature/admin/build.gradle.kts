plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.admin"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
}
