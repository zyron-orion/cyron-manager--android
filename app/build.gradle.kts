import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cyron.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyron.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
    }

    buildFeatures { compose = true; buildConfig = true }

    // A phone only needs its own ABI; shipping both native stacks wastes tens of
    // megabytes. Keep a universal APK as an explicit convenience artifact.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }
    val signingStoreFile = providers.environmentVariable("CYRON_KEYSTORE_PATH").orNull
        ?: keystoreProperties.getProperty("storeFile")
    val signingStorePassword = providers.environmentVariable("CYRON_KEYSTORE_PASSWORD").orNull
        ?: keystoreProperties.getProperty("storePassword")
    val signingKeyAlias = providers.environmentVariable("CYRON_KEY_ALIAS").orNull
        ?: keystoreProperties.getProperty("keyAlias")
    val signingKeyPassword = providers.environmentVariable("CYRON_KEY_PASSWORD").orNull
        ?: keystoreProperties.getProperty("keyPassword")
    val signingValues = listOf(
        "CYRON_KEYSTORE_PATH/storeFile" to signingStoreFile,
        "CYRON_KEYSTORE_PASSWORD/storePassword" to signingStorePassword,
        "CYRON_KEY_ALIAS/keyAlias" to signingKeyAlias,
        "CYRON_KEY_PASSWORD/keyPassword" to signingKeyPassword
    )
    signingConfigs {
        if (signingValues.any { !it.second.isNullOrBlank() }) {
            val missing = signingValues.filter { it.second.isNullOrBlank() }.map { it.first }
            check(missing.isEmpty()) { "Release signing is missing required values: ${missing.joinToString()}" }
            check(!signingStoreFile.isNullOrBlank() && rootProject.file(signingStoreFile).isFile) {
                "Release signing keystore file does not exist."
            }
            create("release") {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.jniLibs.useLegacyPackaging = true

    // Duplicate Java resources need one packaging winner; collectDependencyLicenses
    // also preserves every notice as a separately generated asset.
    packaging.resources.pickFirsts += setOf(
        "/META-INF/LICENSE.md",
        "/META-INF/NOTICE.md"
    )

    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin { jvmToolchain(17) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.ytdlp.android)
    implementation(libs.ffmpeg.kit.full)
    implementation(libs.libtorrent4j)
    runtimeOnly(libs.libtorrent4j.android.arm64)
    runtimeOnly(libs.libtorrent4j.android.x86)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val generatedLicenseAssets = layout.buildDirectory.dir("generated/licenses/assets")
val releaseRuntimeArtifactFiles = provider {
    configurations.getByName("releaseRuntimeClasspath").incoming.artifacts.artifacts.map { it.file }.toSet()
}
android.sourceSets.getByName("main").assets.srcDir(generatedLicenseAssets)

val collectDependencyLicenses = tasks.register("collectDependencyLicenses") {
    group = "release"
    description = "Collects runtime dependency notices and license texts into app assets."
    inputs.files(releaseRuntimeArtifactFiles)
    inputs.dir("src/main/assets/licenses")
    outputs.dir(generatedLicenseAssets)
    doLast {
        val assetRoot = generatedLicenseAssets.get().asFile
        assetRoot.deleteRecursively()
        val outputDirectory = assetRoot.resolve("licenses")
        outputDirectory.mkdirs()
        val bundle = StringBuilder()
        val index = StringBuilder()
        val noticeEntry = Regex(
            "(?i)(?:^|/)(?:license|licence|copying|notice|third[-_]?party[-_]?notices|authors)(?:[._-]|$)|(?:^|/)source\\.txt$|(?:^|/)licenses?/"
        )

        fun appendSection(component: String, artifact: String, entry: String, content: String) {
            bundle.appendLine("=".repeat(78))
            bundle.appendLine("COMPONENT: $component")
            bundle.appendLine("ARTIFACT: $artifact")
            bundle.appendLine("ENTRY: $entry")
            bundle.appendLine("-".repeat(78))
            bundle.appendLine(content.trimEnd())
            bundle.appendLine()
            index.appendLine("$component | $artifact | $entry")
        }

        val nestedDirectory = File(outputDirectory.parentFile, "license-nested").apply {
            deleteRecursively()
            mkdirs()
        }

        fun collectArchive(zip: ZipFile, component: String, artifact: String, prefix: String = "", depth: Int = 0) {
            val artifactDirectory = component.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && noticeEntry.containsMatchIn(it.name) }
                .sortedBy { it.name }
                .toList()
            for (entry in entries) {
                val fullEntry = "$prefix${entry.name}"
                val content = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
                val safeEntryPath = fullEntry.split('/').joinToString("/") { segment ->
                    segment.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "_"
                }
                val target = File(outputDirectory, "$artifactDirectory/$safeEntryPath")
                target.parentFile.mkdirs()
                target.writeText(content)
                appendSection(component, artifact, fullEntry, content)
            }
            if (depth >= 3) return
            val nestedEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && (it.name.endsWith(".imy") || it.name.equals("classes.jar")) }
                .sortedBy { it.name }
                .toList()
            for (nested in nestedEntries) {
                val temporary = File.createTempFile("cyron-license-", ".zip", nestedDirectory)
                try {
                    zip.getInputStream(nested).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    ZipFile(temporary).use { nestedZip ->
                        collectArchive(nestedZip, component, "$artifact!/${nested.name}", "$prefix${nested.name}!/", depth + 1)
                    }
                } finally {
                    temporary.delete()
                }
            }
        }

        val staticLicenseDirectory = file("src/main/assets/licenses")
        staticLicenseDirectory.walkTopDown()
            .filter { it.isFile && it.name != "ALL_DEPENDENCY_LICENSES.txt" }
            .sortedBy { it.relativeTo(staticLicenseDirectory).invariantSeparatorsPath }
            .forEach { source ->
                appendSection(
                    "Cyron Manager bundled asset",
                    source.relativeTo(projectDir).invariantSeparatorsPath,
                    source.name,
                    source.readText()
                )
            }

        val artifacts = configurations.getByName("releaseRuntimeClasspath").incoming.artifacts.artifacts
            .sortedBy {
                val id = it.id.componentIdentifier as ModuleComponentIdentifier
                "${id.group}:${id.module}:${id.version}:${it.file.name}"
            }
        for (artifact in artifacts) {
            val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
            val component = "${id.group}:${id.module}:${id.version}"
            ZipFile(artifact.file).use { collectArchive(it, component, artifact.file.name) }
        }

        file("src/main/assets").walkTopDown()
            .filter { it.isFile && it.extension == "whl" }
            .sortedBy { it.name }
            .forEach { wheel ->
                val parts = wheel.name.removeSuffix(".whl").split('-')
                val component = "${parts.getOrElse(0) { "unknown" }.replace('_', '-')}:${parts.getOrElse(1) { "unknown" }}"
                ZipFile(wheel).use { collectArchive(it, component, wheel.name) }
            }
        nestedDirectory.deleteRecursively()

        File(outputDirectory, "INDEX.txt").writeText(index.toString())
        File(outputDirectory, "ALL_DEPENDENCY_LICENSES.txt").writeText(bundle.toString())
    }
}

tasks.named("preBuild") { dependsOn(collectDependencyLicenses) }

val generateSbom = tasks.register("generateSbom") {
    group = "release"
    description = "Generates a deterministic CycloneDX SBOM for runtime and bundled Python dependencies."
    outputs.file(rootProject.layout.projectDirectory.file("SBOM.json"))
    doLast {
        fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun licenseFor(group: String, module: String): String? = when {
            group.startsWith("androidx.") && module == "datastore-preferences-external-protobuf" -> "BSD-3-Clause"
            group.startsWith("androidx.") -> "Apache-2.0"
            group == "com.arthenica" -> "BSD-3-Clause"
            group == "com.google.code.findbugs" -> "Apache-2.0"
            group.startsWith("com.google.dagger") -> "Apache-2.0"
            group == "com.google.guava" -> "Apache-2.0"
            group.startsWith("com.squareup.okhttp3") -> "Apache-2.0"
            group == "com.squareup.okio" -> "Apache-2.0"
            group == "dev.ffmpegkit-maintained" && module == "ffmpeg-kit-full" -> "LGPL-3.0-only"
            group == "dev.ffmpegkit-maintained" && module == "yt-dlp-android" -> "MIT"
            group == "jakarta.inject" -> "Apache-2.0"
            group == "org.jetbrains" -> "Apache-2.0"
            group.startsWith("org.jetbrains.kotlin") -> "Apache-2.0"
            group == "org.jspecify" -> "Apache-2.0"
            group == "org.libtorrent4j" -> "MIT"
            else -> null
        }

        val components = mutableListOf<Map<String, Any>>()
        val artifacts = configurations.getByName("releaseRuntimeClasspath").incoming.artifacts.artifacts
        for (artifact in artifacts) {
            val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
            val reference = "${id.group}:${id.module}:${id.version}:${artifact.file.name}"
            val component = mutableMapOf<String, Any>(
                "type" to "library",
                "bom-ref" to reference,
                "name" to id.module,
                "group" to id.group,
                "version" to id.version,
                "purl" to "pkg:maven/${id.group}/${id.module}@${id.version}",
                "scope" to "required",
                "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to sha256(artifact.file)))
            )
            licenseFor(id.group, id.module)?.let { license ->
                component["licenses"] = listOf(mapOf("license" to mapOf("id" to license)))
            }
            components += component
        }

        file("src/main/assets").walkTopDown()
            .filter { it.isFile && it.extension == "whl" }
            .sortedBy { it.name }
            .forEach { wheel ->
                val parts = wheel.name.removeSuffix(".whl").split('-')
                val name = parts.getOrElse(0) { "unknown" }.replace('_', '-')
                val version = parts.getOrElse(1) { "unknown" }
                val license = when (name) {
                    "yt-dlp" -> "Unlicense"
                    "certifi" -> "MPL-2.0"
                    else -> null
                }
                val component = mutableMapOf<String, Any>(
                    "type" to "library",
                    "bom-ref" to "pypi:$name:$version:${wheel.name}",
                    "name" to name,
                    "version" to version,
                    "purl" to "pkg:pypi/$name@$version",
                    "scope" to "required",
                    "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to sha256(wheel)))
                )
                license?.let { component["licenses"] = listOf(mapOf("license" to mapOf("id" to it))) }
                components += component
            }

        fun addBundledComponent(name: String, version: String?, license: String?, evidence: String) {
            val reference = "bundled:$name:${version ?: "unknown"}"
            val component = mutableMapOf<String, Any>(
                "type" to "library",
                "bom-ref" to reference,
                "name" to name,
                "scope" to "required",
                "properties" to listOf(mapOf("name" to "cyron:evidence", "value" to evidence))
            )
            version?.let { component["version"] = it }
            license?.let {
                component["licenses"] = listOf(
                    mapOf("license" to if (it == "Public Domain") mapOf("name" to it) else mapOf("id" to it))
                )
            }
            components += component
        }

        addBundledComponent("yt-dlp", "2026.6.9", "Unlicense", "yt-dlp-android 2.0.2 fallback package")
        addBundledComponent("certifi", "2026.6.17", "MPL-2.0", "yt-dlp-android 2.0.2 requirements-common.imy")
        addBundledComponent("setuptools", "68.2.2", "MIT", "yt-dlp-android 2.0.2 bootstrap.imy")
        addBundledComponent("pyelftools", "0.26", "Public Domain", "yt-dlp-android 2.0.2 package metadata")
        addBundledComponent("Python", "3.13.9", "PSF-2.0", "yt-dlp-android 2.0.2 native runtime")
        addBundledComponent("OpenSSL", "3.0.18", "Apache-2.0", "yt-dlp-android 2.0.2 native runtime")
        addBundledComponent("SQLite", "3.50.4", "Public Domain", "yt-dlp-android 2.0.2 native runtime")
        addBundledComponent("Chaquopy", null, "MIT", "yt-dlp-android 2.0.2 embedded Python runtime")
        addBundledComponent("FFmpeg", "n8.1.3", "LGPL-2.1-or-later", "ffmpeg-kit-full 8.1.9 native libraries")
        addBundledComponent("libtorrent", "2.0.6.0", "BSD-3-Clause", "libtorrent4j 2.0.6-26 native library")
        addBundledComponent("OpenSSL", "1.1.1m", "OpenSSL", "libtorrent4j 2.0.6-26 native library")

        val ffmpegEmbeddedLicenses = listOf(
            "cpu_features" to "Apache-2.0",
            "opencore-amr" to "Apache-2.0",
            "vo-amrwbenc" to "Apache-2.0",
            "dav1d" to "BSD-2-Clause",
            "libaom" to "BSD-2-Clause",
            "openh264" to "BSD-2-Clause",
            "kvazaar" to "BSD-3-Clause",
            "libilbc" to "BSD-3-Clause",
            "libogg" to "BSD-3-Clause",
            "libtheora" to "BSD-3-Clause",
            "libvorbis" to "BSD-3-Clause",
            "libvpx" to "BSD-3-Clause",
            "opus" to "BSD-3-Clause",
            "snappy" to "BSD-3-Clause",
            "speex" to "BSD-3-Clause",
            "expat" to "MIT",
            "fontconfig" to "MIT",
            "libxml2" to "MIT",
            "freetype" to "FTL",
            "fribidi" to "LGPL-2.1-only",
            "libsndfile" to "LGPL-2.1-only",
            "soxr" to "LGPL-2.1-only",
            "twolame" to "LGPL-2.1-only",
            "harfbuzz" to "MIT",
            "lame" to "LGPL-2.0-only",
            "shine" to "LGPL-2.0-only",
            "libass" to "ISC",
            "libiconv" to "LGPL-2.1-only",
            "libpng" to "Libpng",
            "libuuid" to null,
            "sdl" to "Zlib",
            "zimg" to "WTFPL"
        )
        ffmpegEmbeddedLicenses.forEach { (name, license) ->
            addBundledComponent(name, null, license, "ffmpeg-kit-full 8.1.9 res/raw license resource")
        }
        addBundledComponent("libdatachannel", null, null, "libtorrent4j 2.0.6-26 native source-path string")
        addBundledComponent("libjuice", null, null, "libtorrent4j 2.0.6-26 native source-path string")
        addBundledComponent("usrsctp", null, null, "libtorrent4j 2.0.6-26 native source-path string")

        val bom = mapOf(
            "\$schema" to "http://cyclonedx.org/schema/bom-1.5.schema.json",
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.5",
            "version" to 1,
            "metadata" to mapOf(
                "component" to mapOf(
                    "type" to "application",
                    "name" to "Cyron Manager",
                    "version" to android.defaultConfig.versionName
                ),
                "tools" to listOf(
                    mapOf("vendor" to "Cyron Manager", "name" to "Gradle SBOM generator", "version" to "1")
                )
            ),
            "components" to components.distinctBy { it["bom-ref"] }.sortedBy { it["bom-ref"].toString() }
        )
        rootProject.layout.projectDirectory.file("SBOM.json").asFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(bom)) + "\n"
        )
    }
}

