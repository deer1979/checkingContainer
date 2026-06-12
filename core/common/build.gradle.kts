plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
}

android {
    namespace = "com.checkingcontainer.core.common"
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.android)
}
