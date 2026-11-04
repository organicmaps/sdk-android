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

The SDK is a side product of the main Organic Maps application. Its application
and SDK implementation are the source of truth; API stability is not guaranteed.
These examples must adapt when SDK changes break integration. See the
[SDK integration guide](https://github.com/organicmaps/organicmaps/blob/f629c29ac7e523abf982a984ca089ba939b93c9e/android/sdk/README.md)
for initialization, lifecycle, permissions, packaging, and API contracts.

## Build and run

Use **JDK 21**, **Android SDK Platform 37**, and an **API 21+** device or emulator.
The examples consume native libraries from SDK AARs and do not need an NDK.

For development with SDK source, publish its artifacts locally. The pinned revision
below provides `publishSdkForChecks`, documented in
[organicmaps/organicmaps#14025](https://github.com/organicmaps/organicmaps/pull/14025).
Building that checkout requires the upstream
[native build environment](https://github.com/organicmaps/organicmaps/blob/f629c29ac7e523abf982a984ca089ba939b93c9e/docs/INSTALL.md).
With an Organic Maps checkout in the sibling `organicmaps` directory:

```sh
git -C ../organicmaps fetch origin f629c29ac7e523abf982a984ca089ba939b93c9e
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

For source integration checks, CI publishes the pinned SDK into an isolated
repository and runs these checks on an API 35 emulator. The manual workflow's
`sdk_ref` input selects another source revision when `sdk_version` is empty.
For device checks, also exercise denied/disabled location, rotation during loading,
light/dark themes, cancelled downloads, missing-map routing, and navigation arrival;
repeat with minified release APKs.

## Check every SDK release

After each SDK release is available on Maven Central, run this repository's
`Android SDK Samples` workflow with that published version:

```sh
gh workflow run android-check.yaml --repo organicmaps/sdk-android -f 'sdk_version=<published-version>'
```

This mode resolves the SDK group exclusively from Maven Central and skips SDK
source checkout, native compilation, and local publication. It builds and lints
debug/minified release examples and runs their instrumentation tests. The same
check can be started by release automation with access to this repository.

If SDK changes require consumer updates, fix the examples here and rerun the check.
Consumer compatibility does not gate changes in `organicmaps/organicmaps`.
Keep the baseline SDK version/source pin current after adopting a release and
exercise minified release APKs on a device for JNI/R8 checks. Resource shrinking
is disabled here and needs separate validation of resources resolved by name.

## Published SDK consumer checks

Together, the `consumerNative`, `consumerGoogle`, and `consumerFoss` variants resolve,
build, and lint all published SDK modules. Car and lane/speed-limit widget dependencies
are confined to these consumers; the three tutorial apps use only the core SDK and
bundled World maps, including the transitive `location-core` module.
[ConsumerActivity](app/src/consumer/java/app/organicmaps/example/ConsumerActivity.kt)
constructs navigation widget previews through ordinary host references.

`checkSamples` checks the resolved SDK module set for all debug/release configurations
to prevent optional dependencies from leaking into the tutorials or mixing providers.
It also uses the Android build tools' `dexdump` to inspect every final release APK,
including all DEX files, for the HTTP/JNI types and exact static `runAsync` and concrete
instance `cancel` method descriptors. The R8 mapping locates the obfuscated concrete
Call class; method signatures are verified in DEX. Focused verifier regressions reject
removed/renamed methods, changed descriptors and incorrect staticness.

`checkSamples` also builds the platform-only release probe. Run that probe against
`consumerNativeRelease` on a device matching the published ABI:

```sh
./gradlew -PsdkRepository=../organicmaps/android/build/sdk-check/repository -PsdkVersion=0.0.0-sdk-check verifyConnectedConsumer
```

The [probe module](runtime-checks/) has no SDK or AndroidX dependencies and adds no
keep rules to the target. It checks the HTTP descriptor, native renderer readiness,
JNI-populated region data and host-created widgets, and fails if no test executes.
The Google/FOSS variants receive build/lint coverage; actual Google Play Services or
microG provider behavior requires an appropriate environment. Car receives dependency
and build coverage. Minified offline/routing feature scenarios remain separate from
this Native smoke test, as tracked in [issue #2](https://github.com/organicmaps/sdk-android/issues/2).

The reusable workflow accepts `sdk_version` for Maven Central release checks,
`sdk_ref` for optional source integration, and `samples_ref` for the examples revision.
For a dedicated manual or post-release workflow, call it at the same examples commit
that you pass as `samples_ref`:

```yaml
jobs:
  check-sdk-consumers:
    uses: organicmaps/sdk-android/.github/workflows/android-check.yaml@SAMPLES_COMMIT_SHA
    permissions:
      contents: read
    with:
      samples_ref: SAMPLES_COMMIT_SHA
      sdk_version: PUBLISHED_SDK_VERSION
```

Replace both `SAMPLES_COMMIT_SHA` values with the same commit containing the reusable
workflow, and `PUBLISHED_SDK_VERSION` with a version available on Maven Central.
To check SDK source instead, omit `sdk_version` and optionally supply `sdk_ref`.
Consumer compatibility runs in this repository and does not gate SDK changes.

## License and support

See [LICENSE](LICENSE). Report sample issues in
[organicmaps/sdk-android](https://github.com/organicmaps/sdk-android/issues) and SDK or
engine issues in [organicmaps/organicmaps](https://github.com/organicmaps/organicmaps/issues).
