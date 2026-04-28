#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/.android/sdk}}"
CMDLINE_VERSION="20.0"
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
CMDLINE_ZIP_PATH="${RUNNER_TEMP:-/tmp}/commandlinetools-linux.zip"
CMDLINE_DIR="$ANDROID_SDK_ROOT/cmdline-tools/$CMDLINE_VERSION"
SDKMANAGER_BIN="$CMDLINE_DIR/bin/sdkmanager"

retry() {
  local attempts="$1"
  shift
  local count=1
  while true; do
    if "$@"; then
      return 0
    fi
    if [ "$count" -ge "$attempts" ]; then
      return 1
    fi
    count=$((count + 1))
    sleep $((count * 2))
  done
}

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" "$ANDROID_SDK_ROOT/licenses"

if [ ! -x "$SDKMANAGER_BIN" ]; then
  EXTRACT_ROOT="${RUNNER_TEMP:-/tmp}/android-cmdline-tools-extract"
  rm -rf "$CMDLINE_DIR" "$ANDROID_SDK_ROOT/cmdline-tools/latest" "$EXTRACT_ROOT"
  retry 5 curl --fail --location --retry 5 --retry-all-errors --connect-timeout 20 --max-time 600 "$CMDLINE_ZIP_URL" -o "$CMDLINE_ZIP_PATH"
  mkdir -p "$EXTRACT_ROOT" "$CMDLINE_DIR"
  unzip -q -o "$CMDLINE_ZIP_PATH" -d "$EXTRACT_ROOT"
  if [ -d "$EXTRACT_ROOT/cmdline-tools" ]; then
    cp -R "$EXTRACT_ROOT/cmdline-tools/." "$CMDLINE_DIR/"
  else
    cp -R "$EXTRACT_ROOT/." "$CMDLINE_DIR/"
  fi
fi

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$CMDLINE_DIR/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

if [ -n "${GITHUB_PATH:-}" ]; then
  {
    printf '%s\n' "$CMDLINE_DIR/bin"
    printf '%s\n' "$ANDROID_SDK_ROOT/platform-tools"
  } >> "$GITHUB_PATH"
fi

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    printf 'ANDROID_HOME=%s\n' "$ANDROID_HOME"
    printf 'ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"
  } >> "$GITHUB_ENV"
fi

set +o pipefail
yes | "$SDKMANAGER_BIN" --licenses >/dev/null
set -o pipefail

retry 5 "$SDKMANAGER_BIN" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
