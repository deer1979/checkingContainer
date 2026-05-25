plugins {
    alias(libs.plugins.testo3.android.library)
    alias(libs.plugins.testo3.android.hilt)
}

android {
    namespace = "com.testo3.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
