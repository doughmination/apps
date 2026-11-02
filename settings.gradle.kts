pluginManagement {
    repositories {
        google()
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

rootProject.name = "doughmination-apps"

// Each app is its own Android application module. They share this build and
// the version catalog, never each other's code.
include(
    ":api-helper:app",            // Device Reporter
    ":email:app",                 // Inbox
    ":doughmination-manager:app", // Doughmination Manager
    ":plural:app",                // Plural
)
