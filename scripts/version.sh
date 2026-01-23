#!/usr/bin/env bash
set -euo pipefail

# Version management script for CalVer (YYYY.MM.PATCH) versioning
# Supports: stable, bugfix, dev, beta, alpha, rc channels

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_PROPERTIES="$PROJECT_DIR/gradle.properties"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $0 <command> [options]

Commands:
    current                     Show current version
    next [--channel=CHANNEL]    Calculate next version
    bump [--channel=CHANNEL]    Bump version and update gradle.properties

Channels:
    stable                      New monthly release (YYYY.MM.0)
    bugfix                      Increment patch (YYYY.MM.X+1)
    dev                         Development build (YYYY.MM.0-dev.YYYYMMDD-HHMMSS)
    beta                        Beta release (YYYY.MM.0-beta.N)
    alpha                       Alpha release (YYYY.MM.0-alpha.N)
    rc                          Release candidate (YYYY.MM.0-rc.N)

Examples:
    $0 current
    $0 next --channel=stable
    $0 next --channel=bugfix
    $0 next --channel=dev
    $0 next --channel=beta
    $0 bump --channel=stable
    $0 bump --channel=dev

EOF
    exit 1
}

log_info() {
    echo -e "${BLUE}INFO:${NC} $1"
}

log_success() {
    echo -e "${GREEN}SUCCESS:${NC} $1"
}

log_error() {
    echo -e "${RED}ERROR:${NC} $1" >&2
}

log_warning() {
    echo -e "${YELLOW}WARNING:${NC} $1"
}

get_current_version() {
    grep "^pluginVersion" "$GRADLE_PROPERTIES" | cut -d'=' -f2 | tr -d ' '
}

parse_version() {
    local version="$1"
    local base_version="${version%%-*}"  # Remove channel suffix if exists

    # Parse YYYY.MM.PATCH
    YEAR=$(echo "$base_version" | cut -d'.' -f1)
    MONTH=$(echo "$base_version" | cut -d'.' -f2)
    PATCH=$(echo "$base_version" | cut -d'.' -f3)

    # Parse channel if exists
    if [[ "$version" == *"-"* ]]; then
        CHANNEL_PART="${version#*-}"
        CHANNEL=$(echo "$CHANNEL_PART" | cut -d'.' -f1)
        BUILD_NUM=$(echo "$CHANNEL_PART" | cut -d'.' -f2 2>/dev/null || echo "")
    else
        CHANNEL=""
        BUILD_NUM=""
    fi
}

get_next_version() {
    local channel="${1:-stable}"
    local current_version
    current_version=$(get_current_version)

    parse_version "$current_version"

    local current_year current_month
    current_year=$(date +%Y)
    current_month=$(date +%m | sed 's/^0//')  # Remove leading zero

    local next_version

    case "$channel" in
        stable)
            # New monthly release
            next_version="${current_year}.${current_month}.0"
            ;;
        bugfix)
            # Increment patch version, keep same year/month
            local next_patch=$((PATCH + 1))
            next_version="${YEAR}.${MONTH}.${next_patch}"
            ;;
        dev)
            # Dev build with timestamp
            local timestamp
            timestamp=$(date +%Y%m%d-%H%M%S)
            next_version="${current_year}.${current_month}.0-dev.${timestamp}"
            ;;
        beta|alpha|rc)
            # Increment channel build number
            if [[ "$CHANNEL" == "$channel" && -n "$BUILD_NUM" ]]; then
                # Increment existing channel build
                local next_build=$((BUILD_NUM + 1))
                next_version="${current_year}.${current_month}.0-${channel}.${next_build}"
            else
                # Start new channel at .1
                next_version="${current_year}.${current_month}.0-${channel}.1"
            fi
            ;;
        *)
            log_error "Unknown channel: $channel"
            echo "Valid channels: stable, bugfix, dev, beta, alpha, rc"
            exit 1
            ;;
    esac

    echo "$next_version"
}

update_gradle_properties() {
    local new_version="$1"
    local temp_file="${GRADLE_PROPERTIES}.tmp"

    # Create backup
    cp "$GRADLE_PROPERTIES" "${GRADLE_PROPERTIES}.bak"

    # Update version
    sed "s/^pluginVersion[[:space:]]*=.*/pluginVersion = ${new_version}/" "$GRADLE_PROPERTIES" > "$temp_file"
    mv "$temp_file" "$GRADLE_PROPERTIES"

    log_success "Updated gradle.properties"
    log_info "Backup saved to: ${GRADLE_PROPERTIES}.bak"
}

cmd_current() {
    local current_version
    current_version=$(get_current_version)
    echo "$current_version"
}

cmd_next() {
    local channel="${1:-stable}"
    local next_version
    next_version=$(get_next_version "$channel")
    echo "$next_version"
}

cmd_bump() {
    local channel="${1:-stable}"
    local current_version next_version

    current_version=$(get_current_version)
    next_version=$(get_next_version "$channel")

    log_info "Current version: $current_version"
    log_info "Next version: $next_version"

    # Update gradle.properties
    update_gradle_properties "$next_version"

    log_success "Version bumped to: $next_version"
    echo "$next_version"
}

# Parse arguments
COMMAND="${1:-}"
CHANNEL="stable"

if [[ $# -eq 0 ]]; then
    usage
fi

shift || true

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
        -h|--help)
            usage
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

# Execute command
case "$COMMAND" in
    current)
        cmd_current
        ;;
    next)
        cmd_next "$CHANNEL"
        ;;
    bump)
        cmd_bump "$CHANNEL"
        ;;
    *)
        log_error "Unknown command: $COMMAND"
        usage
        ;;
esac
