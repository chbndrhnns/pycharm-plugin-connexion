### Goal
Add a Python inspection that reports assignments to *constants* (names matching `ALL_CAPS_IDENTIFIERS`, i.e. “looks like a constant”) whose declared type is **not** wrapped in `Final[...]`. Provide a quick-fix that:

1) If an explicit annotation exists (`FOO: T = ...`), rewrites it to `FOO: Final[T] = ...`.
2) Adds the required import for `Final` (prefer `from typing import Final`).
3) If type is unknown / ambiguous, fall back to `Any`.

### Suggested UX / scope
- **Where it triggers:** top-level module assignments and class attribute assignments.
- **What is a constant:** by name (regex-like check). Recommended definition:
  - starts with an ASCII uppercase letter
  - contains only `A–Z`, digits, and `_`
  - has at least one letter (to avoid flagging `___` or `123`)
  - examples flagged: `FOO`, `FOO_BAR`, `HTTP2`, `X_1`
  - examples ignored: `Foo`, `foo`, `_FOO` (optional: decide whether `_FOO` counts; see edge cases)
- **Severity:** weak warning (or warning) consistent with other project inspections.
- **Quick-fix family name:** e.g. `Wrap constant type in Final`.

### Implementation proposal
#### 1) New inspection class
Create `PyConstantShouldBeFinalInspection : PyInspection()` in `src/main/kotlin/.../inspections/` following the pattern of existing inspections (see `PyPrivateModuleImportInspection`).

**Visitor strategy**
- In `buildVisitor(...)`, early-return empty visitor when:
  - Python version guard fails (if you use it elsewhere; optional here since `typing.Final` exists for Py3.8+ and the project’s tests run at Py3.11).
  - settings toggle disabled (add a flag in `PluginSettingsState`).
- Visit assignment-like statements:
  - `PyAssignmentStatement` (plain `FOO = expr`)
  - `PyAnnotatedAssignmentStatement` (PEP 526: `FOO: T = expr`)
  - optionally: `# type: ...` comments on `PyAssignmentStatement` if you want to support them.

**Target extraction**
Handle only “simple name targets”:
- `FOO = ...` where target is `PyTargetExpression` with a simple `name`.
- Ignore destructuring/unpacking assignments:
  - `FOO, BAR = ...`
  - `FOO: int; FOO = ...` (separate statements; see edge cases)
- Ignore attribute targets:
  - `obj.FOO = ...`
- For multiple targets in one statement (`FOO = BAR = 1`):
  - Either register a problem for each constant target expression, or anchor on the statement and fix all constant targets at once. (I’d recommend **per-target** problems to reduce surprising edits.)

**Detection: “already Final”**
For a candidate name `FOO`:
- If there is an explicit annotation `T`:
  - If it is already `Final[...]` or `Final` (unsubscripted), do not report.
  - If it is `typing.Final[...]` / `typing_extensions.Final[...]`, also do not report.
- If there is no annotation:
  - Report only if you can infer a meaningful type (see inference rules below), otherwise either:
    - still report but use `Final[Any]` in quick-fix, or
    - do not offer quick-fix (my recommendation: **offer** and default to `Any` only when inference is unknown; it’s better than doing nothing, but see edge cases).

#### 2) Quick-fix implementation
Create `WrapConstantInFinalQuickFix` (likely `LocalQuickFix`) that:

1) Locates the specific assignment/target element (store a `SmartPsiElementPointer` to the `PyTargetExpression` or statement).
2) Computes the `T` to wrap: extract annotation text `T`.
3) Performs PSI rewrite: `FOO: T = expr` → `FOO: Final[T] = expr`
   Ensures import:
   - Add `from typing import Final` if not present.
   - If `Final` already imported from `typing_extensions`, reuse it (don’t add a second import).
   - If `typing` is imported as module (`import typing`), you can either:
     - still add `from typing import Final` (simplest), or
     - use `typing.Final[...]` (more “style-preserving” but more logic).
   - Project recommendation: start simple—add `from typing import Final`.
4) Reformat changed nodes (`CodeStyleManager.reformat`) and shorten references / optimize imports as needed.

