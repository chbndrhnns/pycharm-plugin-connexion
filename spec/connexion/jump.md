### Goal (Connexion + OpenAPI navigation)
Add two-way navigation between OpenAPI spec operations and Connexion endpoint implementations:

1) **Spec → implementation**
- When editing an OpenAPI spec file, navigating from an operation to its Python implementation.
- Implementation reference is built from:
  - controller module: `x-openapi-router-controller` (Connexion v3) and also support legacy `x-swagger-router-controller` (Connexion/OpenAPI 2)
  - function name: `operationId`

2) **Code → spec declaration**
- From a Python function/method, jump to the corresponding endpoint declaration in the OpenAPI spec.

---

### Plan (MVP-first, then harden)

#### 0) Detect “OpenAPI spec file” reliably
Use a fast heuristic + optional PSI parsing:
- Eligible extensions: `yaml`, `yml`, `json`.
- Quick content test (cheap): file contains one of:
  - `openapi:` (OpenAPI 3.x)
  - `swagger:` (OpenAPI 2.0)
  - and also `paths:`
- If YAML/JSON PSI is available, prefer PSI-based checks:
  - top-level key `openapi` or `swagger`
  - top-level key `paths` exists and is a map/object

This gate ensures you don’t offer navigation actions in arbitrary YAML/JSON.

#### 1) Spec → implementation (provide Ctrl/Cmd+B and “Go to…”)
**Preferred UX for IntelliJ:** implement a `PsiReference` on the `operationId` scalar/string in the spec, so standard IDE navigation works.

**Where to attach the reference**
- YAML: scalar value node of `operationId:`
- JSON: string literal value of the `operationId` property

**How to compute the Python qualified name**
Define:
- `controllerKey = "x-openapi-router-controller"` (and fallback to `"x-swagger-router-controller"`)
- `operationIdValue = operation.operationId` (string)

Resolution rules (Connexion-oriented, tolerant):
1. If `operationIdValue` already looks fully-qualified, use it as-is:
   - contains `:` (e.g. `pkg.mod:func`) → module=`pkg.mod`, symbol=`func`
   - contains `.` and also matches patterns like `pkg.mod.func` or `pkg.mod.Class.method` → split at last `.` for symbol
2. Else, if controller is present, build `qualified = controller + "." + operationIdValue`.
3. Else: do not create a resolvable reference (or create one that resolves to empty and show a weak intention message).

**Controller lookup precedence** (common in specs; keep this explicit):
- operation-level `x-openapi-router-controller`
- path-item-level `x-openapi-router-controller`
- root-level `x-openapi-router-controller`
- (same precedence order for legacy `x-swagger-router-controller`)

**Python resolution strategy**
For MVP:
- Resolve by qualified name using Python PSI utilities (e.g. resolve module to `PyFile`, then find `PyFunction`/`PyClass` members).
- If runtime import roots differ from the IDE’s, fall back to project search:
  - find candidate `PyFunction`/`PyClass` by name and filter by containing module qualified name.

Return:
- single result → Ctrl/Cmd+B goes directly
- multiple results → return multiResolve targets so the IDE shows the chooser

**What counts as an “implementation”**
Support at least:
- top-level functions: `def get_pets(...):`
- class methods if operationId points to them (e.g. `pkg.api.PetsController.get_pets`)

#### 2) Code → spec declaration (from a Python symbol)
Provide a navigation entry point when caret is on:
- `PyFunction` name identifier
- `PyClass` method identifier

Two possible UX options:
- A `GotoDeclarationHandler` that adds OpenAPI spec targets as “declaration” targets.
- Or an intention/action like “Go to OpenAPI operation” (simpler to reason about in MVP).

**How to map Python symbol → operation(s)**
Compute candidate identifiers for the symbol:
- `qualifiedDot`: `pkg.module.func` or `pkg.module.Class.method`
- `qualifiedColon`: `pkg.module:func` or `pkg.module:Class.method` (some ecosystems use colon)
- `shortName`: `func` or `method` (only usable when spec uses controller)

Then search OpenAPI specs for matches:
- Iterate all detected OpenAPI files in project scope.
- For each spec, iterate `paths` → path → method operations.
- For each operation, compute its resolved target (same algorithm as in Spec → implementation):
  - if `operationId` fully-qualified: compare directly
  - else if controller exists: controller + "." + operationId

If resolved target matches this Python symbol (dot or colon normalized), add that operation as a navigation target.

**Performance strategy**
- MVP: scan on-demand (only when the user triggers the action). Keep it limited:
  - only `yaml/yml/json`
  - only files that pass the cheap “looks like OpenAPI” heuristic
- V2: add indexing (recommended once MVP works)
  - maintain a `FileBasedIndex` mapping `resolvedQualifiedName -> list of (file, json-pointer/yaml-path, method, path)`
  - update on file change; navigation becomes O(1) lookup.

**What the target PSI element should be**
- Prefer the `operationId` value element (so users see the mapping instantly).
- Alternative: target the method key (`get`, `post`, …) or the operation object start.

#### 3) Shared parsing layer (avoid duplicating YAML/JSON logic)
Create a small internal model:
- `OpenApiOperationLocation(file, path, method, operationIdPsi, controllerPsi, …)`

And implement extractors:
- YAML extractor using YAML PSI: walk `paths` mapping
- JSON extractor using JSON PSI: walk `paths` object

Keep parsing tolerant (missing keys, wrong types) and fail “quietly” (no exceptions in editor).

---

### Edge cases to handle (explicit list)

