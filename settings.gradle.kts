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

rootProject.name = "NofAR"

include(":app")
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:data")
include(":core:network")
include(":core:ui")
include(":core:designsystem")
include(":core:testing")
include(":feature:home")
include(":feature:prepare")
include(":feature:explore")
include(":feature:settings")
