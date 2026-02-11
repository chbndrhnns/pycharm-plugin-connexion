#!/usr/bin/env bash
#
# Synchronize a release from one GitHub repo to another.
# Creates the tag on the target repo's latest commit, copies release notes and artifacts.
#
# Usage: ./sync-release.sh <tag>
#
# Required environment variables:
#   SOURCE_REPO      - Source repository (e.g., "owner/repo")
#   TARGET_REPO      - Target repository (e.g., "owner/repo")
#   GH_TOKEN         - GitHub token with repo access to both repositories
#
# Optional environment variables:
#   TARGET_BRANCH    - Branch to tag in target repo (default: main)
#

set -euo pipefail

TAG="${1:-}"

if [[ -z "$TAG" ]]; then
    echo "Usage: $0 <tag>"
    echo "Example: $0 v1.2.3"
    exit 1
fi

: "${SOURCE_REPO:?SOURCE_REPO environment variable is required}"
: "${TARGET_REPO:?TARGET_REPO environment variable is required}"
: "${GH_TOKEN:?GH_TOKEN environment variable is required}"

TARGET_BRANCH="${TARGET_BRANCH:-main}"

echo "==> Syncing release $TAG from $SOURCE_REPO to $TARGET_REPO"

# Configure GitHub CLI authentication
export GITHUB_TOKEN="$GH_TOKEN"

# Fetch release info from source repo
echo "==> Fetching release info for $TAG from $SOURCE_REPO..."
RELEASE_JSON=$(gh api "repos/$SOURCE_REPO/releases/tags/$TAG" 2>/dev/null || true)

if [[ -z "$RELEASE_JSON" || "$RELEASE_JSON" == "null" ]]; then
    echo "Error: Release for tag $TAG not found in $SOURCE_REPO"
    exit 1
fi

RELEASE_NAME=$(echo "$RELEASE_JSON" | jq -r '.name // empty')
RELEASE_BODY=$(echo "$RELEASE_JSON" | jq -r '.body // empty')
IS_DRAFT=$(echo "$RELEASE_JSON" | jq -r '.draft')
IS_PRERELEASE=$(echo "$RELEASE_JSON" | jq -r '.prerelease')

echo "   Release name: $RELEASE_NAME"
echo "   Draft: $IS_DRAFT, Prerelease: $IS_PRERELEASE"

# Get latest commit SHA from target repo's branch
echo "==> Getting latest commit from $TARGET_REPO ($TARGET_BRANCH branch)..."
TARGET_SHA=$(gh api "repos/$TARGET_REPO/git/refs/heads/$TARGET_BRANCH" | jq -r '.object.sha')
echo "   Target commit: $TARGET_SHA"

# Check if tag already exists in target repo
EXISTING_TAG=$(gh api "repos/$TARGET_REPO/git/refs/tags/$TAG" 2>/dev/null || true)
if [[ -n "$EXISTING_TAG" && "$EXISTING_TAG" != "null" ]]; then
    echo "Warning: Tag $TAG already exists in $TARGET_REPO, skipping tag creation"
else
    # Create tag in target repo
    echo "==> Creating tag $TAG in $TARGET_REPO..."
    gh api "repos/$TARGET_REPO/git/refs" \
        -f ref="refs/tags/$TAG" \
        -f sha="$TARGET_SHA" > /dev/null
    echo "   Tag created successfully"
fi

# Check if release already exists in target repo
EXISTING_RELEASE=$(gh api "repos/$TARGET_REPO/releases/tags/$TAG" 2>/dev/null || true)
if [[ -n "$EXISTING_RELEASE" && "$EXISTING_RELEASE" != "null" ]]; then
    echo "Warning: Release for $TAG already exists in $TARGET_REPO, skipping release creation"
    TARGET_RELEASE_ID=$(echo "$EXISTING_RELEASE" | jq -r '.id')
else
    # Create release in target repo
    echo "==> Creating release in $TARGET_REPO..."
    RELEASE_FLAGS=""
    [[ "$IS_DRAFT" == "true" ]] && RELEASE_FLAGS="$RELEASE_FLAGS --draft"
    [[ "$IS_PRERELEASE" == "true" ]] && RELEASE_FLAGS="$RELEASE_FLAGS --prerelease"

    TARGET_RELEASE=$(gh api "repos/$TARGET_REPO/releases" \
        -f tag_name="$TAG" \
        -f name="$RELEASE_NAME" \
        -f body="$RELEASE_BODY" \
        -F draft="$IS_DRAFT" \
        -F prerelease="$IS_PRERELEASE")
    TARGET_RELEASE_ID=$(echo "$TARGET_RELEASE" | jq -r '.id')
    echo "   Release created with ID: $TARGET_RELEASE_ID"
fi

# Download and upload release assets
ASSETS=$(echo "$RELEASE_JSON" | jq -r '.assets[]?.browser_download_url // empty')

if [[ -n "$ASSETS" ]]; then
    echo "==> Copying release assets..."
    TEMP_DIR=$(mktemp -d)
    trap "rm -rf $TEMP_DIR" EXIT

    while IFS= read -r asset_url; do
        if [[ -n "$asset_url" ]]; then
            ASSET_NAME=$(basename "$asset_url")
            echo "   Downloading: $ASSET_NAME"
            curl -sL -o "$TEMP_DIR/$ASSET_NAME" "$asset_url"
            
            echo "   Uploading: $ASSET_NAME"
            gh release upload "$TAG" "$TEMP_DIR/$ASSET_NAME" --repo "$TARGET_REPO" --clobber 2>/dev/null || true
        fi
    done <<< "$ASSETS"
else
    echo "==> No assets to copy"
fi

echo "==> Release sync complete!"
echo "   Source: https://github.com/$SOURCE_REPO/releases/tag/$TAG"
echo "   Target: https://github.com/$TARGET_REPO/releases/tag/$TAG"
