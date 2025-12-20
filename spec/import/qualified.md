### Goal
When a file already has a relative import of a module (e.g., `from . import src`) and the user attempts to import a symbol defined inside that module (e.g., `NewSymbol`), offer an extra item in the imports popup labeled `NewSymbol from .src`. If selected, rewrite the reference at the caret to `src.NewSymbol` (or the alias, if present) and do not add a new `from ... import ...` statement.

### Where to integrate in PyCharm codebase
- Candidates listing and fix wiring:
  - `python-psi-impl/.../codeInsight/imports/AutoImportQuickFix.java`
  - `python-psi-impl/.../codeInsight/imports/ImportFromExistingAction.java`
  - `python-psi-impl/.../codeInsight/imports/ImportCandidateHolder.java`
- Popup rendering/selection:
  - `python/src/.../codeInsight/imports/PyImportChooser.java`

These already implement the “Import this name” workflow: candidates are collected, listed in a popup, and a selected candidate is applied by either adding to an existing import or inserting a new import, and sometimes qualifying the reference.

### High-level design
- Add a new “qualify-via-existing-module” import candidate alongside normal candidates.
- This candidate points to an existing `PyImportElement` that imports the module (e.g., `src` in `from . import src`), but is flagged as “qualify only”.
- Its presentation text is `NewSymbol from .src`.
- On selection, update only the expression at the caret to `src.NewSymbol` (or alias), do not modify imports.

This leverages the existing candidate list and popup, avoiding a separate quick-fix pathway.

### Implementation steps
1. Detect suitable existing relative imports in candidate collection
   - In the code path that builds candidates for unresolved `NewSymbol` (where `AutoImportQuickFix` gets populated):
     - Search the file’s import section for `PyFromImportStatement` with `relativeLevel > 0` and no `importSource` (i.e., `from . import <module>` style) and with a `PyImportElement` whose visible name is the module name you want to qualify with (e.g., `src`) or an alias (`as s`).
     - Also support `from .pkg import src` if feasible: you can still qualify via the imported name (alias or `src`). The leading dots for presentation come from the `relativeLevel` and `importSource`.
   - For each such `PyImportElement` that represents an imported module, add a special candidate for `NewSymbol` that references that `PyImportElement` but is marked “qualify only”.

2. Represent the new candidate kind
   - Option A (minimal invasive): extend `ImportCandidateHolder` with an optional boolean flag `qualifyOnly`. New constructor overload retains backward compatibility.
     - Add `private final boolean myQualifyOnly;` and `public boolean isQualifyOnly()`.
   - Option B: subclass `ImportCandidateHolder` (requires making class/methods extensible). This is heavier; prefer Option A.

3. Provide correct presentation text for the popup
   - `ImportCandidateHolder.getPresentableText()` currently formats `“<name> from <dots><source>”` for `from` imports, but for `from . import src` the import source is `null`, so it would show `“NewSymbol from .”` (missing the module name).
   - For `qualifyOnly` candidates:
     - Override text construction to include the imported element’s visible name after the dots: `NewSymbol from .src` (if alias: `NewSymbol from .s`).
     - Keep other cases unchanged to avoid regressions.
   - Implementation idea:
     - Add a branch in `getPresentableText()`:
       - If `isQualifyOnly()` and `getImportElement()` is not null and parent is `PyFromImportStatement`, compute:
         - `dots = StringUtil.repeat(".", fromImportStatement.getRelativeLevel())`
         - `moduleToken = importElement.getVisibleName()`
         - `return myImportableName + " from " + dots + moduleToken`

4. Apply behavior on selection without changing imports
   - In `ImportFromExistingAction.doIt(ImportCandidateHolder item)`:
     - Currently, if `item.getImportElement() != null` and its parent is `PyFromImportStatement`, it calls `addNameToFromImportStatement(...)`, which would rewrite imports (not what we want).
     - Change `addToExistingImport(item)` to branch:
       - If `item.isQualifyOnly()` → perform qualification only:
         - `visible = importElement.getVisibleName()` (this respects `as` alias)
         - Replace `myTarget` with expression ``visible + "." + myName`` via `PyElementGenerator` and `myTarget.replace(...)`.
       - Else → existing behavior (add name to from-import).
   - Ensure this is done within write command as currently done by `doWriteAction`.

5. Guardrails and edge cases
   - Aliases: `from . import src as s` must produce `s.NewSymbol` and the presentation `NewSymbol from .s`.
   - Existing direct from-import: if `from .src import NewSymbol` already exists, don’t add the `qualifyOnly` suggestion (the reference will resolve anyway, and a different candidate may exist).
   - Shadowing: if `src` (or alias) is shadowed in the local scope, skip offering `qualifyOnly` for that import element (qualification would resolve to the wrong thing). You can check reference resolution of a dummy `src` reference at the caret scope.
   - Multiple candidates: it’s fine to show both the standard “add from-import” and the new “qualify via module” so users can choose. ML ranking may reorder them.
   - Non-module import elements: only add `qualifyOnly` when the import element represents a module/package, not a symbol.
   - Absolute vs relative: this feature is specifically for relative `from` imports. If the existing import is `import pkg.src as s`, the current code path already qualifies via `import` parents (that branch already does qualification). The gap we’re closing is `from . import src`.

6. Internationalized UI string
   - If you hardcode `getPresentableText()` as above, you may not need a new bundle key. If you prefer using `PyPsiBundle`, add a key like `QFIX.auto.import.qualify.via.relative.module` with placeholders `{0} from {1}{2}` where `{1}` is dots and `{2}` is visible module token.

