import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting
        // Declared, not inferred: without this the generated accessor is not in scope here and
        // `implementation(...)` below resolves against the wrong receiver (CI caught that).
        val desktopTest by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.coil.compose)
            implementation(project(":shared"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.coil.network.okhttp)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coil.network.okhttp)
            // The JDK decodes WAV/AIFF/AU only, and music APIs hand back MP3 previews, so desktop
            // playback needs a Java Sound SPI. Both coordinates verified against Maven Central.
            // JavaSoundAudioOutput converts whatever the SPI returns to PCM via PcmConversion;
            // without that step the line is opened against MPEG1L3 and Java Sound reports a
            // missing audio device instead of a missing decoder.
            implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
            implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
        }

        // AppModelTest lives here: it drives the state holder on a plain JVM and deliberately
        // depends only on :shared's main sources plus kotlin-test, so the same file is compiled by
        // the Gradle build and by tools/run-tests.sh (which is what actually runs it today — the
        // tests are plain classes discovered by name, not JUnit annotations).
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":shared"))
        }
    }
}

android {
    namespace = "co.omnimusic.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "co.omnimusic.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            // Debug signing so `assembleRelease` produces an installable artifact out of the box.
            // Replace with a real signing config before publishing.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "co.omnimusic.app.desktop.MainKt"

        nativeDistributions {
            // Windows ships as an Inno Setup installer (`--type exe`), not an MSI. jpackage drives
            // Inno Setup for that, so the build machine needs ISCC.exe; CI checks for it and
            // installs it if the runner image does not ship it.
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "OmniMusic"
            packageVersion = "1.0.1"
            vendor = "OmniNodeCo"
            description = "A music player for Android, Windows, macOS and Linux"
            copyright = "© 2026 OmniNodeCo"

            // The bundled runtime is a jlink image, and the module set jdeps infers misses anything
            // reached reflectively or through ServiceLoader — which describes Java Sound's SPI
            // lookup for the MP3 decoder and the JDK's TLS providers. A module that is missing does
            // not fail the build; it fails on the user's machine as the launcher's opaque "Failed
            // to launch JVM". Bundling every module costs tens of megabytes and removes the class
            // of failure rather than the instance.
            includeAllModules = true

            macOS {
                bundleID = "co.omnimusic.app"
                packageName = "OmniMusic"
            }
            windows {
                menuGroup = "OmniMusic"
                upgradeUuid = "9F1B7E42-6C1D-4A5B-9E2A-3D8C5F0A1B77"
            }
            linux {
                packageName = "omnimusic"
            }
        }
    }
}
