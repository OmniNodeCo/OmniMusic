#!/usr/bin/env bash
# Type-checks the Compose UI without Gradle.
#
# The sandbox has no Maven Central, so the real Compose/AndroidX artifacts cannot be downloaded.
# tools/stubs/ holds compile-only declarations of exactly the API surface the app touches, with
# signatures copied from the real libraries. This script type-checks the project's own UI code
# against those stubs, which catches the class of bug a plain text review misses: wrong parameter
# names, modifiers used in the wrong scope, smart casts that Kotlin will refuse, missing imports.
# It does NOT prove the UI renders or that behaviour is correct — the stub bodies are empty.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=tools/env.sh
source tools/env.sh

OUT="build/ui-check"
rm -rf "$OUT"

echo "==> compiling Compose/Android stubs"
compile_kotlin "$OUT/stubs" "$KOTLIN_STDLIB" tools/stubs

echo "==> type-checking shared + common UI + desktop entry point"
compile_kotlin "$OUT/desktop" "$KOTLIN_STDLIB:$OUT/stubs" \
  shared/src/commonMain/kotlin \
  shared/src/desktopMain/kotlin \
  composeApp/src/commonMain/kotlin \
  composeApp/src/desktopMain/kotlin

echo "==> type-checking shared + common UI + Android entry point"
compile_kotlin "$OUT/android" "$KOTLIN_STDLIB:$OUT/stubs" \
  shared/src/commonMain/kotlin \
  composeApp/src/commonMain/kotlin \
  composeApp/src/androidMain/kotlin

echo "UI type check passed (against compile-only stubs)."