### Minimal code changes (pseudocode)
- ImportCandidateHolder.java
  - Add field and accessors:
    ```java
    private final boolean myQualifyOnly;
    public ImportCandidateHolder(..., boolean qualifyOnly) { ... this.myQualifyOnly = qualifyOnly; }
    public boolean isQualifyOnly() { return myQualifyOnly; }
    ```
  - Adjust `getPresentableText()` branch for `qualifyOnly` with `from . import src` to yield `.src`.
- AutoImportQuickFix.java (or wherever candidates are built)
  - When scanning imports, add:
    ```java
    if (fromImportStatement.getRelativeLevel() > 0 && fromImportStatement.getImportSource() == null) {
      for (PyImportElement el : fromImportStatement.getImportElements()) {
        if (el.isModuleImported(project)) { // conceptually: resolve el to a module
          addImport(importableSymbol, file, el, /*path=*/null, /*asName=*/el.getAsName(), /*qualifyOnly=*/true);
        }
      }
    }
    ```
  - Use the new overload storing `qualifyOnly = true`.
- ImportFromExistingAction.java
  - In `addToExistingImport(ImportCandidateHolder item)`:
    ```java
    if (item.isQualifyOnly()) {
      String mod = importElement.getVisibleName();
      PyElementGenerator gen = PyElementGenerator.getInstance(myTarget.getProject());
      myTarget.replace(gen.createExpressionFromText(LanguageLevel.forElement(myTarget), mod + "." + myName));
      return;
    }
    // old behavior
    if (parent instanceof PyFromImportStatement) { ... }
    ```

### Tests
Add or extend tests under `python/testSrc` and testData files under `python/testData`.

1. Suggestion appears and qualifies via module
   - Input file:
     ```python
     # file: pkg/mod.py
     from . import src
     NewSymbol  # <caret> here
     ```
   - Setup: `pkg/src.py` defines `class NewSymbol: pass`.
   - Action: trigger auto-import quick-fix.
   - Expect:
     - Popup contains an item with text `NewSymbol from .src`.
     - After selecting that item, file becomes:
       ```python
       from . import src
       src.NewSymbol
       ```
     - No additional import added.

2. Alias import
   - Input:
     ```python
     from . import src as s
     NewSymbol  # <caret>
     ```
   - Expect popup: `NewSymbol from .s`; result: `s.NewSymbol`; imports unchanged.

3. Multiple modules, both options present
   - Input has both `from . import src` and external library candidate.
   - Expect both the standard “from X import NewSymbol” and the new “NewSymbol from .src” listed; selection of the latter qualifies, not imports.

4. No suggestion if not a module import
   - `from .src import something` (symbol import), not a `from . import src`.
   - Ensure no `qualifyOnly` candidate is added from this.

5. Shadowing guard
   - Local variable `src = 42` present near caret.
   - Do not offer `NewSymbol from .src` candidate (or if offered, ensure qualification replaces to alias without breaking resolution). Prefer to suppress.

6. Relative levels formatting
   - `from .. import src` in `pkg/sub/mod.py`.
   - Popup should say `NewSymbol from ..src` and result `src.NewSymbol` (the visible token is `src`; dots are only for presentation).

7. No duplication when equivalent candidate exists
   - If there is already a candidate that would qualify via `import src` (absolute import), ensure the new candidate co-exists but both do not cause duplicate final code.

8. Intention Preview compatibility
   - Ensure `execute()` branch for preview (non-physical) handles `qualifyOnly` by rewriting the in-memory PSI.

9. Performance smoke
   - Large file with many imports should not noticeably slow down candidate building. Optionally measure or add a test with timeout.

Test scaffolding hints:
- Use existing quick-fix test classes:
  - `python/testSrc/com/jetbrains/python/quickFixes/PyAddImportQuickFixTest.java`
  - Add methods like `testQualifyViaExistingRelativeModule()` and test data under `python/testData/quickFix/addImport/...`.
- In tests that verify the popup content, if headless test infra auto-selects the first item, ensure your special candidate sorts correctly or explicitly simulate selection by ordering your candidate first in the list for unit-test mode. Alternatively, assert transformation result and that no new import statements were added.
- For popup text, you can assert the `ImportCandidateHolder.getPresentableText()` directly by obtaining candidates via the fix and inspecting them before application.

### Sorting and ML ranking
- `ImportCandidateHolder.compareTo` considers candidates with `myImportElement != null` first. Your `qualifyOnly` candidate will also have `importElement != null`, so it should be near the top, often before new-import candidates. ML import chooser may further reorder; that’s acceptable.

### Risks and mitigations
- Ambiguity if multiple `from . import <module>` exist: create one `qualifyOnly` candidate per matching import element; the visible name (or alias) disambiguates.
- Presentation correctness for `from . import src`: `getPresentableText()` must add the module token after dots; add tests specifically for this.
- Backward compatibility: keep constructors and behavior unchanged for existing candidates; introduce new fields/overloads conservatively.

### Optional: Feature toggle
- Add a registry key (e.g., `python.imports.qualifyViaRelativeModule`) to enable/disable candidate creation. Default enabled if confident; else roll out behind the flag first.

### Summary
- Extend candidate collection to add a special “qualify via the already-imported relative module” item.
- Mark those candidates as `qualifyOnly` and change the apply path to rewrite only the reference (`moduleOrAlias.NewSymbol`) without touching imports.
- Adjust presentation for relative-from to show `NewSymbol from .src`.
- Cover with tests for normal, alias, multiple candidates, shadowing, and formatting cases.