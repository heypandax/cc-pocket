#!/usr/bin/env bash
# Build the relay distribution and package it with the production deployment templates.
# The target server only needs a Java 17 runtime; it does not need Gradle or the source tree.
set -euo pipefail

VERSION="${1:-1.3.25}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

case "$(uname -m)" in
  aarch64|arm64) ARCH="arm64" ;;
  x86_64|amd64) ARCH="x86_64" ;;
  *)            ARCH="$(uname -m)" ;;
esac

echo "==> build relay distribution"
./gradlew :relay:installDist -q ${JAVA_HOME:+-Dorg.gradle.java.home="$JAVA_HOME"}

DIST="$ROOT/relay/build/install/cc-pocket-relay"
STAGE_ROOT="$ROOT/relay/build/release"
STAGE="$STAGE_ROOT/cc-pocket-relay"
[ -d "$DIST/bin" ] && [ -d "$DIST/lib" ] || {
  echo "ERROR: relay distribution not found at $DIST"
  exit 1
}

rm -rf "$STAGE"
mkdir -p "$STAGE/deploy"
cp -R "$DIST/bin" "$DIST/lib" "$STAGE/"
cp "$ROOT/deploy/cc-pocket-relay.service" "$STAGE/deploy/"
cp "$ROOT/deploy/Caddyfile" "$STAGE/deploy/"
cp "$ROOT/deploy/npm-compose.override.yaml" "$STAGE/deploy/"
cp "$ROOT/deploy/NPM.md" "$STAGE/deploy/"
cp "$ROOT/deploy/README.md" "$STAGE/deploy/"

OUT="$ROOT/cc-pocket-relay-${VERSION}-linux-${ARCH}.tar.gz"
tar -C "$STAGE_ROOT" -czf "$OUT" cc-pocket-relay

echo "artifact: $(basename "$OUT")"
echo "sha256:  $(sha256sum "$OUT" | awk '{print $1}')"
