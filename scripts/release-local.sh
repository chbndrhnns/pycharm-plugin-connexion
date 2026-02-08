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

./gradlew buildPlugin

# Generate changelog with git-cliff if available
if command -v git-cliff >/dev/null 2>&1; then
  echo "Generating changelog with git-cliff for version $VERSION..."
  git-cliff --tag "$VERSION" --unreleased -o CHANGELOG.md
  echo "Changelog updated in CHANGELOG.md"

  if [[ "$SKIP_COMMIT" != "yes" ]]; then
    git add CHANGELOG.md
    git commit -m "docs: Update CHANGELOG.md for $VERSION" || echo "No changelog changes to commit."
  fi
else
  echo "WARNING: git-cliff not found. Using existing CHANGELOG.md" >&2
fi

RELEASE_NOTE="./build/tmp/release_note.txt"
mkdir -p "$(dirname "$RELEASE_NOTE")"
./gradlew getChangelog --unreleased --no-header --quiet --console=plain --output-file="$RELEASE_NOTE"

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
