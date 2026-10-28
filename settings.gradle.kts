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
        // TODO: Remove after the Organic Maps SDK is published to Maven Central.
        // Until then, build the SDK from https://github.com/organicmaps/organicmaps
        // and install it to the local Maven repository (~/.m2):
        //   cd organicmaps/android && ./gradlew publishToMavenLocal
        if (!sdkRepository.isPresent) mavenLocal()
    }
}

rootProject.name = "organicmaps-sdk-example"

include(":app")
