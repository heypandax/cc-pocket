#!/usr/bin/env bash
# Build → jpackage (bundled JRE) → tarball for Linux.
# Produces a self-contained cc-pocket-daemon app-image (runs with no system Java).
#
# Unlike macOS there is no codesigning / notarization on Linux, so this is the whole pipeline.
#
# Prereqs:
#   - JDK 17 with jpackage on PATH (JAVA_HOME set)
#   - Run on the target arch (x86_64 or aarch64): jpackage bundles the host JRE, no cross-build.
#
# Usage: scripts/release-linux.sh [version]
set -euo pipefail

VERSION="${1:-1.0.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Normalize to the release asset spelling: install.sh, UpdateService.assetNameFor and the macOS
# artifacts all say "arm64", while uname on Linux reports "aarch64".
case "$(uname -m)" in
  aarch64|arm64) ARCH="arm64" ;;
  *)             ARCH="$(uname -m)" ;;
esac

echo "==> gradle build + jpackage (bundled JRE)"
# The repo's gradle.properties pins org.gradle.java.home to the Mac dev box's Homebrew JDK,
# which doesn't exist on Linux. Override it from JAVA_HOME when set (CI / a Linux user's JDK);
# fall back to the committed pin when JAVA_HOME is unset (the Mac dev's local convenience).
./gradlew :daemon:packageDaemon -q -PappVersion="$VERSION" ${JAVA_HOME:+-Dorg.gradle.java.home="$JAVA_HOME"}
APP="$ROOT/daemon/build/jpackage/cc-pocket-daemon"
[ -d "$APP" ] || { echo "ERROR: jpackage output not found at $APP"; exit 1; }

# jpackage's native Linux launcher consumes root-level --version before the Kotlin main sees it
# (1.3.29 therefore printed Java/usage output even though MainKt handled the flag). Keep the native
# launcher beside a tiny stable wrapper: intercept exactly --version, forward every other invocation
# byte-for-byte. The service/updater still anchors on bin/cc-pocket-daemon as before.
NATIVE="$APP/bin/cc-pocket-daemon.bin"
mv "$APP/bin/cc-pocket-daemon" "$NATIVE"
# The native launcher resolves its cfg from its own basename after the rename.
cp "$APP/lib/app/cc-pocket-daemon.cfg" "$APP/lib/app/cc-pocket-daemon.bin.cfg"
printf '%s\n' \
  '#!/bin/sh' \
  'if [ "$#" -eq 1 ] && [ "$1" = "--version" ]; then' \
  "  echo 'cc-pocket-daemon $VERSION'" \
  '  exit 0' \
  'fi' \
  'DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)' \
  'exec "$DIR/cc-pocket-daemon.bin" "$@"' \
  > "$APP/bin/cc-pocket-daemon"
chmod 0755 "$APP/bin/cc-pocket-daemon"

echo "==> tarball + checksum"
OUT="cc-pocket-daemon-${VERSION}-linux-${ARCH}.tar.gz"
tar -C "$(dirname "$APP")" -czf "$OUT" "$(basename "$APP")"
echo ""
echo "    artifact : $OUT"
echo "    sha256   : $(sha256sum "$OUT" | awk '{print $1}')"
echo ""
echo "Install on the target machine:"
echo "  mkdir -p ~/.local/share/cc-pocket/versions/$VERSION ~/.local/bin"
echo "  tar -xzf $OUT -C ~/.local/share/cc-pocket/versions/$VERSION"
echo "  ln -sfn ~/.local/share/cc-pocket/versions/$VERSION/cc-pocket-daemon/bin/cc-pocket-daemon ~/.local/bin/cc-pocket-daemon"
echo "  ~/.local/bin/cc-pocket-daemon service-install --apply --exec ~/.local/bin/cc-pocket-daemon"
