### What to cache (and where)

Below is a concrete, file-and-function–level plan to eliminate repeated PSI walks and object recomputation. All
suggestions are behavior‑preserving and local.

---

### File: `CaretSelection.kt`

#### 1) Cache the parent chain lookups in `isInsideFunctionCallArgument`

Current flow:

- `isInsideFunctionCallArgument(expr)` → calls `inArgList(expr)` → calls `PsiTreeUtil.getParentOfType` 2× (for
  `PyArgumentList` and then `PyCallExpression`).
- Then `isExcludedAssignment(call)` and `isExcludedReturn(call)` do more parent-of-type walks.

Cacheable values per starting `expr`:

- `PyArgumentList` and `PyCallExpression` pair returned by `inArgList`.
- The boolean results of `isExcludedAssignment(call)` and `isExcludedReturn(call)` for that `call`.
- The first `PyAssignmentStatement` and `PyReturnStatement` ancestors of `call`.

Low‑impact pattern:

- Add a tiny memo object local to the outer operation (or use a simple `MutableMap<PsiElement, ...>` with `PsiElement`
  keys held weakly if you want cross‑call reuse). Given these helpers are hot but short‑lived, per‑call locals are
  usually enough.

Sketch:

```kotlin
private data class ArgContext(
    val argList: PyArgumentList?,
    val call: PyCallExpression?
)

private fun computeArgContext(expr: PyExpression): ArgContext {
    val argList = PsiTreeUtil.getParentOfType(expr, PyArgumentList::class.java)
    val call = if (argList != null) PsiTreeUtil.getParentOfType(argList, PyCallExpression::class.java) else null
    return ArgContext(argList, call)
}

private inline fun <T> withArgContext(
    expr: PyExpression,
    crossinline block: (ArgContext) -> T
): T = block(computeArgContext(expr))

private fun isInsideFunctionCallArgument(expr: PyExpression): Boolean =
    withArgContext(expr) { (argList, call) ->
        if (argList == null || call == null) return@withArgContext false
        if (isExcludedAssignmentCached(call)) return@withArgContext false
        if (isExcludedReturnCached(call)) return@withArgContext false
        true
    }

private val excludedAssignmentCache = java.util.WeakHashMap<PyCallExpression, Boolean>()
private fun isExcludedAssignmentCached(call: PyCallExpression): Boolean =
    excludedAssignmentCache.getOrPut(call) {
        val assignment = PsiTreeUtil.getParentOfType(call, PyAssignmentStatement::class.java)
        if (assignment != null && assignment.assignedValue == call) {
            assignment.targets.any { (it as? PyTargetExpression)?.annotation != null }
        } else false
    }

private val excludedReturnCache = java.util.WeakHashMap<PyCallExpression, Boolean>()
private fun isExcludedReturnCached(call: PyCallExpression): Boolean =
    excludedReturnCache.getOrPut(call) {
        val returnStmt = PsiTreeUtil.getParentOfType(call, PyReturnStatement::class.java)
        returnStmt != null && returnStmt.expression == call
    }
```

Notes:

- WeakHashMap prevents holding PSI strongly. If you keep caching strictly within the scope of a single public call,
  simple locals are even safer and cheaper; the pattern above demonstrates the idea.

#### 2) Reuse parent-of-type results in `findExpressionAtCaret`

Hot spots:

- `chooseBest` calls `PsiTreeUtil.getParentOfType(leaf, PyArgumentList)` and `getParentOfType(leaf, PyCallExpression)`
  immediately after `inArgList`-style checks have likely traversed the same path.

What to cache:

- For a given `leaf`, compute once and pass: `argListAtLeaf` and `callAtLeaf`.

Sketch (minimal changes):

```kotlin
fun findExpressionAtCaret(editor: Editor, file: PsiFile): PyExpression? {
    val offset = editor.caretModel.offset
    val leaf = file.findElementAt(offset) ?: return null

    // Compute once
    val argListAtLeaf = PsiTreeUtil.getParentOfType(leaf, PyArgumentList::class.java)
    val callAtLeaf = PsiTreeUtil.getParentOfType(leaf, PyCallExpression::class.java)

    PsiTreeUtil.getParentOfType(leaf, PyKeywordArgument::class.java)?.let { kw ->
        val value = kw.valueExpression
        if (value != null && !PsiTreeUtil.isAncestor(value, leaf, false)) return value
    }

    argumentRootAtCaret(leaf)?.let { candidate ->
        if (isInsideFunctionCallArgument(candidate)) return candidate
    }

    val candidates = collectCandidates(leaf, file)
    return chooseBest(candidates, leaf, argListAtLeaf, callAtLeaf)
}

private fun chooseBest(
    c: CaretCandidates,
    leaf: PsiElement,
    argListAtLeaf: PyArgumentList?,
    callAtLeaf: PyCallExpression?
): PyExpression? {
    if (c.string != null && isInsideFunctionCallArgument(c.string)) return c.string
    if (c.parenthesized != null && isInsideFunctionCallArgument(c.parenthesized)) return c.string ?: c.parenthesized

    val inArg = listOfNotNull(c.call, c.other).any { isInsideFunctionCallArgument(it) }
    if (inArg) {
        val argList = argListAtLeaf
        val call = callAtLeaf
        if (argList != null && call != null) {
            val args = argList.arguments
            val arg = args.firstOrNull { it == leaf || PsiTreeUtil.isAncestor(it, leaf, false) }
            if (arg is PyKeywordArgument) arg.valueExpression?.let { return it }
            else if (arg is PyExpression) return arg
        }
        return c.call ?: c.other
    }
    return c.call ?: c.parenthesized ?: c.string ?: c.other
}
```

