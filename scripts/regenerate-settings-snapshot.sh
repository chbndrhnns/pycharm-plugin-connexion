#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "$ROOT_DIR"
  BETTERPY_UPDATE_SNAPSHOT=true ./gradlew test --tests "*PluginSettingsSnapshotTest*"
)
