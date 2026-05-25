plugins {
    alias(libs.plugins.checkingcontainer.android.library.compose)
}

android {
    namespace = "com.checkingcontainer.core.designsystem"
}

dependencies {
    api(project(":core:model"))
}
