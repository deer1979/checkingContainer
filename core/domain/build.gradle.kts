plugins {
    alias(libs.plugins.testo3.jvm.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    // javax.inject annotations only — Hilt at the :app level wires the
    // @Inject-annotated constructors. Keeps this module pure Kotlin
    // and fast to compile.
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