#### OpenAPI format / structure
- OpenAPI 3 (`openapi: 3.x`) vs OpenAPI 2 (`swagger: "2.0"`).
- Missing `paths` or non-object `paths`.
- Method keys case variations: `get` vs `GET` (normalize to lowercase).
- `$ref` usage:
  - operations can be `$ref`’d (rare but possible). MVP can ignore `$ref` and only support inline operations; document limitation.

#### Connexion-specific mapping
- Controller key name differences:
  - `x-openapi-router-controller` (modern)
  - `x-swagger-router-controller` (legacy)
- Controller can appear at multiple levels; implement the precedence order.
- `operationId` forms:
  - short form: `get_pets` (requires controller)
  - fully-qualified: `pkg.api.get_pets`
  - colon form: `pkg.api:get_pets`
  - class/method: `pkg.api.PetsController.get_pets`
- `operationId` missing or not a string.

#### Python resolution
- Multiple modules with same name on different source roots.
- Multiple functions with same name across project.
- Namespace packages / implicit packages (module resolution may be ambiguous).
- Symbol is a nested function (Connexion can’t address it; treat as no-match).
- Renames: after rename refactor, references should update if implemented via PSI reference or index.

#### Multiple specs / multiple matches
- Same operationId defined twice (multiple specs, multiple operations):
  - Spec → impl: still resolves to one Python symbol; ok.
  - Code → spec: return multiple targets; let IDE chooser pick.

#### Non-project imports / runtime sys.path mismatch
- In practice, Connexion may import controllers from different roots than IDE marks as sources.
- Strategy: try PSI-qualified resolution first; if it fails, fallback to textual/project-wide search with filtering.

---

### Test plan (unit-style light tests in this repo)
Use the existing conventions:
- Extend `fixtures.TestBase` (gives Python SDK + helpers + deterministic settings).
- Create files with `myFixture.configureByText(...)`.
- Trigger navigation and assert the target file/element.

#### A) Spec → implementation tests
1. **YAML + root controller + short operationId**
   - `openapi.yaml`:
     - root `x-openapi-router-controller: pkg.controllers.pets`
     - `paths: /pets: get: operationId: list_pets`
   - `pkg/controllers/pets.py`: `def list_pets(): ...`
   - Place caret inside `list_pets` string value and call `myFixture.gotoDeclaration()`.
   - Assert it navigates to `pets.py` at `def list_pets`.

2. **YAML + operation-level controller overrides root**
   - root controller `pkg.controllers.default`
   - operation-level controller `pkg.controllers.pets`
   - ensure it resolves to `pkg.controllers.pets.list_pets`.

3. **YAML + fully-qualified operationId ignores controller**
   - controller exists, but `operationId: pkg.controllers.pets.list_pets`
   - ensure it resolves to the fully-qualified target.

4. **YAML + colon operationId**
   - `operationId: pkg.controllers.pets:list_pets`
   - ensure it resolves.

5. **Legacy key support**
   - use `x-swagger-router-controller` (OpenAPI 2 style)
   - ensure resolution works.

6. **No controller + short operationId → no navigation**
   - `operationId: list_pets`, no controller anywhere
   - `myFixture.gotoDeclaration()` should return `null` / stay in place (assert no target).

7. **Ambiguous Python targets**
   - Two modules both contain `list_pets`
   - If operationId fully qualified, only one resolves.
   - If not fully qualified and only controller is used, ensure multiResolve returns both only if controller resolution is ambiguous; otherwise resolve uniquely.

8. **JSON spec variant**
   - Repeat (1) with `openapi.json` and JSON PSI.

#### B) Code → spec declaration tests
9. **Function → operation (controller + short operationId)**
   - Same as (1), but caret on `def list_pets` identifier.
   - Trigger your “Go to OpenAPI operation” (or `gotoDeclaration` if implemented that way).
   - Assert target is `operationId: list_pets` in `openapi.yaml`.

10. **Function → operation (fully-qualified operationId)**
    - spec uses `operationId: pkg.controllers.pets.list_pets`
    - caret on `list_pets` should navigate.

11. **Method → operation**
    - `class PetsController: def list_pets(self): ...`
    - spec uses `operationId: pkg.controllers.pets.PetsController.list_pets`
    - navigation finds it.

12. **Multiple matches across specs**
    - `openapi1.yaml` and `openapi2.yaml` both map to same function.
    - action should return multiple targets (assert target list size = 2, or assert chooser behavior if test harness supports it).

13. **Non-endpoint function**
    - function not referenced by any spec.
    - action returns no targets.

14. **Controller precedence test in reverse direction**
    - root controller differs from operation controller.
    - ensure reverse lookup uses the same precedence logic as forward.

#### C) Robustness / parsing failure tests
15. **Invalid YAML/JSON does not crash**
    - broken YAML, ensure action does nothing and no exceptions.

16. **Spec-like YAML that isn’t OpenAPI**
    - YAML file contains `paths:` but no `openapi/swagger` top-level.
    - ensure no references/actions are offered.

---

### Practical implementation notes (to avoid common pitfalls)
- Prefer PSI-based navigation targets (actual `PsiElement`s) over string/offset navigation.
- Keep resolution pure and side-effect free; do not import Python modules at runtime.
- Normalize qualified names for comparisons:
  - treat `:` and `.` forms consistently (e.g. compare both)
  - normalize method keys to lowercase.
- In tests, assert both:
  - correct target file (`PsiFile.name`)
  - correct target element text range or surrounding text (e.g. line contains `def list_pets`).

---

### MVP scope recommendation
To ship quickly with high value:
1) Implement **Spec → implementation** via `PsiReferenceContributor` for YAML first (then JSON).
2) Implement **Code → spec** as an explicit action/intention that scans OpenAPI files on demand (no index yet).
3) Once stable, add an index for speed and a `GotoDeclarationHandler` for seamless Ctrl/Cmd+B from code too.