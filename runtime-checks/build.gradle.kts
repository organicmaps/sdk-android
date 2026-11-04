import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "app.organicmaps.example.checks"
    compileSdk = providers.gradleProperty("propCompileSdkVersion").get().toInt()
    enableKotlin = false
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = providers.gradleProperty("propMinSdkVersion").get().toInt()
        targetSdk = providers.gradleProperty("propTargetSdkVersion").get().toInt()
        testInstrumentationRunner = "app.organicmaps.example.checks.ConsumerTestRunner"
        missingDimensionStrategy("sample", "consumerNative")
    }

    buildTypes {
        create("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

// The target APK already packages its dependencies; resolve only that artifact.
configurations.matching { it.name.endsWith("TestedApks") }.configureEach {
    isTransitive = false
}

tasks.register("verifyConnectedConsumer") {
    group = "verification"
    dependsOn(":app:verifyReleaseJniContracts", "connectedReleaseAndroidTest")
    doLast {
        val reports = fileTree(layout.buildDirectory.dir("outputs/androidTest-results/connected/release")) {
            include("**/TEST-*.xml")
        }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val count = reports.files.sumOf { parser.parse(it).getElementsByTagName("testcase").length }
        if (count == 0) throw GradleException("No minified Maven-consumer instrumentation tests were executed")
    }
}
