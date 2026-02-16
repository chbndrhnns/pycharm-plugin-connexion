# Fixture Dependency Checks for Moving Pytest Tests

## Context

When a pytest test function is moved into or between classes, fixture resolution can change
due to pytest's precedence rules (class scope overrides module/import/conftest fixtures).
We currently do not validate these changes. This can silently change test behavior or break
fixtures entirely.

This document proposes a minimal, reusable analysis to detect fixture dependency changes
when moving tests.

## Precedence refresher

Pytest resolves fixtures in this order (closest first):

1. Class scope (test class + ancestors)
2. Module scope (same file)
3. Imports (explicit + wildcard)
4. conftest.py (walking up)
5. Plugin fixtures

We already implement this order in `PytestFixtureResolver.findFixtureChain(...)`.

## Risks when moving a test

- **Shadowing**: target class defines a fixture with the same name as the source
  resolution chain; the effective fixture implementation changes.
- **Missing fixtures**: a fixture resolved from the original class is no longer
  available in the target scope.
- **New class-level dependencies**: target class adds `@pytest.mark.usefixtures(...)`
  or has `@pytest.fixture(autouse=True)` methods; these become new dependencies.
- **Loss of class-level dependencies**: moving out of a class removes the above.

## Proposal: `PytestFixtureMoveAnalyzer`

Create a helper that compares fixture resolution between source and target scopes.

### Inputs

- `sourceFunction: PyFunction`
- `sourceClass: PyClass?`
- `targetClass: PyClass?` (null when wrapping into a new class)
- `file: PyFile`
- `context: TypeEvalContext`

### Step 1: Collect fixture names used by the test

Start minimal, then extend:

- Function parameters (exclude `self`, `cls`, `request`).
- `@pytest.mark.usefixtures(...)` on the function.
- (Phase 2) `@pytest.mark.usefixtures(...)` on the class.
- (Phase 2) module-level `pytestmark`.

### Step 2: Compare resolution head (source vs target)

For each fixture name:

- `sourceHead = PytestFixtureResolver.findFixtureChain(sourceElement, name, context).firstOrNull()?.fixtureFunction`
- `targetHead = PytestFixtureResolver.findFixtureChain(targetElement, name, context).firstOrNull()?.fixtureFunction`
    - Use an element inside the target class (e.g. `targetClass.nameIdentifier`) to include class scope.

Interpretation:

- `sourceHead != targetHead` -> **shadowed** (fixture implementation changes)
- `sourceHead != null && targetHead == null` -> **missing** (fixture likely broken)

### Step 3: Class-level dependency differences (Phase 2)

Compute and diff:

- `@pytest.mark.usefixtures(...)` on source vs target class.
- `@pytest.fixture(autouse=True)` methods on source vs target class.
  Report new and lost dependencies.

### Output

```
data class FixtureMoveReport(
    val shadowed: List<String>,
    val missing: List<String>,
    val newUsefixtures: List<String>,
    val removedUsefixtures: List<String>,
    val newAutouse: List<String>,
    val lostAutouse: List<String>
)
```

## UX integration

- **Move test to another class**: show a warning in the dialog when the target class is selected.
  Use `ValidationInfo(...).asWarning()` or add a warning panel listing issues.
- **Wrap test in new class**: should generally be empty, but still run for consistency
  (most issues are likely no-ops).

## Minimal viable implementation (Phase 1)

- Only analyze function parameters + function `usefixtures`.
- Only compare `sourceHead` vs `targetHead`.
- Only surface warnings for "Move test to another class".

## Notes

- This uses existing fixture infrastructure (`PytestFixtureResolver`, `PytestFixtureUtil`).
- Tests should not perform call-site updates; this is purely a validation/reporting step.
