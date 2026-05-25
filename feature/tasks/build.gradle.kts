plugins {
    alias(libs.plugins.checkingcontainer.android.feature)
}

android {
    namespace = "com.checkingcontainer.feature.tasks"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
}
