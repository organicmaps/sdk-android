plugins {
    alias(libs.plugins.android.application) apply false
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
    args("--editorconfig=.editorconfig", "app/src/**/*.kt", "*.gradle.kts")
}
tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("--format", "--editorconfig=.editorconfig", "app/src/**/*.kt", "*.gradle.kts")
}
tasks.register("checkSamples") {
    group = "verification"
    description = "Build and lint all independently installable samples, including minified releases."
    dependsOn("ktlintCheck", ":app:assembleDebug", ":app:assembleRelease")
    listOf("Basic", "Offline", "Routing").forEach { sample ->
        dependsOn(":app:lint${sample}Debug", ":app:lint${sample}Release")
    }
}
