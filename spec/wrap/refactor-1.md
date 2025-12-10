### Summary

Your `WrapWithExpectedTypeIntention.kt` (382 lines) mixes discovery (availability/type analysis), UI decisions (
chooser), PSI mutations (wrapping/imports), and preview composition. Below is a concrete refactoring plan that reduces
statefulness, removes duplication, and isolates responsibilities into small, testable components.

---

### Pain points observed

- Global mutable state in the intention instance: `problematicElement`, `expectedTypeName`, `expectedTypeElement`,
  `unionCandidates` (lines ~65–69). This couples `isAvailable`, `invoke`, and `generatePreview` and can lead to
  stale/incorrect previews.
- Duplication of wrapping logic between `invoke` (lines ~147–188) and `generatePreview` (lines ~191–209).
- `addImportIfNeeded` (lines ~238–269) and `isImported` (lines ~275–294) are embedded utilities but conceptually
  separate concerns.
- `isAlreadyWrappedWith` (lines ~301–317) is simplistic (name-only); it misses qualified calls like `module.ctor(expr)`
  and resolved symbols.
- `collectUnionCtorCandidates` (lines ~320–351) only handles PEP 604 `A | B` unions; it ignores `typing.Union[...]` and
  `typing.Optional[...]` forms.
- UI concerns (`PopupHost`, `JbPopupHost`) live inside the intention file (lines ~29–63).
- Hidden test seam: `WrapWithExpectedTypeIntentionHooks.popupHost` is referenced (line ~132) but not declared here; the
  coupling is unclear.

---

### Target design (small, focused components)

Create a package `...intention.wrap` and split into the following:

- `WrapWithExpectedTypeIntention` (kept tiny): wires everything together.
- `analysis/ExpectedTypeAnalyzer.kt`: computes the expected wrapping action at caret.
- `model/WrapPlan.kt`: immutable description of what to do.
- `apply/WrapApplier.kt`: performs PSI edits and import handling.
- `preview/WrapPreview.kt`: builds preview text/diff from a `WrapPlan`.
- `ui/PopupHost.kt` + `ui/JbPopupHost.kt`: chooser abstraction and default implementation.
- `util/PyImportService.kt`: import detection and addition.
- `util/PyWrapHeuristics.kt`: `isContainerExpression`, `isAlreadyWrappedWith`, etc.
- `util/UnionCandidates.kt`: extract and normalize union constructor candidates across PEP 604 and
  `typing.Union/Optional`.

This keeps each class under ~150 lines and makes unit testing straightforward.

---

### Core data model

Replace the four mutable fields with a single immutable `WrapPlan`:

```kotlin
sealed interface WrapPlan {
    val element: PyExpression

    data class Single(
        override val element: PyExpression,
        val ctorName: String,
        val ctorElement: PsiNamedElement? // for precise import/resolve
    ) : WrapPlan

    data class UnionChoice(
        override val element: PyExpression,
        val candidates: List<Ctor>
    ) : WrapPlan {
        data class Ctor(val name: String, val element: PsiNamedElement?)
    }
}
```

Store it in a transient, safe place between `isAvailable` → `invoke` → `generatePreview`. Two robust options:

- Cache on the editor using `Editor#getUserData(Key<WrapPlan>)` with a private key.
- Or recompute cheaply from caret in both `invoke` and `generatePreview` (preferred for correctness if analysis is
  fast).

---

### Refactor the intention class

Minimal, side-effect-free `isAvailable`; small `invoke`; no global fields.

```kotlin
class WrapWithExpectedTypeIntention(
    private val analyzer: ExpectedTypeAnalyzer = ExpectedTypeAnalyzer(),
    private val applier: WrapApplier = WrapApplier(),
    private val preview: WrapPreview = WrapPreview(),
    private val popupHost: PopupHost = JbPopupHost()
) : IntentionAction, HighPriorityAction, DumbAware {

    override fun getFamilyName() = "Type mismatch wrapper"

    override fun getText(): String = "Wrap with expected type"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val plan = analyzer.analyzeAtCaret(editor, file) ?: return false
        editor.putUserData(PLAN_KEY, plan)
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val plan = editor.getUserData(PLAN_KEY) ?: analyzer.analyzeAtCaret(editor, file) ?: return
        when (plan) {
            is WrapPlan.Single -> applier.apply(project, file, plan)
            is WrapPlan.UnionChoice -> popupHost.showChooser(
                editor,
                title = "Select expected type",
                items = plan.candidates,
                render = { it.name },
                onChosen = { chosen -> applier.apply(project, file, plan.element, chosen) }
            )
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val plan =
            editor.getUserData(PLAN_KEY) ?: analyzer.analyzeAtCaret(editor, file) ?: return IntentionPreviewInfo.EMPTY
        return preview.build(file, plan)
    }

    override fun startInWriteAction() = true

    private companion object {
        val PLAN_KEY = Key.create<WrapPlan>("wrap.with.expected.plan")
    }
}
```

