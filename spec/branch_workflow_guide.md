# Branch Workflow Guide

## Branch Structure

```
main                          # Production releases (stable, published plugin)
├── release/parameter-object  # Feature branch (single feature ready for release)
└── release/other-feature     # Other feature branches...

junie                         # Development branch (all features, experimental work)
```

## Core Principles

1. **`main`** = Only released, stable code
2. **`release/*`** = Individual features ready for release (created from main)
3. **`junie`** = All development work happens here (features, experiments, WIP)

---

## Workflow Scenarios

### 1. **Bug Fix for Parameter Object Feature**

#### Scenario A: Bug found BEFORE release (feature still in `release/parameter-object`)

```bash
# Work happens on junie
git checkout junie
git pull origin junie

# Fix the bug in parameter object code
# ... make changes ...

git add .
git commit -m "fix: Handle edge case in parameter object inline"

# Recreate the release branch to include the fix
git branch -D release/parameter-object
git checkout -b release/parameter-object main

# Copy the updated feature from junie (same process as before)
git checkout junie -- src/main/kotlin/.../refactoring/parameterobject/
# ... repeat the package-based copy process ...

git commit -m "feat: Add introduce/inline parameter object refactoring"
```

**Why?** The release branch is temporary and disposable. It's just a snapshot of junie for releasing.

---

#### Scenario B: Bug found AFTER release (feature already in `main`)

**Option 1: Hotfix directly to main** (for critical bugs)
```bash
# Create hotfix branch from main
git checkout main
git pull origin main
git checkout -b hotfix/parameter-object-fix

# Fix the bug
# ... make changes ...

git add .
git commit -m "fix: Critical bug in parameter object inline"

# Merge to main
git checkout main
git merge hotfix/parameter-object-fix
git push origin main

# Also apply to junie to keep it in sync
git checkout junie
git cherry-pick <hotfix-commit-sha>
# or merge the hotfix branch
git push origin junie
```

**Option 2: Fix in junie, release new version** (for non-critical bugs)
```bash
# Fix on junie
git checkout junie
# ... make changes ...
git commit -m "fix: Handle edge case in parameter object"

# When ready to release fix, update release branch or create new release
git checkout release/parameter-object
git branch -D release/parameter-object
git checkout -b release/parameter-object main

# Re-copy from junie with the fix
git checkout junie -- src/main/kotlin/.../refactoring/parameterobject/
# ...
git commit -m "feat: Add parameter object refactoring (v1.1 with fixes)"
```

---

### 2. **New Feature for Parameter Object**

```bash
# All development on junie
git checkout junie

# Add new feature (e.g., support for attrs library)
# ... implement feature ...

git commit -m "feat: Add attrs support to parameter object"

# When ready to release, recreate release branch
git branch -D release/parameter-object
git checkout -b release/parameter-object main
git checkout junie -- src/main/kotlin/.../refactoring/parameterobject/
# ... etc ...
```

**Key Point:** Never develop on `release/*` branches directly. They're snapshots.

---

### 3. **Releasing a Completely New Feature**

```bash
# Feature developed on junie over time
git checkout junie
# ... lots of commits developing "feature-X" ...

# When feature-X is ready for release
git checkout main
git pull origin main
git checkout -b release/feature-x main

# Copy feature-X package from junie
git checkout junie -- src/main/kotlin/.../feature-x/
# Copy dependencies
# Update plugin.xml
# Test build

git commit -m "feat: Add feature-X"
git push -u origin release/feature-x

# Create PR: release/feature-x → main
gh pr create --base main --head release/feature-x
```

---

### 4. **Releasing Multiple Features Together**

```bash
# Create combined release branch
git checkout main
git checkout -b release/v2.0 main

# Copy multiple feature packages from junie
git checkout junie -- src/main/kotlin/.../feature-a/
git checkout junie -- src/main/kotlin/.../feature-b/
git checkout junie -- src/main/kotlin/.../feature-c/
# Update plugin.xml with all features
# Test

git commit -m "feat: Release v2.0 with features A, B, C"
git push -u origin release/v2.0
```

---

### 5. **Keeping Junie and Main in Sync**

