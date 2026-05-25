plugins {
    alias(libs.plugins.testo3.android.library)
    alias(libs.plugins.testo3.android.hilt)
    alias(libs.plugins.testo3.android.room)
}

android {
    namespace = "com.testo3.core.database"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
}
