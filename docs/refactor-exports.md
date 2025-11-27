### High‑level goal
You already have a nicely factored set of classes around `__all__` and public/private exports:

- `PyPrivateModuleImportInspection` – finds problematic imports from private modules and offers fixes.
- `PyAddSymbolToAllQuickFix` – ensures a symbol is added to `__all__` (possibly cross‑file).
- `PyAllExportUtil` – centralizes `__all__` + re‑export maintenance.

A good refactor/optimization is mostly about:

1. Making responsibilities explicit and symmetric across all export‑related inspections/quick‑fixes.
2. Pulling the shared analysis into one small “export model”/service instead of sprinkling ad‑hoc helpers.
3. Making the package structure reflect those roles so new features are easy to add.

Below is a concrete target structure and the minimal code‑level reshuffles to get there.

---

### 1. Proposed package layout

Under `.../inspections` (or a subpackage of it), introduce a small “export/public API” subsystem:

```text
com/github/chbndrhnns/intellijplatformplugincopy/inspections/
  exports/
    model/
      PackageExports.kt           // immutable view of a package's exports
      ExportChange.kt             // data class describing a desired change
    analysis/
      DunderAllAnalyzer.kt       // parses __all__ into PackageExports
      PrivateModuleImportAnalyzer.kt
    actions/
      PyAddSymbolToAllQuickFix.kt
      PyMakeSymbolPublicAndUseExportedSymbolQuickFix.kt
      PyUseExportedSymbolFromPackageQuickFix.kt
    inspection/
      PyPrivateModuleImportInspection.kt
    util/
      PyAllExportUtil.kt         // trimmed down to very focused write‑side helpers
```

Key ideas:

- `model` – purely read‑only/immutable data objects that describe current exports and desired changes.
- `analysis` – read/interpret PSI → `model` (no writes, no quick‑fix logic).
- `actions` – quick‑fixes that *apply* `ExportChange` using utilities.
- `inspection` – small classes that only:
  - find occurrences to analyze and
  - delegate to `analysis` + build `ProblemsHolder` entries with the appropriate fixes.
- `util` – low‑level PSI write operations used by multiple quick‑fixes.

This mirrors the refactor plan you already have for wrap‑intentions (`analysis/model/apply/preview/util`), just adapted to `__all__`/export logic.

---

### 2. Refactor `PyAllExportUtil` into smaller, testable helpers

`PyAllExportUtil` currently mixes three responsibilities:

1. Discover/parse `__all__` and associated imports (`findDunderAllAssignment`, reading `assignedValue`).
2. Compute the *desired* state (which names should be exported/re‑exported).
3. Apply PSI changes (inserting into sequences, injecting imports, creating assignment).

Refactor toward:

```kotlin
// model/PackageExports.kt
data class PackageExports(
    val dunderAllAssignment: PyAssignmentStatement?,
    val exportedNames: List<String>,        // from __all__
    val reExports: List<ReExport>,          // from `from ._lib import` etc.
)

data class ReExport(val moduleName: String, val names: List<String>)

// model/ExportChange.kt
data class ExportChange(
    val targetFile: PyFile,
    val name: String,
    val sourceModule: PyFile?,   // null = just add to __all__
)
```

```kotlin
// analysis/DunderAllAnalyzer.kt
object DunderAllAnalyzer {
    fun analyze(targetFile: PyFile): PackageExports { ... }  // reads PSI only
}
```

```kotlin
// util/PyAllExportWriter.kt (split out from PyAllExportUtil)
object PyAllExportWriter {
    fun applyExportChange(project: Project, exports: PackageExports, change: ExportChange) { ... }
}
```

Then `PyAllExportUtil.ensureSymbolExported` becomes a small facade around these pieces or can be inlined into quick‑fixes/tests once the responsibilities are cleanly separated.

Benefits:

- You can unit‑test `DunderAllAnalyzer.analyze` with synthetic PSI (or small fixture files) without touching write logic.
- Quick‑fix tests can be more focused: they assert that a given `ExportChange` is *produced* for a given scenario, and separately that `PyAllExportWriter` applies it correctly.

---

### 3. Make `PyPrivateModuleImportInspection` a thin inspection on top of an analyzer

`PyPrivateModuleImportInspection` currently:

- Walks `PyFile`.
- Finds `PyFromImportStatement`.
- Contains its own logic for private module detection, locating package `__init__.py`, parsing `__all__`, and deciding which problem/fix to register.

Refactor into:

```kotlin
// analysis/PrivateModuleImportAnalyzer.kt
object PrivateModuleImportAnalyzer {
    sealed interface ProblemKind {
        data class ShouldImportFromPackage(val packageFile: PyFile, val name: String) : ProblemKind
        data class ShouldMakeSymbolPublic(val packageFile: PyFile, val name: String) : ProblemKind
    }

    data class Problem(
        val importElement: PyImportElement,
        val kind: ProblemKind,
    )

    fun analyze(file: PyFile): List<Problem> { ... }
}
```

