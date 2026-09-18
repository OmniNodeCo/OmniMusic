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

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":shared"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            // The JDK has no MP3 decoder. Music APIs hand back MP3 previews, so desktop playback
            // needs a Java Sound SPI. Uncomment these two lines to enable it:
            //
            // implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
            // implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
            //
            // Without them the app still runs — playback reports a clear "no decoder for this
            // stream" error from JavaSoundAudioOutput instead of failing silently.
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
        versionCode = 1
        versionName = "1.0.0"
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OmniMusic"
            packageVersion = "1.0.0"
            description = "A music player for Android, Windows, macOS and Linux"
            copyright = "© 2026 OmniNodeCo"

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
