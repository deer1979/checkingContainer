plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.checkingcontainer.core.data"
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
