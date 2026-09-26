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

rootProject.name = "zcode-android"

include(":app")
include(":core:designsystem")
include(":core:agent")
include(":core:engine")
include(":core:tools")
include(":core:terminal")
include(":core:mcp")
include(":core:storage")
include(":core:config")
include(":feature:chat")
include(":feature:terminal")
include(":feature:workspace")
include(":feature:settings")
