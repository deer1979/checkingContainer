plugins {
    alias(libs.plugins.testo3.android.library)
    alias(libs.plugins.testo3.android.hilt)
}

android {
    namespace = "com.testo3.core.data"
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
}
