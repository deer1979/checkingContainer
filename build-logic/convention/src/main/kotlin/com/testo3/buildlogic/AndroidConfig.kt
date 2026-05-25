package com.testo3.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).orElseThrow {
        IllegalStateException("Version '$alias' not found in libs.versions.toml")
    }.requiredVersion.toInt()

/**
 * Configure the Kotlin extension on either Android or JVM projects. Each
 * Android convention plugin calls this after applying its variant-specific
 * DSL (because in AGP 9 the per-property configuration — compileSdk,
 * defaultConfig, compileOptions, lint — has to be done against the
 * concrete extension type, not CommonExtension).
 */
internal fun Project.configureKotlin() {
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    extensions.findByType(KotlinJvmProjectExtension::class.java)?.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    tasks.withType<Test> {
        useJUnit()
    }
}
