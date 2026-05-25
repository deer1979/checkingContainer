pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Testo3"

include(":app")

// Core modules
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:data")
include(":core:domain")

// Feature modules
include(":feature:tasks")
include(":feature:login")
include(":feature:settings")
