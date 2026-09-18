#!/usr/bin/env bash
# Compiles the Kotlin core, the Compose UI and both test suites, then runs every test — no Gradle,
# no Maven Central. This is the check that must pass before a change is called done.
#
# The UI is compiled against the compile-only stubs in tools/stubs (see tools/stubs/README.md);
# that is what lets AppModel's logic be tested on a plain JVM.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=tools/env.sh
source tools/env.sh

OUT="build/test"
rm -rf "$OUT"

echo "==> compiling Compose/Android stubs"
compile_kotlin "$OUT/stubs" "$KOTLIN_STDLIB" tools/stubs

echo "==> compiling shared, composeApp and every test suite"
compile_kotlin "$OUT/classes" "$KOTLIN_STDLIB:$KOTLIN_TEST_JAR:$OUT/stubs" \
  shared/src/commonMain/kotlin \
  shared/src/desktopMain/kotlin \
  composeApp/src/commonMain/kotlin \
  shared/src/commonTest/kotlin \
  shared/src/desktopTest/kotlin \
  composeApp/src/desktopTest/kotlin \
  tools/testing/TestRunner.kt

echo "==> running tests"
run_kotlin "$OUT/classes:$KOTLIN_STDLIB:$KOTLIN_TEST_JAR:$OUT/stubs" \
  co.omnimusic.tools.testing.TestRunner "$OUT/classes"
