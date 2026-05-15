#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

CHANNEL="stable"
ALLOW_DIRTY="no"
SKIP_TESTS="no"
SKIP_PUBLISH="no"
SKIP_GH="no"
SKIP_TAG="no"
SKIP_COMMIT="no"
PUSH="no"

usage() {
  cat <<'USAGE'
Usage: scripts/release-local.sh [options]

Options:
  --channel=CHANNEL    Release channel: stable, bugfix, beta, alpha, rc
  --allow-dirty        Allow running with uncommitted changes
  --skip-tests         Skip running ./gradlew test
  --skip-publish       Skip ./gradlew publishPlugin
  --skip-gh            Skip GitHub release creation/upload
  --skip-tag           Skip creating git tag
  --skip-commit        Skip committing version bump
  --push               Push commit and tag to origin
  -h, --help           Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --channel=*)
      CHANNEL="${1#*=}"
      shift
      ;;
    --channel)
      CHANNEL="$2"
      shift 2
      ;;
    --allow-dirty)
      ALLOW_DIRTY="yes"
      shift
      ;;
    --skip-tests)
      SKIP_TESTS="yes"
      shift
      ;;
    --skip-publish)
      SKIP_PUBLISH="yes"
      shift
      ;;
    --skip-gh)
      SKIP_GH="yes"
      shift
      ;;
    --skip-tag)
      SKIP_TAG="yes"
      shift
      ;;
    --skip-commit)
      SKIP_COMMIT="yes"
      shift
      ;;
    --push)
      PUSH="yes"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$ALLOW_DIRTY" != "yes" ]] && [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: Working tree is dirty. Commit or use --allow-dirty." >&2
  exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "ERROR: All releases must be run from the main branch. Current branch: $CURRENT_BRANCH" >&2
  exit 1
fi

VERSION=$(./scripts/version.sh bump --channel="$CHANNEL" | tail -n 1)

if [[ "$SKIP_COMMIT" != "yes" ]]; then
  git add gradle.properties
  git commit -m "chore: Bump version to $VERSION" || echo "No version change to commit."
fi

if [[ "$SKIP_TESTS" != "yes" ]]; then
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

if [[ "$SKIP_COMMIT" != "yes" ]]; then
  git add CHANGELOG.md
  git commit -m "docs: Update CHANGELOG.md for $VERSION" || echo "No changelog changes to commit."
fi

RELEASE_NOTE="./build/tmp/release_note.txt"
mkdir -p "$(dirname "$RELEASE_NOTE")"
extract_version_notes CHANGELOG.md "$VERSION" "$RELEASE_NOTE"

# Allow interactive editing of release notes
if [[ -t 0 ]]; then
  echo ""
  echo "========================================="
  echo "  Release Notes Preview"
  echo "========================================="
  cat "$RELEASE_NOTE"
  echo ""
  echo "========================================="
  echo ""
  read -p "Edit release notes before continuing? (y/N) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    ${EDITOR:-vi} "$RELEASE_NOTE"
    echo "Release notes updated."
  fi
fi

if [[ "$SKIP_PUBLISH" != "yes" ]]; then
  if [[ -n "${PUBLISH_TOKEN:-}" ]]; then
    ./gradlew publishPlugin
  else
    echo "PUBLISH_TOKEN not set, skipping publishPlugin."
  fi
fi

if ! ls ./build/distributions/*.zip >/dev/null 2>&1; then
  echo "ERROR: No plugin zip found in ./build/distributions." >&2
  exit 1
fi

if [[ "$SKIP_TAG" != "yes" ]]; then
  if git rev-parse "$VERSION" >/dev/null 2>&1; then
    echo "Tag $VERSION already exists, skipping tag creation."
  else
    git tag -a "$VERSION" -m "Release $VERSION"
  fi
fi

if [[ "$SKIP_GH" != "yes" ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI not found, skipping GitHub release."
  else
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
  fi
fi

if [[ "$PUSH" == "yes" ]]; then
  git push origin HEAD
  if [[ "$SKIP_TAG" != "yes" ]]; then
    git push origin "$VERSION"
  fi
fi

echo "Release $VERSION completed locally."