Benefits:

- No mutable member fields.
- `getText()` can stay generic or derive from a cached plan.
- `generatePreview` no longer depends on side effects from `isAvailable`.

---

### ExpectedTypeAnalyzer (extract logic from current `isAvailable`)

Single place to compute:

- the mismatching expression at caret,
- whether it’s already wrapped,
- either a `Single` plan or a `UnionChoice` plan.

```kotlin
class ExpectedTypeAnalyzer {
    fun analyzeAtCaret(editor: Editor, file: PsiFile): WrapPlan? {
        val element = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null
        val tec = TypeEvalContext.codeAnalysis(file.project, file)
        val names = PyTypeIntentions.computeTypeNames(element, tec)
        if (names.actual == null || names.expected == null || names.actual == names.expected) return null

        // Prefer precise ctor element when available
        val ctorName = PyTypeIntentions.expectedCtorName(element, tec)
        val ann = names.expectedCtorElement as? PyTypedElement

        val unionCtors = ann?.let { UnionCandidates.collect(it, element) }.orEmpty()
        if (unionCtors.size >= 2) {
            if (PyWrapHeuristics.isWrappedWithAny(element, unionCtors.map { it.name })) return null
            return WrapPlan.UnionChoice(element, unionCtors)
        }

        if (!ctorName.isNullOrBlank()) {
            if (PyWrapHeuristics.isAlreadyWrappedWith(
                    element,
                    ctorName,
                    expected = names.expectedElement as? PsiNamedElement
                )
            ) return null
            val ctorElem = (names.expectedElement as? PsiNamedElement)
            return WrapPlan.Single(element, ctorName, ctorElem)
        }
        return null
    }
}
```

---

### WrapApplier (PSI edits + imports)

Move `addImportIfNeeded`, `applyWrapWith`, and wrapping construction here. Unify invoke code path and handle `list`/
`str` special cases.

```kotlin
class WrapApplier(
    private val imports: PyImportService = PyImportService()
) {
    fun apply(project: Project, file: PsiFile, plan: WrapPlan.Single) =
        WriteCommandAction.runWriteCommandAction(project) {
            imports.ensureImportedIfNeeded(file, plan.element, plan.ctorElement)
            replaceWithWrapped(project, plan.element, plan.ctorName)
        }

    fun apply(project: Project, file: PsiFile, element: PyExpression, choice: WrapPlan.UnionChoice.Ctor) =
        WriteCommandAction.runWriteCommandAction(project) {
            imports.ensureImportedIfNeeded(file, element, choice.element)
            replaceWithWrapped(project, element, choice.name)
        }

    private fun replaceWithWrapped(project: Project, element: PyExpression, ctorName: String) {
        val generator = PyElementGenerator.getInstance(project)
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val text = when {
            ctorName == "str" && unwrapped is PyNumericLiteralExpression -> "\"${unwrapped.text}\""
            ctorName == "list" && PyWrapHeuristics.isContainerExpression(unwrapped) -> "list(${unwrapped.text})"
            ctorName == "list" -> "[${unwrapped.text}]"
            else -> "$ctorName(${unwrapped.text})"
        }
        val wrapped = generator.createExpressionFromText(LanguageLevel.getLatest(), text)
        element.replace(wrapped)
    }
}
```

---

### PyImportService (imports logic)

Extract and slightly harden `addImportIfNeeded` and `isImported`.

```kotlin
class PyImportService {
    fun ensureImportedIfNeeded(file: PsiFile, anchor: PyExpression, element: PsiNamedElement?) {
        element ?: return
        val builtins = PyBuiltinCache.getInstance(anchor)
        if (builtins.isBuiltin(element)) return

        val name = element.name ?: return
        val pyFile = file as? PyFile ?: return

        // symbol already resolvable in scope?
        val owner = ScopeUtil.getScopeOwner(anchor) ?: pyFile
        val tec = TypeEvalContext.codeAnalysis(file.project, file)
        val resolved = PyResolveUtil.resolveQualifiedNameInScope(QualifiedName.fromDottedString(name), owner, tec)
        if (resolved.isNotEmpty()) return

        if (isImported(pyFile, name)) return

        AddImportHelper.addImport(element, file, anchor)
    }

    private fun isImported(file: PyFile, name: String): Boolean =
        file.importBlock.any { stmt ->
            when (stmt) {
                is PyFromImportStatement -> stmt.importElements.any { it.importedQName?.lastComponent == name }
                is PyImportStatement -> stmt.importElements.any { it.visibleName == name }
                else -> false
            }
        }
}
```

