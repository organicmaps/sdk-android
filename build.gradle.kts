plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
}

val ktlint = configurations.create("ktlint")
ktlint.attributes {
    attribute(
        org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
        objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
    )
}
dependencies { ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0") }

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "--editorconfig=.editorconfig",
        "app/src/**/*.kt",
        "*.gradle.kts",
        "*/build.gradle.kts",
        "gradle/**/*.gradle.kts",
    )
}
tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(
        "--format",
        "--editorconfig=.editorconfig",
        "app/src/**/*.kt",
        "*.gradle.kts",
        "*/build.gradle.kts",
        "gradle/**/*.gradle.kts",
    )
}
val sampleFlavors = listOf("Basic", "Offline", "Routing")
val consumerFlavors = listOf("ConsumerNative", "ConsumerGoogle", "ConsumerFoss")

tasks.register("checkSamples") {
    group = "verification"
    description = "Build and lint all independently installable samples, including minified releases."
    dependsOn(
        "ktlintCheck",
        ":app:assembleDebug",
        ":app:verifyReleaseJniContracts",
        ":app:verifySdkConsumerDependencies",
        ":runtime-checks:assembleRelease",
    )
    (sampleFlavors + consumerFlavors).forEach { sample ->
        dependsOn(":app:lint${sample}Debug", ":app:lint${sample}Release")
    }
    sampleFlavors.forEach { sample -> dependsOn(":app:assemble${sample}DebugAndroidTest") }
}

tasks.register("connectedSamples") {
    group = "verification"
    description = "Run each sample's instrumentation tests on a connected device or emulator."
    sampleFlavors.forEach { sample -> dependsOn(":app:connected${sample}DebugAndroidTest") }
}

tasks.register("verifyConnectedConsumer") {
    group = "verification"
    description = "Exercise a minified consumer without adding test dependencies or keep rules to its APK."
    dependsOn(":runtime-checks:verifyConnectedConsumer")
}
