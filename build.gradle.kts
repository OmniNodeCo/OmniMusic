plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
}

// Runs the core test suite without Gradle's test framework or Maven Central.
// Same entry point as tools/run-tests.sh, so CI and a laptop run identical code.
tasks.register<Exec>("runCoreTests") {
    group = "verification"
    description = "Compiles and runs the shared Kotlin core tests with kotlinc (no Gradle test framework)."
    workingDir = rootDir
    commandLine("./tools/run-tests.sh")
}
