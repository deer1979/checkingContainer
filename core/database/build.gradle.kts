plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
    alias(libs.plugins.checkingcontainer.android.room)
}

android {
    namespace = "com.checkingcontainer.core.database"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
}
