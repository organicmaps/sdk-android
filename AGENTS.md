# Repository Guidelines

## Project Structure & Module Organization

The `app` module demonstrates the Organic Maps Android SDK with independently installable `basic`, `offline`, and `routing` tutorial flavors. The `consumerNative`, `consumerGoogle`, and `consumerFoss` flavors validate the published module combinations. The separate `runtime-checks` test module probes the minified Native consumer using only Android platform APIs.

- `app/src/main/java/app/organicmaps/example/`: Kotlin activities and `ExampleApplication`, which owns SDK initialization and readiness.
- `app/src/main/res/`: layouts, strings, themes, and drawable assets; the manifest is in `app/src/main/`.
- `app/src/consumer/`: shared consumer activity, resources, and navigation widget previews.
- `runtime-checks/src/main/`: platform-only release instrumentation runner, with no SDK or AndroidX dependencies.
- `gradle/sdk-consumer-checks.gradle.kts`: final-APK JNI checks, verifier regressions, and resolved SDK module assertions.
- `gradle/libs.versions.toml`: dependency versions; `.github/workflows/android-check.yaml`: CI.

## Build, Test, and Development Commands

Use JDK 21, Android SDK Platform 37, and an API 21+ device or emulator. The sample consumes native libraries from SDK AARs and needs no NDK.

Build fresh SDK artifacts from a sibling Organic Maps checkout:

```sh
(cd ../organicmaps/android && ./gradlew -Parm64 -PsdkVersion=0.0.0-sdk-check publishSdkForChecks)
./gradlew -PsdkRepository=../organicmaps/android/build/sdk-check/repository -PsdkVersion=0.0.0-sdk-check checkSamples
```

`checkSamples` checks Kotlin style, builds and lints all six debug and minified release variants, builds the tutorial instrumentation APKs and release probe, checks optional-module isolation, and verifies HTTP/JNI contracts in every final release APK with the Android build tools' `dexdump`. Keep the same repository/version flags when running other sample tasks:

- `./gradlew :app:assembleBasicDebug`: build the basic sample.
- `./gradlew :app:installOfflineDebug`: install offline search; use `installRoutingDebug` for routing.
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat`: check or fix Kotlin formatting.
- `./gradlew :app:verifyReleaseJniContracts`: inspect the final release DEX types, method descriptors, and staticness.
- `./gradlew :app:verifySdkConsumerDependencies`: check SDK module isolation in all debug and release configurations.
- `./gradlew verifyConnectedConsumer`: run the platform-only probe against `consumerNativeRelease` on a matching device.

## Coding Style & Naming Conventions

Follow `.editorconfig`: four-space indentation for Kotlin and Gradle Kotlin scripts, 120-character lines, UTF-8, LF, and a final newline. Use ktlint's `android_studio` style and trailing commas. Name classes/files in PascalCase, functions/properties in camelCase, and Android resources in snake_case. Keep UI text in `res/values/strings.xml`.

## Testing Guidelines

AndroidX tutorial instrumentation tests live in `app/src/androidTest/`, `app/src/androidTestOffline/`, and `app/src/androidTestRouting/`; the platform-only release probe lives in `runtime-checks/src/main/`. Run `checkSamples` before submitting changes and `connectedSamples verifyConnectedConsumer` on a matching device or emulator. The Google/FOSS consumers receive build and lint coverage; actual provider behavior requires an appropriate Google/microG environment. Car is included for dependency/build coverage. Verify rotation, light/dark themes, location permissions, cancelled downloads, offline search, missing-map routing, and background/foreground transitions. Repeat relevant checks with minified offline/routing APKs; the Native probe does not cover those feature scenarios. See `README.md` for sample walkthroughs.

## Commit & Pull Request Guidelines

Use short imperative commit subjects, such as `Add offline search and routing SDK samples`. Existing commits include `Signed-off-by` trailers; use `git commit -s`. Describe behavior changes, link relevant issues, report validation and SDK source/version, and attach screenshots for UI changes.

## Integration & Agent Instructions

Preserve lifecycle forwarding, core-library desugaring, and uncompressed `mwm`, `ttf`, and `packed_polygons.bin` assets. Keep machine-specific SDK paths in ignored `local.properties`.

## SDK compatibility and releases

- The SDK is a side product of the main Organic Maps app; the application and SDK implementation are the source of truth and may break consumers
- Keep consumer applications, tests, and compatibility CI in this repository; do not gate `organicmaps/organicmaps` changes on consumer compatibility
- After every SDK release is available on Maven Central, run `android-check.yaml` here with `sdk_version` set to the published version
- Fix consumers to match the SDK when needed, rerun the release check, and update the baseline SDK version/source pin
- `sdk_ref` selects an optional source integration check only when `sdk_version` is empty
