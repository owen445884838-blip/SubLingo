import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte: Byte -> "%02x".format(byte) }
}

val releaseStoreFilePath = providers.environmentVariable("SUBLINGO_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("SUBLINGO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("SUBLINGO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("SUBLINGO_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.sublingo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sublingo.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.sublingo.app.test.SublingoTestRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk { abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // youtubedl-android reads its zipped runtimes from nativeLibraryDir.
        jniLibs { useLegacyPackaging = true }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui:1.11.0")
    implementation("androidx.compose.runtime:runtime:1.11.0")
    implementation("androidx.compose.foundation:foundation:1.11.0")
    implementation("androidx.compose.animation:animation:1.11.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    // Keep Debug and Release on the same Material3 API surface. Without an explicit
    // version Gradle selected 1.3.1 for Debug but 1.2.1 for Release.
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.0")

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.0")
}

tasks.register("generateReleaseSbom") {
    group = "reporting"
    description = "Generates a CycloneDX JSON inventory from the resolved Release runtime artifacts."
    val output = rootProject.layout.projectDirectory.file("sbom/sublingo-release.cdx.json")
    outputs.file(output)
    doLast {
        val artifacts = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
            .sortedWith(compareBy({ it.moduleVersion.id.group }, { it.name }, { it.moduleVersion.id.version }))
        val components = artifacts.distinctBy {
            "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}"
        }.map { artifact ->
            val group = artifact.moduleVersion.id.group
            val name = artifact.name
            val version = artifact.moduleVersion.id.version
            mapOf(
                "type" to "library",
                "group" to group,
                "name" to name,
                "version" to version,
                "purl" to "pkg:maven/$group/$name@$version",
                "hashes" to listOf(mapOf("alg" to "SHA-256", "content" to artifact.file.sha256())),
            )
        }
        val document = mapOf(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.5",
            "serialNumber" to "urn:uuid:${UUID.randomUUID()}",
            "version" to 1,
            "metadata" to mapOf(
                "timestamp" to Instant.now().toString(),
                "component" to mapOf(
                    "type" to "application",
                    "group" to "com.sublingo",
                    "name" to "SubLingo",
                    "version" to android.defaultConfig.versionName,
                    "purl" to "pkg:apk/com.sublingo.app@${android.defaultConfig.versionName}",
                ),
                "tools" to listOf(mapOf("vendor" to "SubLingo", "name" to "Gradle releaseRuntimeClasspath inventory")),
            ),
            "components" to components,
        )
        val target = output.asFile
        target.parentFile.mkdirs()
        target.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(document)) + "\n")
        println("Wrote ${components.size} Release components to ${target.absolutePath}")
    }
}