Impact:

- Avoids two repeated `getParentOfType` calls per hot caret selection.

#### 3) Minor: cache `PsiTreeUtil.isAncestor` checks inside tight loops

In `chooseBest` and `argumentRootAtCaret`, you run `isAncestor` multiple times for the same `(ancestor, leaf)` pair.

- You can cache `val isArgAncestor = { a: PsiElement -> a == leaf || PsiTreeUtil.isAncestor(a, leaf, false) }` and reuse
  the lambda.
- Or, precompute `val leafRange = leaf.textRange` and compare against candidate ranges where safe.

---

### File: `ContainerTyping.kt`

#### 4) Compute `ContainerContext` once per operation and pass it through

Current flow:

- `analyzeContainer(element)` computes `(container, pos, kind)`.
- `findEnclosingContainer(element)` calls `analyzeContainer(element)` and returns `container`.
- `locatePositionInContainer(element)` calls `analyzeContainer(element)` again and returns `pos`.
- `expectedCtorForContainerItem(pos, container, ctx)` rebuilds `ContainerContext` and re‑classifies `container`.

What to cache/pass:

- A single `ContainerContext` instance per outer request, containing `container`, `pos`, and `kind`.

Minimal refactor:

```kotlin
// Public entry
fun tryContainerItemCtor(element: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
    val cc = analyzeContainer(element) ?: return null
    return expectedCtorFor(ctx, cc)
}

// Drop the extra classify in expectedCtorForContainerItem by removing the helper
private fun expectedCtorFor(
    ctx: TypeEvalContext,
    cc: ContainerContext
): ExpectedCtor? {
    val info = ExpectedTypeInfo.getExpectedTypeInfo(cc.container, ctx) ?: return null
    // ... unchanged
}
```

Optional safety: If you still need `findEnclosingContainer`/`locatePositionInContainer` for other call sites, provide
overloads that accept a precomputed `ContainerContext` to prevent hidden recomputation:

```kotlin
private fun findEnclosingContainer(cc: ContainerContext): PyExpression = cc.container
private fun locatePositionInContainer(cc: ContainerContext): ContainerPos = cc.pos
```

#### 5) Avoid re‑classifying the same container

- `expectedCtorForContainerItem` currently calls `classifyContainer(container)` again even though `analyzeContainer`
  just computed it. With the change above, you always reuse `cc.kind`.

#### 6) Micro: cache lookups inside `locateElementPosition`

- For tuples and dicts you call `isAncestorOrSelfOf` repeatedly inside a search.
- Pre-grab `val elements = container.elements` and iterate once; for dicts, lift
  `PsiTreeUtil.getParentOfType(element, PyKeyValueExpression::class.java)` out so it’s called at most once.

Sketch:

```kotlin
private fun locateElementPosition(container: PyExpression, element: PyExpression): ContainerPos? = when (container) {
    is PyTupleExpression -> {
        val elems = container.elements
        val idx = elems.indexOfFirst { it.isAncestorOrSelfOf(element) }
        if (idx >= 0) ContainerPos.TupleItem(idx) else null
    }
    is PyDictLiteralExpression -> {
        val kv = PsiTreeUtil.getParentOfType(element, PyKeyValueExpression::class.java) ?: return null
        when {
            kv.key.isAncestorOrSelfOf(element) -> ContainerPos.DictKey
            kv.value?.isAncestorOrSelfOf(element) == true -> ContainerPos.DictValue
            else -> null
        }
    }
    else -> /* as before */ null
}
```

---

### Quick checklist of cache candidates

- `CaretSelection.isInsideFunctionCallArgument`
    - Cache: `PyArgumentList`, `PyCallExpression` for the starting `expr`.
    - Cache: results of `isExcludedAssignment(call)` and `isExcludedReturn(call)` per `call`.
- `CaretSelection.findExpressionAtCaret`
    - Cache once per caret: `argListAtLeaf`, `callAtLeaf` and pass into `chooseBest`.
    - Optionally cache repeated `isAncestor` checks for the same `(candidate, leaf)` pair.
- `ContainerTyping.analyzeContainer`
    - Cache: return `ContainerContext` and pass through instead of recomputing in `findEnclosingContainer`,
      `locatePositionInContainer`, and `expectedCtorForContainerItem`.
    - Cache inside `locateElementPosition` the `PyKeyValueExpression` parent and the array of elements.

---

### Why these are safe

- All caches are scoped to a single PSI snapshot (the current read action). PSI is immutable within a read action; after
  changes, your methods will be re‑entered and recompute fresh values.
- Where a longer‑lived cache is shown (WeakHashMap keyed by `PyCallExpression`), `WeakHashMap` avoids retaining PSI
  after it’s invalidated.

---

### Expected wins

- Fewer `PsiTreeUtil.getParentOfType` walks in the caret hot path (often one of the hottest activities during
  typing/selection‑driven intentions).
- Reduced repeated classification in container typing.
- Cleaner data flow: functions receive what they actually need (a `ContainerContext`) instead of reconstructing it.

If you want, I can turn the sketches into minimal diffs tailored to your exact code style and nullability preferences.