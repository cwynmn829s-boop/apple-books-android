#!/bin/sh
# Bootstrap Gradle 8.7 when a wrapper JAR is not present in this source checkout.
set -eu
VERSION=8.7
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/manual-gradle-$VERSION"
BIN="$BASE/gradle-$VERSION/bin/gradle"
if [ ! -x "$BIN" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle.zip"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  else wget -q "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -O "$ZIP"; fi
  unzip -q -o "$ZIP" -d "$BASE"
  rm -f "$ZIP"
fi
exec "$BIN" "$@"
