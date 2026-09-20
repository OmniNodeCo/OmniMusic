#!/usr/bin/env bash
#
# Seeks a real MP3 using the jars the packaged app ships.
#
# The unit tests decode WAV, because that is all the JDK can do without the mp3spi jar — and the
# seek bug was MP3-specific. This closes that gap on a runner, where the app image exists and the
# decoder is on the classpath. It also needs network, for two reasons: a preview URL, and the audio
# behind it.
set -euo pipefail
cd "$(dirname "$0")/.."

# Job logs are awkward to reach after the fact, so failures are also raised as annotations, which
# the check-runs API returns directly. Without this the only evidence is "exit code 1".
fail() {
  echo "FAIL: $1" >&2
  echo "::error::$1"
  exit 1
}


# Discover the image rather than naming it: the directory follows the app name, which differs in
# case from the Linux package name, and guessing it is how this script would fail silently.
BINARIES="composeApp/build/compose/binaries/main/app"
APP_DIR="$(find "$BINARIES" -mindepth 1 -maxdepth 1 -type d -print -quit 2>/dev/null)"
if [ -z "$APP_DIR" ]; then
  echo "what is under composeApp/build/compose:" >&2
  find composeApp/build/compose -maxdepth 4 2>/dev/null | head -30 >&2
  layout="$(find composeApp/build/compose -maxdepth 4 -type d 2>/dev/null | head -12 | tr '\n' ' ')"
  fail "no app image under $BINARIES; compose dirs: ${layout:-none}"
fi
echo "app image: $APP_DIR"

# jpackage lays the image out differently per platform: jars sit in app/ on Windows and macOS, but
# in lib/ on Linux. A hardcoded app/ is what made this fail on ubuntu.
FIRST_JAR="$(find "$APP_DIR" -maxdepth 2 -name '*.jar' -print -quit 2>/dev/null)"
if [ -z "$FIRST_JAR" ]; then
  fail "no jars under $APP_DIR; contents: $(find "$APP_DIR" -maxdepth 2 2>/dev/null | head -12 | tr '\n' ' ')"
fi
JAR_DIR="$(dirname "$FIRST_JAR")"
echo "jars: $JAR_DIR"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
command -v "$JAVAC" >/dev/null 2>&1 || JAVAC=javac
command -v "$JAVA" >/dev/null 2>&1 || JAVA=java

CLASSPATH="$(find "$JAR_DIR" -name '*.jar' | tr '\n' ':')"
echo "app jars: $(find "$JAR_DIR" -name '*.jar' | wc -l)"
echo "decoder present: $(find "$JAR_DIR" \( -name 'mp3spi*.jar' -o -name 'jlayer*.jar' \) | tr '\n' ' ')"

# A live preview URL from the API the app uses. Signed and short-lived, which is fine: it is used
# within seconds of being fetched.
PREVIEW="$(curl -fsS 'https://api.deezer.com/search?q=daft%20punk&limit=1' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"][0]["preview"])')"
if [ -z "$PREVIEW" ]; then
  fail "could not get a preview URL from the Deezer API"
fi
echo "preview: ${PREVIEW%%\?*}"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
command -v "$JAVAC" >/dev/null 2>&1 || fail "no javac on PATH (JAVA_HOME=${JAVA_HOME:-unset})"

if ! "$JAVAC" -nowarn -cp "$CLASSPATH" -d "$OUT" tools/AudioSeekCheck.java >/tmp/javac.log 2>&1; then
  fail "javac could not compile against the packaged jars: $(tr '\n' ' ' </tmp/javac.log | head -c 500)"
fi

if ! "$JAVA" -cp "$OUT:$CLASSPATH" AudioSeekCheck "$PREVIEW"; then
  fail "the packaged decoder could not seek a real MP3 (see the step output above)"
fi
