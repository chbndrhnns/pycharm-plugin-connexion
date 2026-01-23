#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

VERSION="${VERSION:-${GITHUB_REF_NAME:-}}"
if [[ -z "$VERSION" ]]; then
  echo "ERROR: VERSION or GITHUB_REF_NAME is required." >&2
  exit 1
fi

CURRENT_VERSION="$(./scripts/version.sh current)"
if [[ "$CURRENT_VERSION" != "$VERSION" ]]; then
  echo "ERROR: Tag version '$VERSION' does not match gradle.properties pluginVersion '$CURRENT_VERSION'." >&2
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$VERSION" == *-dev.* ]]; then
  if [[ "$CURRENT_BRANCH" != "develop" && "$CURRENT_BRANCH" != "dev" ]]; then
    git fetch origin develop --depth=1
    if ! git merge-base --is-ancestor HEAD origin/develop; then
      echo "ERROR: Dev release tag must be based on develop." >&2
      exit 1
    fi
  fi
else
  if [[ "$CURRENT_BRANCH" != "main" ]]; then
    git fetch origin main --depth=1
    if ! git merge-base --is-ancestor HEAD origin/main; then
      echo "ERROR: Release tag must be based on main." >&2
      exit 1
    fi
  fi
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI is required in CI to create the GitHub release." >&2
  exit 1
fi

RELEASE_NOTE="./build/tmp/release_note.txt"
mkdir -p "$(dirname "$RELEASE_NOTE")"

./gradlew getChangelog --unreleased --no-header --quiet --console=plain --output-file="$RELEASE_NOTE"

if [[ "${SKIP_TESTS:-}" != "true" ]]; then
  ./gradlew test
fi

./gradlew buildPlugin
./gradlew publishPlugin

if ! ls ./build/distributions/*.zip >/dev/null 2>&1; then
  echo "ERROR: No plugin zip found in ./build/distributions." >&2
  exit 1
fi

PRERELEASE_FLAG=""
if [[ "$VERSION" == *-* ]]; then
  PRERELEASE_FLAG="--prerelease"
fi

if gh release view "$VERSION" >/dev/null 2>&1; then
  gh release edit "$VERSION" --notes-file "$RELEASE_NOTE" $PRERELEASE_FLAG
else
  gh release create "$VERSION" --title "$VERSION" --notes-file "$RELEASE_NOTE" $PRERELEASE_FLAG
fi

gh release upload "$VERSION" ./build/distributions/*.zip --clobber
