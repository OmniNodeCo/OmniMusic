#!/usr/bin/env bash
# Shared helpers for the Gradle-free build of the Kotlin core.
# Source this file; do not execute it.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${OMNIMUSIC_TOOLCHAIN:-$HOME/.cache/omnimusic}"
export JAVA_HOME="$TOOLCHAIN/jdk/jdk4py/java-runtime"
KOTLIN_HOME="$TOOLCHAIN/kotlinc"
KOTLIN_LIB="$KOTLIN_HOME/lib"
JAVA="$JAVA_HOME/bin/java"
KOTLIN_STDLIB="$KOTLIN_LIB/kotlin-stdlib.jar:$KOTLIN_LIB/kotlin-stdlib-jdk8.jar:$KOTLIN_LIB/annotations-13.0.jar"
KOTLIN_TEST_JAR="$KOTLIN_LIB/kotlin-test.jar"

if [ ! -x "$JAVA" ] || [ ! -f "$KOTLIN_LIB/kotlin-compiler.jar" ]; then
  echo "toolchain missing — run tools/setup-toolchain.sh first" >&2
  return 1 2>/dev/null || exit 1
fi

# Compiles Kotlin sources to JVM bytecode without Gradle.
# usage: compile_kotlin <output-dir> <classpath> <source paths...>
compile_kotlin() {
  local out="$1"; shift
  local classpath="$1"; shift
  mkdir -p "$out"
  "$JAVA" \
    -cp "$KOTLIN_LIB/kotlin-compiler.jar:$KOTLIN_STDLIB" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -nowarn \
    -jvm-target 17 \
    -classpath "$classpath" \
    -d "$out" \
    "$@"
}

run_kotlin() {
  local classpath="$1"; shift
  "$JAVA" -cp "$classpath" "$@"
}
