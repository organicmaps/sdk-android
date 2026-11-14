import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

buildscript {
    // Applied scripts need the existing plugin classpath to use AGP's public artifact API.
    dependencies { classpath(files(rootProject.buildscript.configurations.getByName("classpath"))) }
}

fun Element.elements(tag: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

fun verifyJniContracts(xml: String, mapping: String, variant: String) {
    val parser = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder()
    val packages = parser.parse(xml.byteInputStream()).getElementsByTagName("package")
    val classes = buildMap {
        for (index in 0 until packages.length) {
            val pkg = packages.item(index) as Element
            pkg.elements("class").forEach { type ->
                val name = listOf(pkg.getAttribute("name"), type.getAttribute("name"))
                    .filter { it.isNotEmpty() }.joinToString(".")
                put(name, type)
            }
        }
    }
    val http = "app.organicmaps.sdk.util.HttpClient"
    listOf(http, "$http\$Params", "okhttp3.Call").forEach { type ->
        if (type !in classes) throw GradleException("JNI type missing in $variant: $type")
    }

    fun requireMethod(type: String, name: String, result: String, parameters: List<String>, isStatic: Boolean) {
        val found = classes[type]?.elements("method")?.any { method ->
            method.getAttribute("name") == name && method.getAttribute("return") == result &&
                method.getAttribute("static") == isStatic.toString() && method.getAttribute("abstract") == "false" &&
                method.elements("parameter").map { it.getAttribute("type") } == parameters
        } == true
        if (!found) {
            val kind = if (isStatic) "static" else "concrete instance"
            throw GradleException(
                "JNI method missing in $variant: $kind $type.$name(${parameters.joinToString()}): $result",
            )
        }
    }

    requireMethod(http, "runAsync", "okhttp3.Call", listOf("$http\$Params", "long"), true)
    // JNI gets cancel() from the returned object's class. Only use the mapping to locate that class;
    // original method signatures in mapping.txt do not establish the final DEX descriptor.
    val realCall = Regex("(?m)^okhttp3\\.internal\\.connection\\.RealCall -> ([^:]+):$")
        .find(mapping)?.groupValues?.get(1) ?: throw GradleException("JNI concrete Call type missing in $variant")
    requireMethod(realCall, "cancel", "void", emptyList(), false)
}

val verifierTests = tasks.register("testJniContractVerifier") {
    group = "verification"
    description = "Check that the APK verifier rejects missing, renamed and changed JNI contracts."
    val fixture = rootProject.layout.projectDirectory.file("gradle/tests/jni-contracts.xml")
    inputs.file(fixture)
    doLast {
        val xml = fixture.asFile.readText()
        val mapping = "okhttp3.internal.connection.RealCall -> a:"
        verifyJniContracts(xml, mapping, "valid fixture")
        val invalid = mapOf(
            "removed runAsync" to xml.replace(Regex("(?s)<method name=\"runAsync\".*?</method>"), ""),
            "renamed runAsync" to xml.replace("name=\"runAsync\"", "name=\"renamed\""),
            "instance runAsync" to xml.replace("static=\"true\"", "static=\"false\""),
            "changed return descriptor" to xml.replace("return=\"okhttp3.Call\"", "return=\"a\""),
            "changed parameter descriptor" to xml.replace("type=\"long\"", "type=\"int\""),
            "renamed HTTP type" to xml.replace("name=\"HttpClient\"", "name=\"Renamed\""),
            "renamed Params type" to xml.replace("name=\"HttpClient\$Params\"", "name=\"Renamed\""),
            "removed cancellation" to xml.replace(Regex("(?s)<method name=\"cancel\".*?</method>"), ""),
            "static cancellation" to xml.replace("name=\"cancel\" static=\"false\"", "name=\"cancel\" static=\"true\""),
        )
        invalid.forEach { (name, candidate) ->
            val rejected = try {
                verifyJniContracts(candidate, mapping, name)
                false
            } catch (_: GradleException) {
                true
            }
            if (!rejected) throw GradleException("The JNI verifier accepted $name")
        }
    }
}

val verifyReleaseJniContracts = tasks.register("verifyReleaseJniContracts") {
    group = "verification"
    description = "Verify the final HTTP/JNI types, method descriptors and staticness in every release APK."
    dependsOn(verifierTests)
}

val android = extensions.getByType<ApplicationExtension>()
val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
val dexdump = androidComponents.sdkComponents.sdkDirectory.map { sdk ->
    val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
    sdk.file("build-tools/${android.buildToolsVersion}/dexdump$suffix")
}
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val apks = variant.artifacts.get(SingleArtifact.APK)
    val mapping = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
    val loader = variant.artifacts.getBuiltArtifactsLoader()
    val verify = tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}JniContracts") {
        group = "verification"
        inputs.dir(apks)
        inputs.file(mapping)
        inputs.file(dexdump)
        doLast {
            val artifacts =
                loader.load(apks.get()) ?: throw GradleException("No release APK metadata for ${variant.name}")
            if (artifacts.elements.isEmpty()) throw GradleException("No release APKs for ${variant.name}")
            artifacts.elements.forEach { artifact ->
                // dexdump reads all classes*.dex entries from the packaged APK, including secondary DEX files.
                val xml = providers.exec {
                    commandLine(dexdump.get().asFile, "-l", "xml", artifact.outputFile)
                }.standardOutput.asText.get()
                verifyJniContracts(xml, mapping.get().asFile.readText(), variant.name)
                logger.lifecycle("${variant.name}: verified HTTP/JNI contracts in ${File(artifact.outputFile).name}")
            }
        }
    }
    verifyReleaseJniContracts.configure { dependsOn(verify) }
}

tasks.register("verifySdkConsumerDependencies") {
    group = "verification"
    description = "Verify the SDK module set and optional-module isolation in every debug and release variant."
    doLast {
        val core = setOf("sdk", "location-core", "maps-world")
        val optional = setOf("car", "widgets-lanes", "widgets-speedlimit")
        android.productFlavors.forEach { flavor ->
            val expected = when (flavor.name) {
                "basic", "offline", "routing" -> core
                "consumerNative" -> core + optional
                "consumerGoogle" -> core + optional + "location-google"
                "consumerFoss" -> core + optional + "location-foss"
                else -> throw GradleException("No expected SDK module set for ${flavor.name}")
            }
            listOf("Debug", "Release").forEach { buildType ->
                val variant = "${flavor.name}$buildType"
                val actual = configurations.getByName(
                    "${variant}RuntimeClasspath",
                ).incoming.resolutionResult.allComponents
                    .mapNotNull { it.moduleVersion?.takeIf { module -> module.group == "app.organicmaps.sdk" }?.name }
                    .toSet()
                if (actual != expected) {
                    throw GradleException(
                        "Unexpected SDK modules in $variant: expected ${expected.sorted()}, got ${actual.sorted()}",
                    )
                }
                logger.lifecycle("$variant: ${actual.sorted().joinToString()}")
            }
        }
    }
}
