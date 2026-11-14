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
        val sdkRepository = providers.gradleProperty("sdkRepository")
        if (sdkRepository.isPresent) {
            exclusiveContent {
                forRepository { maven { url = uri(sdkRepository.get()) } }
                filter { includeGroup("app.organicmaps.sdk") }
            }
        }
        google()
        mavenCentral()
        // A local publication must use the same sdkVersion as this consumer; see README.md for the isolated workflow.
        if (!sdkRepository.isPresent) mavenLocal()
    }
}

rootProject.name = "organicmaps-sdk-example"

include(":app")
include(":runtime-checks")