```kotlin
// inspection/PyPrivateModuleImportInspection.kt
class PyPrivateModuleImportInspection : PyInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        val settings = PluginSettingsState.instance().state
        if (!settings.enablePyMissingInDunderAllInspection) return PyElementVisitor()

        return object : PyElementVisitor() {
            override fun visitPyFile(node: PyFile) {
                super.visitPyFile(node)

                for (problem in PrivateModuleImportAnalyzer.analyze(node)) {
                    val fix = when (problem.kind) {
                        is ProblemKind.ShouldImportFromPackage ->
                            PyUseExportedSymbolFromPackageQuickFix(problem.kind.name)
                        is ProblemKind.ShouldMakeSymbolPublic ->
                            PyMakeSymbolPublicAndUseExportedSymbolQuickFix(problem.kind.name)
                    }

                    val message = when (problem.kind) {
                        is ProblemKind.ShouldImportFromPackage ->
                            "Symbol '${problem.kind.name}' is exported from package __all__; import it from the package instead of the private module"
                        is ProblemKind.ShouldMakeSymbolPublic ->
                            "Symbol '${problem.kind.name}' is not exported from package __all__ yet; make it public and import from the package"
                    }

                    holder.registerProblem(problem.importElement, message, fix)
                }
            }
        }
    }
}
```

This keeps the inspection class tiny and makes it symmetric with any future `__all__`‑related inspections (e.g. “unused export”, “exported but not imported anywhere”).

---

### 4. Standardize quick‑fix responsibilities

Currently `PyAddSymbolToAllQuickFix` mixes:

- Identifying the correct `targetFile`/`sourceModule` (context resolution).
- Invoking `PyAllExportUtil.ensureSymbolExported`.

Refactor into:

```kotlin
// actions/PyAddSymbolToAllQuickFix.kt
class PyAddSymbolToAllQuickFix(private val name: String) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Add to __all__"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val change = ExportContextResolver.resolveExportChange(element, name, updater) ?: return

        val exports = DunderAllAnalyzer.analyze(change.targetFile)
        PyAllExportWriter.applyExportChange(project, exports, change)
    }
}
```

```kotlin
// analysis/ExportContextResolver.kt
object ExportContextResolver {
    fun resolveExportChange(element: PsiElement, name: String, updater: ModPsiUpdater): ExportChange? {
        // essentially your existing logic that decides between
        //   - current file is __init__.py
        //   - or find the package __init__.py from VFS/PSI
    }
}
```

`PyMakeSymbolPublicAndUseExportedSymbolQuickFix` and `PyUseExportedSymbolFromPackageQuickFix` can follow the same pattern:

- Resolve a small, testable `ExportChange` (or a pair of changes: `ExportChange` + import‑rewrite change).
- Delegate actual PSI edits to dedicated `Writer`/`Util` classes.

This makes it easy to:

- Share cross‑file behavior between fixes.
- Add a new fix that also updates `__all__` using the same primitives.

---

### 5. Testing and future extensibility

With the above structure in place you gain:

- **Analyzer tests** independent from quick‑fix execution:
  - Given a fixture file, `PrivateModuleImportAnalyzer.analyze` returns a list of problems.
  - Given a package file, `DunderAllAnalyzer.analyze` returns the `PackageExports` model.
- **Writer/util tests** that verify PSI transformations only:
  - Given `PackageExports` and `ExportChange`, `PyAllExportWriter` produces desired source.
- **Quick‑fix integration tests** remain as they are, but become simpler to reason about because most logic is in small, isolated components.

Future improvements become straightforward:

- Add inspections like “symbol is in `__all__` but not actually defined or re‑exported” by reusing the same `PackageExports` model.
- Support more complex `__all__` patterns if needed (e.g. concatenations) by extending `DunderAllAnalyzer` without touching quick‑fixes.

---

### 6. Minimal first steps

If you want a small, low‑risk first refactor, you can do it incrementally:

1. **Introduce `DunderAllAnalyzer` and `PackageExports`**:
   - Move `findDunderAllAssignment` and `findDunderAllNames`‑style logic into `analysis`.
   - Replace direct calls in `PyPrivateModuleImportInspection` and `PyAllExportUtil` with calls to the analyzer.

2. **Extract `PyAllExportWriter`** from `PyAllExportUtil`:
   - Move `updateExistingDunderAll`, `createNewDunderAll`, and `addOrUpdateImportForModuleSymbol` into it.
   - Let `ensureSymbolExported` just be a thin façade that wires `Analyzer` + `Writer`.

3. **Only then** split the package into `exports/model`, `exports/analysis`, `exports/actions`, etc.

This preserves behavior while steadily moving toward the more modular structure.

If you share more of the surrounding inspection/quick‑fix classes, I can tailor the package layout and interfaces even more precisely to your codebase.