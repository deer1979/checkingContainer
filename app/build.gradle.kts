plugins {
    alias(libs.plugins.checkingcontainer.android.application)
    alias(libs.plugins.checkingcontainer.android.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.checkingcontainer"

    defaultConfig {
        applicationId = "com.checkingcontainer"
        versionCode = 2
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Committed debug keystore so every CI-built APK is signed identically.
        // Without this each runner generates its own keystore and the user
        // gets INSTALL_FAILED_UPDATE_INCOMPATIBLE when upgrading the debug
        // build on the same device. Safe to commit: debug-only, never used
        // for release / Play Store.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Feature modules
    implementation(project(":feature:splash"))
    implementation(project(":feature:login"))
    implementation(project(":feature:announcements"))
    implementation(project(":feature:admin"))
    implementation(project(":feature:users"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:units"))
    implementation(libs.androidx.material.icons.extended)

    // Core modules
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Compose (BOM resolves versions)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
