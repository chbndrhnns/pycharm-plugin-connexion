# Versioning Strategy

## Overview

BetterPy uses **CalVer** (Calendar Versioning) with the format: `YYYY.MM.PATCH[-channel.build]` (MM is zero-padded)

This versioning scheme provides:
- Clear timeline visibility for releases
- Natural alignment with monthly release cadence
- Support for multiple release channels (stable, beta, dev)
- Simple bugfix tracking

## Version Format

### Base Format: `YYYY.MM.PATCH`

- **YYYY**: 4-digit year
- **MM**: Month without leading zero (1-12)
- **PATCH**: Bugfix/patch number (starts at 0)

### Channel Suffix: `-channel.build`

Optional suffix for pre-release and development builds:
- **channel**: Release channel name (dev, beta, alpha, rc)
- **build**: Build number or timestamp

## Release Types

### Stable Releases
Monthly production releases following calendar months.

**Format:** `YYYY.MM.0`

**Examples:**
- `2026.01.0` - January 2026 release
- `2026.02.0` - February 2026 release
- `2026.12.0` - December 2026 release

**When to use:**
- Regular monthly feature releases
- Production-ready code
- Published to `default` channel in JetBrains Marketplace

### Bugfix Releases
Patch releases for urgent fixes within a monthly cycle.

**Format:** `YYYY.MM.PATCH` (where PATCH > 0)

**Examples:**
- `2026.01.1` - First bugfix for January 2026
- `2026.01.2` - Second bugfix for January 2026

**When to use:**
- Critical bug fixes
- Security patches
- Hot fixes between monthly releases

### Development Builds
Unstable development snapshots with timestamps.

**Format:** `YYYY.MM.0-dev.YYYYMMDD-HHMMSS`

**Examples:**
- `2026.01.0-dev.20260115-143000`
- `2026.02.0-dev.20260203-091500`

**When to use:**
- Automated CI builds
- Feature branch testing
- Internal development testing
- Published to `dev` channel

### Beta Releases
Pre-release versions for wider testing before stable release.

**Format:** `YYYY.MM.0-beta.N`

**Examples:**
- `2026.01.0-beta.1` - First beta
- `2026.01.0-beta.2` - Second beta

**When to use:**
- Feature complete but needs testing
- User acceptance testing
- Published to `beta` channel

### Alpha Releases
Early pre-release versions for initial testing.

**Format:** `YYYY.MM.0-alpha.N`

**Examples:**
- `2026.01.0-alpha.1`
- `2026.01.0-alpha.2`

**When to use:**
- Early feature testing
- Breaking changes testing
- Published to `alpha` channel

### Release Candidates
Final testing before stable release.

**Format:** `YYYY.MM.0-rc.N`

**Examples:**
- `2026.01.0-rc.1`
- `2026.01.0-rc.2`

**When to use:**
- Final testing phase
- No new features, only bug fixes
- Published to `rc` channel

## IntelliJ Marketplace Channels

The build system automatically routes versions to appropriate channels:

| Version Pattern | Channel | Visibility |
|----------------|---------|------------|
| `2026.01.0` | `default` | All users |
| `2026.01.1` | `default` | All users |
| `2026.01.0-beta.X` | `beta` | Beta testers |
| `2026.01.0-alpha.X` | `alpha` | Alpha testers |
| `2026.01.0-rc.X` | `rc` | RC testers |
| `2026.01.0-dev.X` | `dev` | Developers only |

## Version Management Script

Use `scripts/version.sh` for automated version management:

### Show Current Version
```bash
./scripts/version.sh current
```

### Preview Next Version
```bash
# Next stable monthly release
./scripts/version.sh next --channel=stable

# Next bugfix
./scripts/version.sh next --channel=bugfix

# Next dev build
./scripts/version.sh next --channel=dev

# Next beta
./scripts/version.sh next --channel=beta
```

### Bump Version
```bash
# Bump to next stable release
./scripts/version.sh bump --channel=stable

# Bump to next bugfix
./scripts/version.sh bump --channel=bugfix

# Create dev build
./scripts/version.sh bump --channel=dev

# Create beta release
./scripts/version.sh bump --channel=beta
```

The script automatically:
- Updates `gradle.properties`
- Creates a backup (`.bak` file)
- Uses current date for stable/dev versions
- Increments build numbers for beta/alpha/rc

## Workflow Examples

### Monthly Stable Release
```bash
# 1. Prepare release
./scripts/version.sh bump --channel=stable
# Output: 2026.01.0

# 2. Update CHANGELOG.md with release notes

# 3. Commit and tag
git add gradle.properties CHANGELOG.md
git commit -m "Release 2026.01.0"
git tag 2026.01.0
git push origin main --tags
```

### Bugfix Release
```bash
# 1. Create bugfix
./scripts/version.sh bump --channel=bugfix
# Output: 2026.01.1

# 2. Update CHANGELOG.md

# 3. Commit and tag
git commit -am "Release 2026.01.1 - Fix critical bug"
git tag 2026.01.1
git push origin main --tags
```

### Beta Release Cycle
```bash
# 1. Create first beta
./scripts/version.sh bump --channel=beta
# Output: 2026.01.0-beta.1

# 2. Test and fix issues

# 3. Create second beta
./scripts/version.sh bump --channel=beta
# Output: 2026.01.0-beta.2

# 4. When ready, bump to stable
./scripts/version.sh bump --channel=stable
# Output: 2026.01.0
```

### Development Build (CI)
```bash
# Automated in CI pipeline
./scripts/version.sh bump --channel=dev
# Output: 2026.01.0-dev.20260123-143000
```

## Git Tags

Tag format matches version format:
- Stable: `2026.01.0`
- Bugfix: `2026.01.1`
- Beta: `2026.01.0-beta.1`
- Dev: Not typically tagged (ephemeral)

## Migration from SemVer

If migrating from SemVer (e.g., `0.0.1`):

1. Choose appropriate CalVer version based on current date
2. Update `gradle.properties`
3. Update `CHANGELOG.md` with new section
4. Tag the transition commit

Example:
```bash
# Old: 0.0.1
# New: 2026.01.0 (January 2026)
./scripts/version.sh bump --channel=stable
```

## Best Practices

1. **Monthly Cadence**: Aim for stable releases at the start of each month
2. **Bugfix Windows**: Reserve patches for critical fixes only
3. **Beta Testing**: Use beta channel for 1-2 weeks before stable
4. **Dev Builds**: Automate in CI for every commit to main
5. **Changelog**: Always update CHANGELOG.md before tagging
6. **Testing**: Test beta/rc thoroughly before promoting to stable
7. **Communication**: Announce releases with clear channel information

## FAQ

**Q: What if I need to release twice in one month?**
A: Use patch versions (e.g., `2026.01.0`, then `2026.01.1` if needed)

**Q: Can I skip months?**
A: Yes! If no release in February, go directly to `2026.03.0` in March

**Q: How do I handle multiple features in development?**
A: Use dev builds (`-dev.timestamp`) or feature branches with beta releases

**Q: What about breaking changes?**
A: Document in CHANGELOG.md and consider using beta cycle for user feedback

**Q: Can I use week numbers instead of months?**
A: Yes, modify the script to use `YYYY.WW.PATCH` format (week of year)
