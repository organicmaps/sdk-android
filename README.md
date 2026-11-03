# Organic Maps Android SDK examples

Three independently installable Android apps demonstrate the SDK:

| Variant | Try it |
| --- | --- |
| `basic` | Pan and zoom the map, select a place, and enable your position. |
| `offline` | Find a region such as Liechtenstein, select it, and download it. Then search installed maps for a place or category. Download and search feedback are independent. |
| `routing` | Select an origin and destination, choose walking/cycling/driving, and build a route. Download missing maps when prompted. For navigation, obtain a fix and use your position as the origin. |

Each app has separate map storage. Route planning survives rotation and backgrounding;
foreground navigation ends on arrival, Stop, backgrounding, or closing the activity.
Downloads continue independently of the screen and offer cancellation and retry.

The SDK API is experimental and may change. See the
[SDK integration guide](https://github.com/organicmaps/organicmaps/blob/9b5343caf0d19f1a6e06511fdac5d6f7e59a1f12/android/sdk/README.md)
for initialization, lifecycle, permissions, packaging, and API contracts.

## Build and run

Use **JDK 21**, **Android SDK Platform 37**, and an **API 21+** device or emulator.
The examples consume native libraries from SDK AARs and do not need an NDK.

Until a public SDK release is available, publish its artifacts locally. The revision
below contains `publishSdkForChecks` from
[organicmaps/organicmaps#14025](https://github.com/organicmaps/organicmaps/pull/14025).
Building that checkout requires the upstream
[native build environment](https://github.com/organicmaps/organicmaps/blob/9b5343caf0d19f1a6e06511fdac5d6f7e59a1f12/docs/INSTALL.md).
With an Organic Maps checkout in the sibling `organicmaps` directory:

```sh
git -C ../organicmaps fetch origin 9b5343caf0d19f1a6e06511fdac5d6f7e59a1f12
git -C ../organicmaps checkout --detach FETCH_HEAD
git -C ../organicmaps submodule update --init --recursive --depth 1
(cd ../organicmaps/android && ./gradlew -Parm64 -PsdkVersion=0.0.0-sdk-check publishSdkForChecks)
./gradlew -PsdkRepository=../organicmaps/android/build/sdk-check/repository -PsdkVersion=0.0.0-sdk-check checkSamples
./gradlew -PsdkRepository=../organicmaps/android/build/sdk-check/repository -PsdkVersion=0.0.0-sdk-check :app:installBasicDebug
```

Use `installOfflineDebug` or `installRoutingDebug` for the other samples. Keep the same
`sdkRepository` and `sdkVersion` options for every command. `-Parm64` builds SDK AARs
for arm64; use `-Px86_64` in the producer for an Intel emulator. Examples package the
ABIs supplied by those AARs. Release APKs are minified and signed with the debug key
for local installation.

`sdkRepository` restricts the SDK group to the supplied repository. Without it,
Google, Maven Central, and `mavenLocal()` are available. Local Maven publication also
requires `-PsdkVersion=0.0.0-sdk-check` in the producer to match the default consumer
version; published releases can be selected with `-PsdkVersion=<version>`.

## Checks

`checkSamples` runs Kotlin style checks and Android lint, builds all debug/minified
release APKs, and builds the instrumentation tests. Run `ktlintFormat` to format Kotlin.
On a matching connected device or emulator:

```sh
./gradlew -PsdkRepository=../organicmaps/android/build/sdk-check/repository -PsdkVersion=0.0.0-sdk-check connectedSamples
```

CI publishes the pinned SDK into an isolated repository and runs these checks on an
API 35 emulator. The manual workflow's `sdk_ref` input selects another SDK revision.
For device checks, also exercise denied/disabled location, rotation during loading,
light/dark themes, cancelled downloads, missing-map routing, and navigation arrival;
repeat with minified release APKs.

## License and support

See [LICENSE](LICENSE). Report sample issues in
[organicmaps/sdk-android](https://github.com/organicmaps/sdk-android/issues) and SDK or
engine issues in [organicmaps/organicmaps](https://github.com/organicmaps/organicmaps/issues).