---

### PyWrapHeuristics (utilities and stronger already-wrapped check)

- Keep `isContainerExpression` as an extension.
- Strengthen `isAlreadyWrappedWith` to also match qualified calls (`pkg.ctor`, `typing.Text`), and when available
  compare resolved symbol to `expected`.

```kotlin
object PyWrapHeuristics {
    fun isContainerExpression(expr: PyExpression): Boolean = when (expr) {
        is PyListLiteralExpression,
        is PyTupleExpression,
        is PySetLiteralExpression,
        is PyDictLiteralExpression,
        is PyListCompExpression,
        is PySetCompExpression,
        is PyDictCompExpression,
        is PyGeneratorExpression -> true
        else -> false
    }

    fun isWrappedWithAny(expr: PyExpression, ctorNames: List<String>): Boolean =
        ctorNames.any { isAlreadyWrappedWith(expr, it, null) }

    fun isAlreadyWrappedWith(expr: PyExpression, ctorName: String, expected: PsiNamedElement?): Boolean {
        // Case A: the expression itself is a call to ctorName
        (expr as? PyCallExpression)?.let { call ->
            if (calleeMatches(call, ctorName, expected)) return true
        }
        // Case B: the expression is an argument of a call to ctorName
        val call = PsiTreeUtil.getParentOfType(expr, PyCallExpression::class.java)
        val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java)
        if (call != null && argList != null && PsiTreeUtil.isAncestor(argList, expr, false)) {
            if (calleeMatches(call, ctorName, expected)) return true
        }
        return false
    }

    private fun calleeMatches(call: PyCallExpression, ctorName: String, expected: PsiNamedElement?): Boolean {
        val callee = call.callee
        val name = (callee as? PyReferenceExpression)?.name
        if (name == ctorName) return true
        if (expected != null) {
            val resolved = (callee as? PyReferenceExpression)?.reference?.resolve() as? PsiNamedElement
            if (resolved != null && resolved == expected) return true
        }
        return false
    }
}
```

---

### UnionCandidates (normalize unions beyond PEP 604)

Expand `collectUnionCtorCandidates` to detect `typing.Union[A, B]` and `typing.Optional[A]`.

```kotlin
object UnionCandidates {
    fun collect(annotation: PyTypedElement, anchor: PyExpression): List<WrapPlan.UnionChoice.Ctor> {
        val expr = annotation as? PyExpression ?: return emptyList()
        val out = LinkedHashSet<WrapPlan.UnionChoice.Ctor>()

        fun addRef(ref: PyReferenceExpression) {
            val name = ref.name ?: return
            val resolved = ref.reference.resolve() as? PsiNamedElement
            out += WrapPlan.UnionChoice.Ctor(name, resolved)
        }

        fun visit(e: PyExpression) {
            when (e) {
                is PyBinaryExpression -> if (e.operator == PyTokenTypes.OR) {
                    (e.leftExpression as? PyExpression)?.let(::visit)
                    (e.rightExpression as? PyExpression)?.let(::visit)
                    return
                }
                is PySubscriptionExpression -> { // Union[A, B] / Optional[A]
                    val calleeName = (e.operand as? PyReferenceExpression)?.name
                    if (calleeName == "Union" || calleeName == "Optional") {
                        e.indexExpression?.let { idx ->
                            when (idx) {
                                is PyTupleExpression -> idx.elements.filterIsInstance<PyReferenceExpression>()
                                    .forEach(::addRef)
                                is PyReferenceExpression -> addRef(idx)
                            }
                        }
                        return
                    }
                }
            }
            (e as? PyReferenceExpression)?.let(::addRef)
        }

        visit(expr)

        // Filter out builtins only if all are non-builtin and at least 2 remain
        val builtins = PyBuiltinCache.getInstance(anchor)
        val distinct = out.toList().distinctBy { it.name }
        val nonBuiltin = distinct.filter { it.element != null && !builtins.isBuiltin(it.element!!) }
        return if (nonBuiltin.size >= 2 && nonBuiltin.size == distinct.size) nonBuiltin else emptyList()
    }
}
```

---

### Preview builder (deduplicate preview composition)

Unify preview text generation with the same rules as the applier.