After releasing a feature to `main`, you typically want to keep `junie` aligned:

```bash
# Option A: Merge main into junie (if you want clean main history)
git checkout junie
git merge main  # Brings released features back to junie
git push origin junie

# Option B: Do nothing (junie already has the features)
# Since junie is the source of truth, it already has everything
# Only merge if main has hotfixes that weren't in junie
```

**Recommendation:** Only merge `main → junie` if:
- Hotfixes were made directly to main
- You want to synchronize version numbers or metadata

Otherwise, `junie` already has everything and more.

---

## Decision Tree

### "Where do I make this change?"

```
Is it a critical bug in production?
├─ YES → Hotfix branch from main → merge to main → cherry-pick to junie
└─ NO  → Are you developing/experimenting?
    ├─ YES → Work on junie
    └─ NO  → Are you preparing a release?
        └─ YES → Create/update release/* branch from main + junie
```

### "When do I update release/* branches?"

```
Never update them directly!

Instead:
1. Make changes on junie
2. Delete old release/* branch
3. Recreate release/* from main
4. Copy updated package from junie
```

**Why?** Release branches are **disposable snapshots**, not development branches.

---

## Best Practices

### DO ✅

- **All development on `junie`** - features, experiments, fixes
- **Release branches are temporary** - recreate them when needed
- **`main` only gets releases** - through PRs from release/* branches
- **One commit per feature** in release branches (squashed history)
- **Keep release branches focused** - single feature or related features

### DON'T ❌

- ❌ Develop directly on release/* branches
- ❌ Merge junie directly into main (too messy)
- ❌ Cherry-pick individual commits from junie to release branches
- ❌ Try to maintain release/* branches long-term
- ❌ Have multiple versions of a feature branch (delete and recreate)

---

## Example Timeline

```
Week 1-4: Develop parameter object on junie
  junie: commit, commit, commit (58 commits total)

Week 5: Ready to release
  git checkout -b release/parameter-object main
  git checkout junie -- .../parameterobject/
  git commit "feat: Add parameter object"
  PR: release/parameter-object → main
  main: [merged, tagged v1.0.0, published]

Week 6: Bug found in production
  git checkout junie
  fix bug
  git commit "fix: parameter object bug"

  # Recreate release branch with fix
  git branch -D release/parameter-object
  git checkout -b release/parameter-object main
  git checkout junie -- .../parameterobject/
  git commit "feat: Add parameter object (v1.0.1)"
  PR: release/parameter-object → main
  main: [merged, tagged v1.0.1, published]

Week 8: New feature added
  git checkout junie
  # Add attrs support
  git commit "feat: Add attrs support to parameter object"

  # When ready for release v1.1.0
  git branch -D release/parameter-object
  git checkout -b release/parameter-object main
  git checkout junie -- .../parameterobject/
  git commit "feat: Parameter object v1.1.0 with attrs support"
  PR: release/parameter-object → main
```

---

## Advanced: Handling Shared Dependencies

If parameter object depends on infrastructure in other packages:

### Scenario: Parameter object needs new utils class

```bash
# On junie, develop both together
git checkout junie
# Add src/main/kotlin/.../utils/NewUtil.kt
# Update parameter object to use NewUtil
git commit "feat: Add NewUtil for parameter object"

# When releasing
git checkout -b release/parameter-object main
git checkout junie -- src/main/kotlin/.../parameterobject/
git checkout junie -- src/main/kotlin/.../utils/NewUtil.kt  # Copy dependency
# Update plugin.xml
git commit "feat: Add parameter object with new utils"
```

**Key:** Copy dependencies along with the feature.

---

## Summary

| Branch | Purpose | Lifespan | Changes |
|--------|---------|----------|---------|
| `main` | Production releases | Permanent | Only via PRs from release/* |
| `junie` | All development | Permanent | All development work |
| `release/*` | Feature snapshots for release | Temporary | None (recreate from scratch) |

**Mental Model:**
- `junie` = Your workshop (messy, iterative, all features)
- `release/*` = Gift wrapping station (clean, single purpose)
- `main` = Store shelf (only finished, tested products)
