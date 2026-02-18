#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

DRY_RUN="${DRY_RUN:-false}"

VERSION="${VERSION:-${GITHUB_REF_NAME:-}}"
if [[ -z "$VERSION" ]]; then
  echo "ERROR: VERSION or GITHUB_REF_NAME is required." >&2
  exit 1
fi

if [[ "$DRY_RUN" == "true" ]]; then
  echo "========================================="
  echo "  DRY RUN MODE - No publishing will occur"
  echo "========================================="
fi

CURRENT_VERSION="$(./scripts/version.sh current)"
if [[ "$CURRENT_VERSION" != "$VERSION" ]]; then
  echo "ERROR: Tag version '$VERSION' does not match gradle.properties pluginVersion '$CURRENT_VERSION'." >&2
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  git fetch origin main
  MAIN_SHA="$(git rev-parse origin/main)"
  TAG_SHA="$(git rev-parse HEAD)"

  if ! git merge-base --is-ancestor "$TAG_SHA" "$MAIN_SHA"; then
    echo "ERROR: Release tag must be based on main." >&2
    echo "Tag SHA: $TAG_SHA" >&2
    echo "Main SHA: $MAIN_SHA" >&2
    exit 1
  fi
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI is required in CI to create the GitHub release." >&2
  exit 1
fi

if [[ "${SKIP_TESTS:-}" != "true" ]]; then
  ./gradlew test
fi

./gradlew buildPlugin

# Generate changelog with git-cliff if available
if command -v git-cliff >/dev/null 2>&1; then
  echo "Generating changelog with git-cliff for version $VERSION..."
  git-cliff --tag "$VERSION" --unreleased -o CHANGELOG.md

  # Add back [Unreleased] section for next release
  # Insert after the header (after the CalVer format line)
  sed -i.bak '/^This project uses.*CalVer/a\
\
## [Unreleased]
' CHANGELOG.md
  rm -f CHANGELOG.md.bak

  echo "Changelog updated in CHANGELOG.md"
else
  echo "WARNING: git-cliff not found. Using existing CHANGELOG.md" >&2
fi

RELEASE_NOTE="./build/tmp/release_note.txt"
mkdir -p "$(dirname "$RELEASE_NOTE")"
./gradlew getChangelog --unreleased --no-header --quiet --console=plain --output-file="$RELEASE_NOTE"

if ! ls ./build/distributions/*.zip >/dev/null 2>&1; then
  echo "ERROR: No plugin zip found in ./build/distributions." >&2
  exit 1
fi

# Publish to JetBrains Marketplace (skip in dry-run mode)
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY RUN] Skipping JetBrains Marketplace publishing"
  echo "[DRY RUN] Would run: ./gradlew publishPlugin"
else
  echo "Publishing to JetBrains Marketplace..."
  ./gradlew publishPlugin
fi

PRERELEASE_FLAG=""
if [[ "$VERSION" == *-* ]]; then
  PRERELEASE_FLAG="--prerelease"
fi

# Create GitHub release (skip in dry-run mode)
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY RUN] Skipping GitHub release creation"
  echo "[DRY RUN] Would create/edit release: $VERSION"
  echo "[DRY RUN] Release notes preview:"
  cat "$RELEASE_NOTE"
else
  if gh release view "$VERSION" >/dev/null 2>&1; then
    gh release edit "$VERSION" --notes-file "$RELEASE_NOTE" $PRERELEASE_FLAG
  else
    gh release create "$VERSION" --title "$VERSION" --notes-file "$RELEASE_NOTE" $PRERELEASE_FLAG
  fi

  gh release upload "$VERSION" ./build/distributions/*.zip --clobber
fi
