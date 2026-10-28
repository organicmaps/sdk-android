plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "app.organicmaps.example"
  compileSdk = 37

  // Use the built-in Kotlin support of the Android Gradle Plugin 9+.
  enableKotlin = true

  buildFeatures {
    buildConfig = true
    resValues = true
  }

  defaultConfig {
    applicationId = "app.organicmaps.example"
    minSdk = 21
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  flavorDimensions += "sample"
  productFlavors {
    create("basic") {
      dimension = "sample"
      applicationIdSuffix = ".basic"
      manifestPlaceholders["sampleActivity"] = "app.organicmaps.example.MainActivity"
      resValue("string", "app_name", "OM SDK Map")
    }
    create("offline") {
      dimension = "sample"
      applicationIdSuffix = ".offline"
      manifestPlaceholders["sampleActivity"] = "app.organicmaps.example.OfflineSearchActivity"
      resValue("string", "app_name", "OM SDK Offline Search")
    }
    create("routing") {
      dimension = "sample"
      applicationIdSuffix = ".routing"
      manifestPlaceholders["sampleActivity"] = "app.organicmaps.example.RoutingActivity"
      resValue("string", "app_name", "OM SDK Routing")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Required by the Organic Maps SDK (it is built with desugaring enabled).
    isCoreLibraryDesugaringEnabled = true
  }

  // The native core reads .mwm maps, fonts and packed polygons directly from
  // the APK using random access, so they must be stored uncompressed.
  androidResources {
    noCompress += listOf("mwm", "ttf", "packed_polygons.bin")
  }

  buildTypes {
    release {
      // Minification works out of the box: the SDK ships consumer R8 rules
      // that keep the classes accessed from the native code via JNI.
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      // Sign with the debug key so the example can be installed right away.
      signingConfig = signingConfigs.getByName("debug")
    }
  }
}

dependencies {
  val sdkVersion = providers.gradleProperty("sdkVersion").getOrElse(libs.versions.organicmaps.sdk.get())
  // The Organic Maps SDK: rendering engine, offline maps framework, location.
  implementation("app.organicmaps.sdk:sdk:$sdkVersion")
  // Optional: bundles the World overview maps for instant offline use (~70 MB).
  // Without this dependency, download them on the first start with
  // DownloadResourcesLegacyActivity natives (see how the Organic Maps app does it).
  implementation("app.organicmaps.sdk:maps-world:$sdkVersion")

  coreLibraryDesugaring(libs.android.tools.desugaring)

  implementation(libs.androidx.appcompat)
  implementation(libs.google.material)
}