**Type inference approach**
Use PyCharm’s type evaluation infrastructure:
- Evaluate type from the assigned value expression using a `TypeEvalContext`.
- Convert resulting `PyType` to a presentable annotation string.


**Special case: `None` initializer**
If `FOO = None` (no annotation), a naive inference is `None`, which is not very useful. 
Use `Final[None]`.

#### 3) Settings / registration
- Add a toggle in `PluginSettingsState` similar to existing ones (e.g. `enableConstantFinalInspection: Boolean = true`).
- Register inspection in plugin descriptor as done for other inspections.
- Add `inspectionDescriptions/<InspectionShortName>.html`.

### Edge cases to explicitly decide
1) **Leading underscore constants**: `_FOO = 1`
   - Many codebases treat these as “private constants.” Include.

2) **Multiple targets**: `FOO = BAR = 1`
   - Ignore.

3) **Tuple unpacking**: `FOO, BAR = (1, 2)`
   - Ignore.

4) **Existing `Final` but missing type arg**: `FOO: Final = 1`
   - Not compliant with “wrap inferred type” requirement.
   - should upgrade to `Final[int]`.

5) **Already `Final[...]` but wrong `T`**
   - Do nothing; inspection should not become a type-correctness checker.

6) **Type comments**: `FOO = 1  # type: int`
   - Use inferred type

7) **Stub files (`.pyi`)**
   - In stubs, constants are often declared without assignment:
     - `FOO: int`
   - You can support rewriting to `FOO: Final[int]`.

8) **Reassignment later**
   - It's constant. Ignore

9) **Generated / library files**
   - Follow the same “user source only” checks as other inspections (via `ProjectRootManager` file index).

### Test plan
Add a new test class under `src/test/kotlin/.../inspections/`, extending `fixtures.TestBase`, and use `fixtures.InspectionHelpers.doInspectionTest` with test data in `src/test/testData/inspections/<InspectionName>/...`.

#### Core test cases (single-file)
1) **Unannotated constant literal**
   - Before: `FOO = 1`
   - After: `from typing import Final\n\nFOO: Final[int] = 1`

2) **Already annotated**
   - Before: `FOO: int = 1`
   - After: `from typing import Final\n\nFOO: Final[int] = 1`

3) **Already Final**
   - Input: `from typing import Final\nFOO: Final[int] = 1`
   - Expect: no highlight / no quick-fix.

4) **Existing import from typing_extensions**
   - Input: `from typing_extensions import Final\nFOO: int = 1`
   - After: keeps `typing_extensions` import and uses it (no extra `from typing import Final`).

5) **Unknown type falls back to Any**
   - Input: `FOO = some_unknown()`
   - After: `FOO: Final[Any] = some_unknown()` and import `Any` + `Final` (either `from typing import Final, Any` or separate imports).

6) **`None` initializer**
   - Input: `FOO = None`
   - After: `FOO: Final[None] = None` + `Final` import.

#### Negative / skip tests
8) **Not ALL_CAPS**
   - `Foo = 1` / `foo = 1` should not be reported.

9) **Tuple unpacking skipped**
   - `FOO, BAR = (1, 2)` should not offer fix.

10) **Multiple targets skipped**
   - `FOO = BAR = 1` should not offer fix (or only offer on a single-target variant depending on your decision).

11) **Reassigned constant skipped** (if you implement the “no reassignment” guard)
   - `FOO = 1\nFOO = 2` should not be reported.

#### Settings toggle tests (optional but consistent with repo)
- Similar to existing `...SettingsToggleTest`:
  - when disabled, no highlighting.
  - when enabled, highlighting present.

### Practical notes for implementation in this repo
- Follow the existing inspection patterns:
  - file-index “user code only” guard (see `PyPrivateModuleImportInspection`).
  - settings flags via `PluginSettingsState.instance().state`.
- PSI changes in quick-fix must be done in `WriteCommandAction.runWriteCommandAction(project)`, and reformat via `CodeStyleManager`.
- Test base already configures Python helpers and a mock SDK; keep tests minimal and focused.

