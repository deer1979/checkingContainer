plugins {
    alias(libs.plugins.checkingcontainer.jvm.library)
}

dependencies {
    testImplementation(libs.junit)
    api(libs.kotlinx.collections.immutable)
}
