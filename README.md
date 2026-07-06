# Organic Maps Android SDK Example

A minimal Android app demonstrating how to integrate the
[Organic Maps](https://organicmaps.app) SDK: a fully **offline**,
privacy-respecting map engine powered by
[OpenStreetMap](https://www.openstreetmap.org) data.

| Offline map, place info | Dark map style |
|:---:|:---:|
| ![Light](docs/screenshot-light.png) | ![Dark](docs/screenshot-dark.png) |

The example shows:

- Native core initialization (`OrganicMaps`, `MapView`, `MapController`)
- An interactive offline world map rendered with OpenGL ES
- The "my position" button with runtime location permissions
- Place selection: tap a city or any point to see its info
- Light/dark map styles following the system theme
- Zoom buttons and edge-to-edge layout with correct insets

## Requirements

- JDK 17+
- Android SDK Platform 36 (`compileSdk = 36`)
- An Android 5.0+ (API 21) device or emulator
- Android NDK is **not** required to build this example. Without it, the build
  prints an "Unable to strip liborganicmaps.so" warning and packages the
  library with debug symbols as-is.

## Build and run

> **Note:** the SDK has not been published to Maven Central yet. Until then,
> build it locally (see below). After the SDK is released, this section will
> shrink to just the `dependencies` block.

1. Build the SDK and install it to the local Maven repository (`~/.m2`):

   ```bash
   git clone --recurse-submodules --shallow-submodules https://github.com/organicmaps/organicmaps.git
   cd organicmaps
   ./configure.sh
   cd android
   ./gradlew publishToMavenLocal
   ```

2. Check the published version and update `organicmaps-sdk` in
   [gradle/libs.versions.toml](gradle/libs.versions.toml) if it differs:

   ```bash
   ls ~/.m2/repository/app/organicmaps/sdk/sdk/
   ```

3. Build, install and run the example:

   ```bash
   ./gradlew :app:installDebug
   adb shell am start -n app.organicmaps.example/.MainActivity
   ```

   Or simply open this project in Android Studio and press Run.

## Integration guide

### 1. Add the dependencies

The SDK is an ordinary Android library (AAR) with the native engine and the
required map data (drawing styles, fonts, classifiers) inside:

```kotlin
dependencies {
  implementation("app.organicmaps.sdk:sdk:<version>")
  // Optional: bundles the World overview maps for instant offline use (~70 MB).
  implementation("app.organicmaps.sdk:maps-world:<version>")
}
```

Optional add-ons published alongside the SDK:

| Artifact | Purpose |
|---|---|
| `maps-world` | Bundled `World.mwm` / `WorldCoasts.mwm` overview maps |
| `location-google` | Fused location provider via Google Play Services |
| `location-foss` | Fused location fallback for GMS-free devices |
| `car` | Android Auto (`androidx.car.app`) integration |
| `widgets-lanes`, `widgets-speedlimit` | Navigation UI widgets |

Without `maps-world`, the map is empty until the World files are downloaded on
the first start (see `DownloadResourcesLegacyActivity` natives and how the
Organic Maps app uses them). Without a `location-*` module, the SDK falls back
to the standard Android location providers, which is fine for most apps.

### 2. Configure packaging

Two settings in `app/build.gradle.kts` are **required**:

```kotlin
android {
  compileOptions {
    // The SDK is built with core library desugaring; the Android Gradle Plugin
    // refuses to build an app without it (checked via the AAR metadata).
    isCoreLibraryDesugaringEnabled = true
  }

  // The native core reads .mwm maps and fonts directly from the APK using
  // random access, so they must be stored uncompressed. Without this, the
  // map data bundled in the AARs is deflated and the engine cannot load it.
  androidResources {
    noCompress += listOf("mwm", "ttf", "packed_polygons.bin")
  }
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
```

Location, network and storage permissions are declared in the SDK manifest and
merged into your app automatically — runtime permission requests are still your
app's job (see `MainActivity.onMyPositionModeChanged`).

### 3. Create the SDK entry point

Create the `OrganicMaps` instance in your `Application`
([ExampleApplication.kt](app/src/main/java/app/organicmaps/example/ExampleApplication.kt)):

```kotlin
class ExampleApplication : Application() {
  lateinit var organicMaps: OrganicMaps
    private set

  override fun onCreate() {
    super.onCreate()
    organicMaps = OrganicMaps(this, "example", BuildConfig.APPLICATION_ID,
                              BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
  }
}
```

### 4. Initialize and show the map

Put `app.organicmaps.sdk.MapView` in the layout, then initialize the core and
bind a `MapController` to the activity lifecycle
([MainActivity.kt](app/src/main/java/app/organicmaps/example/MainActivity.kt)):

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  organicMaps = (application as ExampleApplication).organicMaps
  // Loads bundled maps and invokes the callback on the main thread.
  // Returns false (and skips the callback) when already initialized,
  // e.g. on activity recreation.
  val callbackWillFire = organicMaps.init(::onCoreInitialized) // throws IOException
  if (!callbackWillFire)
    onCoreInitialized()
}

private fun onCoreInitialized() {
  setContentView(R.layout.activity_main)
  val controller = MapController(findViewById(R.id.map), organicMaps.locationHelper,
                                 mapRenderingListener, null, false)
  lifecycle.addObserver(controller)
}
```

Everything else — location modes, place taps, map styles, zoom — is a few
lines each; see the comments in
[MainActivity.kt](app/src/main/java/app/organicmaps/example/MainActivity.kt).

## Good to know

- **`Map` name clash (Kotlin):** the SDK class `app.organicmaps.sdk.Map`
  shadows `kotlin.collections.Map`; import it with an alias:
  `import app.organicmaps.sdk.Map as OMMap`.
- **R8/ProGuard:** minified builds work out of the box — the SDK ships
  consumer R8 rules (the native code calls Java classes and methods by name,
  so they must not be renamed or stripped). The example release build has
  `minifyEnabled = true` to prove it. Avoid `shrinkResources` for now: some
  SDK drawables are looked up by name at runtime.
- **APK size:** the SDK AAR carries the engine (per-ABI `.so`) and ~40 MB of
  map data assets; `maps-world` adds ~70 MB. Use Android App Bundles so users
  download only their ABI.
- **Downloading regions:** country maps with full detail (POIs, streets,
  search, routing) are managed by `app.organicmaps.sdk.downloader.MapManager`.
  The bundled World map intentionally contains only an overview.
- **More SDK surface** to explore beyond this example: `search`, `routing`,
  `bookmarks`, `editor`, `maplayer` (subway/isolines/traffic) packages, and the
  [Organic Maps app](https://github.com/organicmaps/organicmaps/tree/master/android)
  itself — the most complete SDK consumer.

## Support

- Email: [sdk@organicmaps.app](mailto:sdk@organicmaps.app)
- Bugs and feature requests:
  [github.com/organicmaps/organicmaps/issues](https://github.com/organicmaps/organicmaps/issues)

## License

See [LICENSE](LICENSE). The SDK code is Apache 2.0; map data and other binary
files are covered by the
[Organic Maps Binary Data License](https://github.com/organicmaps/organicmaps/blob/master/DATA_LICENSE.txt).
Apps using the SDK must clearly mention they are based on Organic Maps and
provide a clickable link to [organicmaps.app](https://organicmaps.app), and
keep the OpenStreetMap attribution visible on the map.
