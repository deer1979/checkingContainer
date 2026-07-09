plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
}

android {
    namespace = "com.checkingcontainer.core.network"
}

dependencies {
    // Firebase Firestore + Storage (api — transitivo para core:data)
    api(platform(libs.firebase.bom))
    api(libs.firebase.firestore)
    api(libs.firebase.storage)
    api(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)
}
