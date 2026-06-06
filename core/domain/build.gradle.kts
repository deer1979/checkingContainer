plugins {
    alias(libs.plugins.checkingcontainer.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    // javax.inject annotations only — Hilt at the :app level wires the
    // @Inject-annotated constructors. Keeps this module pure Kotlin
    // and fast to compile.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
