### Goal
Add a Python inspection that reports assignments to *constants* (names matching `ALL_CAPS_IDENTIFIERS`, i.e. “looks like a constant”) whose declared type is **not** wrapped in `Final[...]`. Provide a quick-fix that:

1) If an explicit annotation exists (`FOO: T = ...`), rewrites it to `FOO: Final[T] = ...`.
2) If no annotation exists (`FOO = ...`), infers a type `T` and rewrites to `FOO: Final[T] = ...`.
3) Adds the required import for `Final` (prefer `from typing import Final`).

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
2) Computes the `T` to wrap:
   - If annotated assignment: extract annotation text `T`.
   - Else infer type from the initializer expression.
3) Performs PSI rewrite:
   - If annotated: `FOO: T = expr` → `FOO: Final[T] = expr`
   - If unannotated: `FOO = expr` → `FOO: Final[T] = expr`
4) Ensures import:
   - Add `from typing import Final` if not present.
   - If `Final` already imported from `typing_extensions`, reuse it (don’t add a second import).
   - If `typing` is imported as module (`import typing`), you can either:
     - still add `from typing import Final` (simplest), or
     - use `typing.Final[...]` (more “style-preserving” but more logic).
   - Project recommendation: start simple—add `from typing import Final`.
5) Reformat changed nodes (`CodeStyleManager.reformat`) and shorten references / optimize imports as needed.

**Type inference approach**
Use PyCharm’s type evaluation infrastructure:
- Evaluate type from the assigned value expression using a `TypeEvalContext`.
- Convert resulting `PyType` to a presentable annotation string.

Heuristics for reliable inference:
- Literals:
  - `1` → `int`, `"x"` → `str`, `b"x"` → `bytes`, `True` → `bool`, `None` → `None` (but see below)
- Collection literals:
  - `[]` / `{}` / `set()` often becomes `list[Any]` / `dict[Any, Any]` / `set[Any]` unless context exists.
- Constructor calls:
  - `Foo()` → `Foo` (when resolvable)
- If type is unknown / ambiguous, fall back to `Any`.

**Special case: `None` initializer**
If `FOO = None` (no annotation), a naive inference is `None`, which is not very useful. Two options:
- Prefer `Final[None]` (strictly correct for that initializer).
- Or if there is later evidence (not trivial), prefer `Final[T | None]`.
Recommendation: keep it simple: use `Final[None]`.

#### 3) Settings / registration
- Add a toggle in `PluginSettingsState` similar to existing ones (e.g. `enableConstantFinalInspection: Boolean = true`).
- Register inspection in plugin descriptor as done for other inspections.
- Add `inspectionDescriptions/<InspectionShortName>.html`.

### Edge cases to explicitly decide
1) **Leading underscore constants**: `_FOO = 1`
   - Many codebases treat these as “private constants.” Decide whether to include.
   - Recommendation: include them only if you explicitly want “module-level constants,” otherwise exclude to avoid noise.

2) **Multiple targets**: `FOO = BAR = 1`
   - Apply fix per target or all at once.
   - Recommendation: per target problem, but fix can safely transform the whole statement only if you rewrite it into multiple annotated assignments (messy). Best: *only* offer fix when the statement has a single target.

3) **Tuple unpacking**: `FOO, BAR = (1, 2)`
   - Generally skip: hard to rewrite cleanly to multiple annotated assignments without changing formatting/semantics.

4) **Existing `Final` but missing type arg**: `FOO: Final = 1`
   - Not compliant with “wrap inferred type” requirement.
   - Decide whether quick-fix should upgrade to `Final[int]`.
   - Recommendation: yes—treat unsubscripted `Final` as needing an arg when initializer is present.

5) **Already `Final[...]` but wrong `T`**
   - Do nothing; inspection should not become a type-correctness checker.

6) **Type comments**: `FOO = 1  # type: int`
   - Supporting this is valuable because it’s common in older code.
   - If supported: treat it like an annotation and wrap the comment type.

7) **Stub files (`.pyi`)**
   - In stubs, constants are often declared without assignment:
     - `FOO: int`
   - You can support rewriting to `FOO: Final[int]`.

8) **Reassignment later**
   - If `FOO` is reassigned, marking it `Final` may be wrong.
   - Minimal approach: ignore control-flow and still suggest.
   - Better approach (recommended): before reporting, search for additional assignments to the same target in the same scope and skip if found.

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

7) **Class attribute constant**
   - Input:
     ```python
     class C:
         FOO = 1
     ```
   - After:
     ```python
     from typing import Final

     class C:
         FOO: Final[int] = 1
     ```

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

### Open questions (pick defaults if you want the quickest path)
1) Should `_FOO` be considered a constant? (Default suggestion: **no**, to reduce noise.)
2) Should `FOO = BAR = 1` be supported? (Default: **skip**.)
3) Do you want to support `# type:` comments? (Default: **yes**, if it’s not too much work; otherwise defer.)

If you confirm these 3, the inspection behavior and tests can be nailed down to a deterministic spec.