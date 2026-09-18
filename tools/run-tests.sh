#!/usr/bin/env bash
# Compiles the Kotlin core together with its tests and runs them — no Gradle, no Maven Central.
# This is the check that must pass before a change is called done.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=tools/env.sh
source tools/env.sh

OUT="build/test"
rm -rf "$OUT"

echo "==> compiling shared (commonMain + desktopMain) and tests"
compile_kotlin "$OUT" "$KOTLIN_STDLIB:$KOTLIN_TEST_JAR" \
  shared/src/commonMain/kotlin \
  shared/src/desktopMain/kotlin \
  shared/src/commonTest/kotlin \
  shared/src/desktopTest/kotlin \
  tools/testing/TestRunner.kt

echo "==> running tests"
run_kotlin "$OUT:$KOTLIN_STDLIB:$KOTLIN_TEST_JAR" co.omnimusic.tools.testing.TestRunner "$OUT"
