### Goal

You want the *Wrap with…* intentions to:

1. Prefer “higher‑value” constructors when a union mixes builtin / stdlib / 3rd‑party / own types.
2. Only offer the **best bucket** that is actually available.
3. If several candidates exist in that best bucket, offer a chooser popup.
4. If the current value is *already* of the best bucket type, don’t show the intention at all.

Below is a concrete behavior spec, test plan, and an implementation sketch that should fit into your existing
`ExpectedTypeAnalyzer` → `WrapPlan` → `WrapApplier` / `WrapPreview` design.

---

### Conceptual model

#### 1. Buckets and precedence

Define an enum expressing the priority you described:

```kotlin
enum class TypeBucket(val priority: Int) {
    BUILTIN(0),     // lowest
    STDLIB(1),
    THIRDPARTY(2),
    OWN(3);         // highest
}
```

Higher `priority` = more desirable for wrapping.

We need a function that assigns each *candidate constructor* to one bucket:

```kotlin
fun classifyBucket(symbol: PsiNamedElement?): TypeBucket? {
    if (symbol == null) return null

    // 1) Builtins (Python example, adjust to your PSI helpers)
    if (PyTypeIntentions.isBuiltin(symbol)) return TypeBucket.BUILTIN

    // 2) Stdlib
    if (PyTypeIntentions.isStdlib(symbol)) return TypeBucket.STDLIB

    // 3) Third‑party
    if (PyTypeIntentions.isThirdParty(symbol)) return TypeBucket.THIRDPARTY

    // 4) Own code (fallback if resolvable and none of the above)
    return TypeBucket.OWN
}
```

You probably already have most of these heuristics in your *Introduce custom type* / *wrap* utilities (e.g., via module
roots, SDK roots, `site-packages` paths). Reuse them if possible.

#### 2. Relating buckets to a union

Given a list of union candidates:

```kotlin
data class WrapCtorCandidate(
    val name: String,
    val element: PsiNamedElement?,
)

fun groupByBucket(candidates: List<WrapCtorCandidate>): Map<TypeBucket, List<WrapCtorCandidate>> {
    return candidates
        .mapNotNull { ctor ->
            val bucket = classifyBucket(ctor.element) ?: return@mapNotNull null
            bucket to ctor
        }
        .groupBy({ it.first }, { it.second })
}
```

Then compute the *best bucket* present:

```kotlin
fun bestBucket(groups: Map<TypeBucket, List<WrapCtorCandidate>>): TypeBucket? =
    groups.keys.maxByOrNull { it.priority }
```

Once you know the best bucket, the `ExpectedTypeAnalyzer` can decide:

- If **no bucket** exists → no wrap intention.
- If **best bucket** exists and has:
    - **1 candidate** → auto `WrapPlan.Single`.
    - **>1 candidates** → `WrapPlan.UnionChoice` with only those candidates; UI shows popup.

#### 3. “Already highest bucket” check

You also want: *If the given value is already of the highest available bucket, do not suggest a wrap.*

Interpretation that works well with your current union / mismatch logic:

- You’re only in wrap logic when there is a **mismatch** between actual type and expected type.
- Among all candidate constructors (from expected type), determine the best bucket.
- If the **actual type** already belongs to that same bucket, you don’t want to wrap it with a *lower or equal* bucket
  alternative.

Pseudocode in analyzer:

```kotlin
fun shouldOfferWrap(
    actualType: PyType?,
    unionCtors: List<WrapCtorCandidate>
): Boolean {
    val groups = groupByBucket(unionCtors)
    val topBucket = bestBucket(groups) ?: return false

    val actualBucket = classifyBucket(resolveSymbolFromType(actualType))

    // If actual is already at least as “good” as the top candidate, no wrap
    if (actualBucket != null && actualBucket.priority >= topBucket.priority) {
        return false
    }

    return true
}
```

You might choose a slightly stricter rule (e.g. only suppress when `actualBucket == topBucket`), but using `>=` handles
edge cases where “own” or “thirdparty” types are considered strictly better than stdlib/builtin.


---

### Behavior examples (edge cases)

Assume `OWN > THIRDPARTY > STDLIB > BUILTIN`.

1. **Expected**: `Union[int, pathlib.Path]`  
   **Actual**: `str`  
   Buckets: `int` → builtin, `Path` → stdlib.  
   Best bucket = `STDLIB` → only `Path`.  
   → Offer *only*: `Wrap with Path()`.

2. **Expected**: `Union[str, MyId]`  
   **Actual**: `Path`  
   Buckets: `str` → builtin, `MyId` → own.  
   Best bucket = `OWN` → only `MyId`.  
   Actual bucket = `STDLIB`.  `STDLIB.priority < OWN.priority`.  
   → Suggest wrap with `MyId()`.