val verifyReleaseNotices = tasks.register("verifyReleaseNotices") {
    group = "release"
    description = "Verifies required notices, provenance and native libraries in release APKs."
    doLast {
        val releaseDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = releaseDirectory.listFiles { file -> file.extension == "apk" }?.sortedBy { it.name }.orEmpty()
        check(apks.size == 3) { "Expected three release APKs, found ${apks.size}. Run assembleRelease first." }
        val licenseBundle = File(generatedLicenseAssets.get().asFile, "licenses/ALL_DEPENDENCY_LICENSES.txt").readText()
        for (marker in listOf(
            "dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9",
            "dev.ffmpegkit-maintained:yt-dlp-android:2.0.2",
            "yt-dlp:2026.10.26",
            "yt_dlp-2026.6.9.dist-info/licenses/LICENSE",
            "certifi-2026.6.17.dist-info/licenses/LICENSE",
            "setuptools-68.2.2.dist-info/LICENSE"
        )) {
            check(marker in licenseBundle) { "Generated license bundle is missing $marker" }
        }
        val requiredAssets = listOf(
            "assets/THIRD_PARTY_NOTICES.md",
            "assets/licenses/ALL_DEPENDENCY_LICENSES.txt",
            "assets/licenses/INDEX.txt",
            "assets/licenses/Apache-2.0.txt",
            "assets/licenses/MIT.txt",
            "assets/licenses/BSD-3-Clause.txt",
            "assets/licenses/LGPL-2.0.txt",
            "assets/licenses/LGPL-2.1.txt",
            "assets/licenses/LGPL-3.0.txt",
            "assets/licenses/GPL-3.0.txt",
            "assets/licenses/MPL-2.0.txt",
            "assets/licenses/PSF-2.0.txt",
            "assets/licenses/OpenSSL-1.1.txt",
            "assets/licenses/Vazirmatn-OFL.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/BUILD-AND-RELINK.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/THIRD-PARTY-NOTICES.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/native-libs.sha256",
            "assets/licenses/ffmpeg-kit/8.1.9/libiconv-LGPL-2.1.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/upstream-res/source.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/upstream-res/license.txt",
            "assets/licenses/ffmpeg-kit/8.1.9/upstream-res/license_libiconv.txt"
        )
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toSet()
                val missing = requiredAssets.filterNot { it in entries }
                check(missing.isEmpty()) { "${apk.name} is missing notice assets: ${missing.joinToString()}" }
                val expectedAbis = when {
                    apk.name.contains("universal") -> listOf("arm64-v8a", "x86_64")
                    apk.name.contains("arm64-v8a") -> listOf("arm64-v8a")
                    apk.name.contains("x86_64") -> listOf("x86_64")
                    else -> error("Unrecognized release APK name: ${apk.name}")
                }
                for (abi in expectedAbis) {
                    for (library in listOf("libffmpegkit.so", "libavcodec.so", "libtorrent4j.so")) {
                        val entry = "lib/$abi/$library"
                        check(entry in entries) { "${apk.name} is missing $entry" }
                    }
                }
            }
            println("Verified notices and native libraries in ${apk.name}")
        }
    }
}
