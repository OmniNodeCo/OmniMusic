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

APP_DIR="composeApp/build/compose/binaries/main/app/OmniMusic"
if [ ! -d "$APP_DIR/app" ]; then
  echo "no app image at $APP_DIR - run :composeApp:createDistributable first" >&2
  exit 1
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
command -v "$JAVAC" >/dev/null 2>&1 || JAVAC=javac
command -v "$JAVA" >/dev/null 2>&1 || JAVA=java

CLASSPATH="$(find "$APP_DIR/app" -name '*.jar' | tr '\n' ':')"
echo "app jars: $(find "$APP_DIR/app" -name '*.jar' | wc -l)"
echo "decoder present: $(find "$APP_DIR/app" -name 'mp3spi*.jar' -o -name 'jlayer*.jar' | tr '\n' ' ')"

# A live preview URL from the API the app uses. Signed and short-lived, which is fine: it is used
# within seconds of being fetched.
PREVIEW="$(curl -fsS 'https://api.deezer.com/search?q=daft%20punk&limit=1' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"][0]["preview"])')"
if [ -z "$PREVIEW" ]; then
  echo "could not get a preview URL from the Deezer API" >&2
  exit 1
fi
echo "preview: ${PREVIEW%%\?*}"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
"$JAVAC" -nowarn -cp "$CLASSPATH" -d "$OUT" tools/AudioSeekCheck.java
"$JAVA" -cp "$OUT:$CLASSPATH" AudioSeekCheck "$PREVIEW"