3. **Expected**: `Union[StrAlias, MyId]`  
   `StrAlias = NewType("StrAlias", str)` (own), `MyId` (own).  
   **Actual**: `str` (builtin).  
   Buckets (candidates): both own (StrAlias, MyId).  
   Best bucket = `OWN`, 2 candidates.  
   → Show chooser popup with `Wrap with StrAlias()` and `Wrap with MyId()`.

4. **Expected**: `Union[StdId, MyId]`  
   `StdId` comes from stdlib; `MyId` is own.  
   **Actual**: `StdId`. Actual bucket `STDLIB`, best bucket among union = `OWN` (because of `MyId`).  
   `STDLIB.priority < OWN.priority` → still allow a wrap.  
   → Suggest `Wrap with MyId()`.

5. **Expected**: `Union[MyId, OtherId]` (both own)  
   **Actual**: `MyId`.  
   Best bucket = `OWN`. Actual bucket = `OWN` (>=).  
   → **No wrap** (already highest bucket).

---

### Suggested test cases

Assume tests live under `.../intention/wrap/` next to `GenericTest`, `ListWrappingTest`, etc. Examples are in Kotlin
test style using `myFixture`.

#### 1. Builtin vs stdlib

```kotlin
fun testUnionBuiltinAndStdlib_PrefersStdlib() {
    myFixture.configureByText(
        "a.py",
        """
        from pathlib import Path
        
        def f(p: int | Path):
            f(<caret>"abc")
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Wrap with Path()")
    myFixture.launchAction(intention)

    myFixture.checkResult(
        """
        from pathlib import Path
        
        def f(p: int | Path):
            f(Path("abc"))
        """.trimIndent()
    )
}
```

#### 2. Stdlib vs own – own wins

```kotlin
fun testUnionStdlibAndOwn_PrefersOwn() {
    myFixture.configureByText(
        "a.py",
        """
        from pathlib import Path
        from mypkg.ids import PathId
        
        def f(p: Path | PathId):
            f(<caret>"abc")
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Wrap with PathId()")
    myFixture.launchAction(intention)

    myFixture.checkResult(
        """
        from pathlib import Path
        from mypkg.ids import PathId
        
        def f(p: Path | PathId):
            f(PathId("abc"))
        """.trimIndent()
    )
}
```

#### 3. Multiple own candidates → chooser popup

Use your existing fake popup host (`WrapWithExpectedTypeIntentionHooks.popupHost`) to simulate selection.

```kotlin
fun testUnionMultipleOwn_ShowsChooser() {
    WrapWithExpectedTypeIntentionHooks.withFakePopupSelection("CloudId") {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            CloudId = NewType("CloudId", int)
            
            def f(u: UserId | CloudId):
                f(<caret>1)
            """.trimIndent()
        )

        myFixture.doHighlighting()

        // Both items should be available in the chooser.
        val allIntentions = myFixture.availableIntentions.map { it.text }
        assertTrue(allIntentions.contains("Wrap with UserId()"))
        assertTrue(allIntentions.contains("Wrap with CloudId()"))

        // Fake popup selects CloudId.
        val intention = myFixture.findSingleIntention("Wrap with expected type…")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from typing import NewType
            UserId = NewType("UserId", int)
            CloudId = NewType("CloudId", int)
            
            def f(u: UserId | CloudId):
                f(CloudId(1))
            """.trimIndent()
        )
    }
}
```

*(You may already have a similar chooser test; adapt names accordingly.)*

#### 4. No suggestion when already highest bucket

```kotlin
fun testAlreadyHighestBucket_NoWrap() {
    myFixture.configureByText(
        "a.py",
        """
        from typing import NewType
        UserId = NewType("UserId", int)
        CloudId = NewType("CloudId", int)
        
        def f(u: UserId | CloudId):
            uid = <caret>UserId(1)
            f(uid)
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.availableIntentions.find { it.text.contains("Wrap with") }
    assertNull("No wrap intention must be available for an OWN bucket value", intention)
}
```

#### 5. Third‑party vs stdlib

```kotlin
fun testUnionThirdPartyAndStdlib_PrefersThirdParty() {
    myFixture.configureByText(
        "a.py",
        """
        from pathlib import Path
        from pydantic import HttpUrl
        
        def f(u: Path | HttpUrl):
            f(<caret>"https://example.com")
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Wrap with HttpUrl()")
    myFixture.launchAction(intention)

    myFixture.checkResult(
        """
        from pathlib import Path
        from pydantic import HttpUrl
        
        def f(u: Path | HttpUrl):
            f(HttpUrl("https://example.com"))
        """.trimIndent()
    )
}
```

#### 6. Preview behavior when chooser is needed

You already have in `WrapPreview` something like:

```kotlin
is WrapPlan.UnionChoice -> IntentionPreviewInfo.EMPTY
```

