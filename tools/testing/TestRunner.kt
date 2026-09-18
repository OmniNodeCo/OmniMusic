package co.omnimusic.tools.testing

import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * A dependency-free test runner.
 *
 * The project has to be verifiable on a machine with no access to Maven Central, so pulling in
 * JUnit is not an option. This discovers every compiled `*Test` class, invokes its zero-argument
 * `testXxx` methods, and reports a pass/fail summary. Assertions themselves come from `kotlin.test`,
 * which ships with the Kotlin compiler distribution.
 *
 * The same class is wired into Gradle as the `runCoreTests` task, so `tools/run-tests.sh` and
 * `./gradlew runCoreTests` run exactly the same code.
 */
object TestRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val outputDir = Path.of(args.firstOrNull() ?: "build/test")
        val classNames = discover(outputDir)
        if (classNames.isEmpty()) {
            System.err.println("no test classes found under ${outputDir.toAbsolutePath()}")
            exitProcess(2)
        }

        var passed = 0
        val failures = mutableListOf<String>()
        println("Running ${classNames.size} test classes from ${outputDir.toAbsolutePath()}")

        for (className in classNames) {
            val clazz = Class.forName(className)
            val methods = clazz.methods
                .filter { it.name.startsWith("test") && it.parameterCount == 0 && it.returnType == Void.TYPE }
                .sortedBy { it.name }
            println("  ${clazz.simpleName} (${methods.size})")
            for (method in methods) {
                try {
                    // A fresh instance per method: mutable fields in a test class must never leak
                    // from one test into the next.
                    method.invoke(clazz.getDeclaredConstructor().newInstance())
                    passed++
                    println("    ok   ${method.name}")
                } catch (e: InvocationTargetException) {
                    val cause = e.cause ?: e
                    failures += "${clazz.simpleName}.${method.name}: ${cause::class.simpleName}: ${cause.message}"
                    println("    FAIL ${method.name} -> ${cause.message}")
                }
            }
        }

        println()
        println("=".repeat(64))
        println("passed: $passed   failed: ${failures.size}")
        failures.forEach { println("  FAILED $it") }
        println("=".repeat(64))
        if (failures.isNotEmpty()) exitProcess(1)
    }

    private fun discover(outputDir: Path): List<String> =
        if (!Files.isDirectory(outputDir)) {
            emptyList()
        } else {
            Files.walk(outputDir).use { stream ->
                stream.filter { path ->
                    val name = path.fileName.toString()
                    name.endsWith("Test.class") && !name.contains('$')
                }.map { path ->
                    outputDir.relativize(path).toString()
                        .removeSuffix(".class")
                        .replace(java.io.File.separatorChar, '.')
                }.sorted().toList()
            }
        }
}
