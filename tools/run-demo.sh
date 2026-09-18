#!/usr/bin/env bash
# Builds and runs the command-line demo of the shared core.
# Add --fixtures shared/src/desktopTest/fixtures to replay recorded API responses instead of going live.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=tools/env.sh
source tools/env.sh

OUT="build/demo"
rm -rf "$OUT"

compile_kotlin "$OUT" "$KOTLIN_STDLIB" \
  shared/src/commonMain/kotlin \
  shared/src/desktopMain/kotlin \
  shared/src/desktopTest/kotlin/co/omnimusic/core/testing/FixtureHttpFetcher.kt \
  tools/demo/DemoMain.kt 2>&1 | grep -vE "^WARNING" || true

run_kotlin "$OUT:$KOTLIN_STDLIB" co.omnimusic.tools.demo.DemoMain "$@"