Add a test asserting that `generatePreview` returns `EMPTY` when multiple candidates from a bucket exist (i.e., popup
case) so your preview infra stays consistent.


---

### Implementation path

Below is a minimal‑disruption way to layer this onto your existing design.

#### 1. Extend model for buckets

In `.../intention/wrap/util` or `.../intention/shared`:

```kotlin
enum class TypeBucket(val priority: Int) { BUILTIN(0), STDLIB(1), THIRDPARTY(2), OWN(3) }

data class BucketedCtor(
    val name: String,
    val element: PsiNamedElement?,
    val bucket: TypeBucket,
)
```

If you already have `ExpectedCtor` or similar, extend it with `bucket: TypeBucket` instead of creating a parallel type.

#### 2. Implement `classifyBucket`

In a small, testable util (e.g. `TypeBucketClassifier.kt`):

```kotlin
object TypeBucketClassifier {
    fun bucketFor(symbol: PsiNamedElement?): TypeBucket? {
        if (symbol == null) return null

        if (PyTypeIntentions.isBuiltin(symbol)) return TypeBucket.BUILTIN
        if (PyTypeIntentions.isStdlib(symbol)) return TypeBucket.STDLIB
        if (PyTypeIntentions.isThirdParty(symbol)) return TypeBucket.THIRDPARTY
        return TypeBucket.OWN
    }
}
```

You can implement `isStdlib` / `isThirdParty` via:

- Inspecting the containing file’s virtual file path against SDK roots.
- Treating `site-packages` (or venv library roots) as third‑party.
- Treating project content roots as *own*.

This is orthogonal to the wrap logic and can be unit‑tested separately.

#### 3. Enhance `UnionCandidates` (or equivalent)

Where you currently build the list of union constructor candidates (e.g. `UnionCandidates.collectUnionCtorCandidates`),
wrap them with buckets:

```kotlin
val bucketed = rawCandidates.mapNotNull { ctor ->
    val bucket = TypeBucketClassifier.bucketFor(ctor.element) ?: return@mapNotNull null
    BucketedCtor(ctor.name, ctor.element, bucket)
}
```

Group and pick the best bucket:

```kotlin
val groupedByBucket = bucketed.groupBy { it.bucket }
val bestBucket = groupedByBucket.keys.maxByOrNull { it.priority } ?: return null
val bestBucketCtors = groupedByBucket.getValue(bestBucket)
```

Then construct the `WrapPlan`:

```kotlin
return when (bestBucketCtors.size) {
    0 -> null
    1 -> WrapPlan.Single(element, bestBucketCtors.single().name, bestBucketCtors.single().element)
    else -> WrapPlan.UnionChoice(
        element,
        bestBucketCtors.map { WrapPlan.UnionChoice.Ctor(it.name, it.element) }
    )
}
```

#### 4. Integrate “already highest bucket” into `ExpectedTypeAnalyzer`

In `ExpectedTypeAnalyzer` where you now compute a `WrapPlan?` for a type mismatch:

1. Compute `bucketed` union candidates and `bestBucket` as above.
2. Resolve the **actual type** symbol and its bucket.
3. If `actualBucket != null && actualBucket.priority >= bestBucket.priority`, return `null` (no intention).
4. Otherwise, proceed to build `WrapPlan.Single` or `WrapPlan.UnionChoice`.

That keeps the “no wrap if highest bucket already” decision in the analysis layer where it belongs.

#### 5. UI / chooser integration

Your existing popup host (`PopupHost` / `JbPopupHost`, plus `WrapWithExpectedTypeIntentionHooks`) already supports the
union chooser. You just change:

- The **set of candidates** passed in (only those from `bestBucket`).

No UI change is required otherwise.

#### 6. Tests wiring

Add a new test class, e.g. `BucketPriorityTest.kt`, next to other wrap tests:

- Focus purely on bucket selection behavior.
- Use tiny Python snippets with unions that combine builtin / stdlib / third‑party / own types.
- Add one “no intention” test for the “already highest bucket” behavior.

Re‑use `WrapWithExpectedTypeIntention` directly; no need for a separate fixture type.


---

### Summary

*Behavior*: among all possible “Wrap with …” constructors from a union, classify each into one of
`{builtin, stdlib, thirdparty, own}`; find the best bucket by priority; if the current value already lives in at least
that bucket, don’t offer the intention; otherwise, offer:

- a single auto wrap if that bucket has 1 candidate;
- a chooser popup if it has multiple candidates.

The proposal above gives you:

- A simple, testable `TypeBucketClassifier`.
- Minimal changes to your existing `ExpectedTypeAnalyzer` / `UnionCandidates` and `WrapPlan`.
- Concrete tests that will lock in the desired bucket precedence and “no wrap when already at top bucket” rule.