#!/usr/bin/env bash
# Installs a Kotlin/JVM toolchain (JRE + kotlinc) without Gradle or Maven Central.
#
# This is what lets `tools/verify.sh` compile and run the platform-independent core in environments
# that have no Android SDK and no access to repo1.maven.org. A normal workstation build uses Gradle
# instead — see README.md. Both paths compile the very same sources under shared/.
set -euo pipefail

TOOLCHAIN="${OMNIMUSIC_TOOLCHAIN:-$HOME/.cache/omnimusic}"
KOTLIN_VERSION="${KOTLIN_VERSION:-2.4.20}"
JDK4PY_VERSION="${JDK4PY_VERSION:-25.0.2.1}"
JDK_DIR="$TOOLCHAIN/jdk/jdk4py/java-runtime"
KOTLIN_DIR="$TOOLCHAIN/kotlinc"

mkdir -p "$TOOLCHAIN"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "==> Installing JRE (jdk4py $JDK4PY_VERSION from PyPI)"
  wheel_url="$(curl -fsSL "https://pypi.org/pypi/jdk4py/json" |
    python3 -c "import json,sys;d=json.load(sys.stdin);print([u['url'] for u in d['releases']['$JDK4PY_VERSION'] if 'manylinux' in u['filename'] and 'x86_64' in u['filename']][0])")"
  curl -fsSL -o "$TOOLCHAIN/jdk4py.whl" "$wheel_url"
  rm -rf "$TOOLCHAIN/jdk"
  python3 -c "import zipfile;zipfile.ZipFile('$TOOLCHAIN/jdk4py.whl').extractall('$TOOLCHAIN/jdk')"
  chmod -R +x "$JDK_DIR/bin"
  rm -f "$TOOLCHAIN/jdk4py.whl"
fi

if [ ! -f "$KOTLIN_DIR/lib/kotlin-compiler.jar" ]; then
  echo "==> Installing Kotlin compiler $KOTLIN_VERSION (from the npm registry)"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/kotlinc.tgz" \
    "https://registry.npmjs.org/kotlin-compiler/-/kotlin-compiler-$KOTLIN_VERSION.tgz"
  mkdir -p "$KOTLIN_DIR"
  tar xzf "$tmp/kotlinc.tgz" -C "$KOTLIN_DIR" --strip-components=1
  rm -rf "$tmp"
fi

"$JDK_DIR/bin/java" -version 2>&1 | head -1
echo "toolchain ready at $TOOLCHAIN"
