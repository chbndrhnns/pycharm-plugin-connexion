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

RELEASE_BUILD=true ./gradlew buildPlugin

# ── Changelog helpers ──────────────────────────────────────────────

ensure_unreleased_section() {
  if ! grep -q '^## \[Unreleased\]' CHANGELOG.md; then
    # Insert after the header (after the CalVer format line)
    sed -i.bak '/^This project uses.*CalVer/a\
\
## [Unreleased]
' CHANGELOG.md
    rm -f CHANGELOG.md.bak
  fi
}

# Extract hand-authored content from the [Unreleased] section.
extract_handwritten_notes() {
  local changelog="$1" output="$2"
  : > "$output"
  if grep -q '^## \[Unreleased\]' "$changelog"; then
    awk '/^## \[Unreleased\]/{f=1;next} /^## \[/{f=0} f' "$changelog" > "$output"
  fi
}

# Remove the [Unreleased] section (heading + content) from the changelog.
strip_unreleased_section() {
  local changelog="$1"
  awk '/^## \[Unreleased\]/{s=1;next} /^## \[/{s=0} !s' "$changelog" > "${changelog}.tmp"
  mv "${changelog}.tmp" "$changelog"
}

# Insert hand-authored notes right after the version heading line.
inject_handwritten_notes() {
  local changelog="$1" notes_file="$2" version="$3"
  [[ -s "$notes_file" ]] || return 0
  local target="## [${version}]"
  {
    while IFS= read -r line || [[ -n "$line" ]]; do
      printf '%s\n' "$line"
      if [[ "$line" == "$target"* ]]; then
        cat "$notes_file"
      fi
    done < "$changelog"
  } > "${changelog}.tmp" && mv "${changelog}.tmp" "$changelog"
}

# Extract release notes for a specific version from CHANGELOG.md.
extract_version_notes() {
  local changelog="$1" version="$2" output="$3"
  awk -v ver="$version" '
    /^## \[/{if(index($0,"["ver"]")>0){f=1;next}else if(f){exit}}
    f
  ' "$changelog" > "$output"
}

# ── Generate changelog ─────────────────────────────────────────────

if ! command -v git-cliff >/dev/null 2>&1; then
  echo "ERROR: git-cliff not found. Install git-cliff to proceed." >&2
  exit 1
fi

echo "Generating changelog with git-cliff for version $VERSION..."

HAND_AUTHORED_FILE="./build/tmp/hand_authored_notes.md"
mkdir -p "$(dirname "$HAND_AUTHORED_FILE")"

# Preserve hand-authored notes from [Unreleased] before git-cliff regenerates
extract_handwritten_notes CHANGELOG.md "$HAND_AUTHORED_FILE"

# Remove [Unreleased] section so it doesn't interfere with --prepend
strip_unreleased_section CHANGELOG.md

# Generate new version entry from conventional commits and prepend to changelog
git-cliff --tag "$VERSION" --unreleased --prepend CHANGELOG.md

# Merge hand-authored notes into the new version section
inject_handwritten_notes CHANGELOG.md "$HAND_AUTHORED_FILE" "$VERSION"

# Add empty [Unreleased] section for the next development cycle
ensure_unreleased_section

echo "Changelog updated in CHANGELOG.md"

RELEASE_NOTE="./build/tmp/release_note.txt"
mkdir -p "$(dirname "$RELEASE_NOTE")"
extract_version_notes CHANGELOG.md "$VERSION" "$RELEASE_NOTE"

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
