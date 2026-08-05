plugins {
    alias(libs.plugins.checkingcontainer.android.library)
    alias(libs.plugins.checkingcontainer.android.hilt)
}

android {
    namespace = "com.checkingcontainer.core.data"

    testOptions {
        // El repositorio de bootstrap registra con android.util.Log; en test JVM
        // los métodos del framework devuelven el valor por defecto en vez de
        // lanzar "not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