```kotlin
class WrapPreview {
    fun build(file: PsiFile, plan: WrapPlan): IntentionPreviewInfo = when (plan) {
        is WrapPlan.Single -> previewFor(file, plan.element, plan.ctorName)
        is WrapPlan.UnionChoice -> IntentionPreviewInfo.EMPTY // chooser case: can’t show single preview
    }

    private fun previewFor(file: PsiFile, element: PyExpression, ctorName: String): IntentionPreviewInfo {
        val unwrapped = PyPsiUtils.flattenParens(element) ?: element
        val original = unwrapped.text
        val modified = when {
            ctorName == "str" && unwrapped is PyNumericLiteralExpression -> "\"$original\""
            ctorName == "list" && PyWrapHeuristics.isContainerExpression(unwrapped) -> "list($original)"
            ctorName == "list" -> "[$original]"
            else -> "$ctorName($original)"
        }
        return IntentionPreviewInfo.CustomDiff(file.fileType, file.name, element.text, modified)
    }
}
```

---

### UI extraction

Move `PopupHost` + `JbPopupHost` to `ui` package. Keep the same signature used in tests. Expose a tiny, explicit `Hooks`
for test injection.

```kotlin
object WrapWithExpectedTypeHooks {
    @Volatile
    var popupHostFactory: (() -> PopupHost)? = null
}

class JbPopupHost : PopupHost { /* unchanged */ }
```

The intention constructor can then use `WrapWithExpectedTypeHooks.popupHostFactory?.invoke() ?: JbPopupHost()` or accept
a `PopupHost` directly (dependency injection is simpler for tests).

---

### Additional improvements

- Resolve callee fully-qualified names in `isAlreadyWrappedWith` when `expected` is present (already included above).
- Guard against `null` file cast in imports (`file as? PyFile` checked).
- Avoid computing `TypeEvalContext` multiple times: analyzer creates it once and passes to helper functions if you add
  parameters as needed.
- Make all helpers `internal` top-level to simplify tests.
- KDoc each public class/method with responsibilities and invariants.

---

### Proposed file layout

```
intention/
  WrapWithExpectedTypeIntention.kt            // ~100–130 lines
  model/WrapPlan.kt                            // ~40 lines
  analysis/ExpectedTypeAnalyzer.kt             // ~90 lines
  apply/WrapApplier.kt                         // ~110 lines
  preview/WrapPreview.kt                       // ~60 lines
  ui/PopupHost.kt                              // ~40 lines
  ui/JbPopupHost.kt                            // ~60 lines
  util/PyImportService.kt                      // ~120 lines (with tests)
  util/PyWrapHeuristics.kt                      // ~100 lines (with tests)
  util/UnionCandidates.kt                      // ~120 lines (with tests)
```

---

### Test plan (high-level)

- Analyzer:
    - Returns `null` when types match.
    - Returns `Single` for simple mismatches; respects `isAlreadyWrappedWith`.
    - Returns `UnionChoice` when two or more non-builtin, distinct constructors are present across PEP 604 and
      `typing.Union/Optional`.
- Heuristics:
    - `isContainerExpression` covers lists/tuples/sets/dicts/comprehensions/generators.
    - `isAlreadyWrappedWith` matches direct call, qualified call, and resolved-call equality.
- Applier + Preview parity:
    - Numeric to `str` → quotes literal.
    - `list` over containers vs. scalars.
    - General `$ctor(expr)` formatting.
- Imports:
    - Skips builtins.
    - Detects both `from x import Name` and `import x as Name` styles.
    - Doesn’t duplicate when symbol resolvable in scope.
- UI:
    - Chooser displays candidate names and applies chosen wrapping.

Use your existing `FakePopupHost` and tests under `.../intention/wrap/` to drive the chooser path.

---

### Migration checklist

- [ ] Introduce `WrapPlan` and switch `isAvailable`/`invoke`/`generatePreview` to use it (keep old helpers temporarily).
- [ ] Extract `PyImportService`, move old import logic and tests.
- [ ] Extract `PyWrapHeuristics`, move `isContainerExpression` and improved `isAlreadyWrappedWith`.
- [ ] Extract `UnionCandidates` and expand union parsing.
- [ ] Extract `WrapApplier` and `WrapPreview`; delete duplicated logic from intention.
- [ ] Move `PopupHost` and `JbPopupHost` to `ui`; wire dependency injection for tests.
- [ ] Delete old mutable fields and helpers from the intention file.

---

### Expected outcome

- Shorter intention class, easier to reason about.
- No cross-method hidden state; previews are always correct even if `isAvailable` didn’t run immediately beforehand.
- Single source of truth for wrapping rules, shared between apply and preview.
- Cleaner test surface and easier future enhancements (e.g., supporting `set`, `tuple`, or callable factories).

If you’d like, I can sketch a PR diff for step 1 (introducing `WrapPlan` and refactoring `isAvailable`/`invoke`/
`generatePreview`) based on your current project layout.