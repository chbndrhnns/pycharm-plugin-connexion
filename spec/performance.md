### Performance Problems Analysis

After analyzing the codebase, I've identified several performance issues, primarily in the `isAvailable()` methods of intention actions. These methods are called very frequently by the IDE (on every caret movement, keystroke, etc.), so they must be as lightweight as possible.

---

### Critical Performance Issues

#### 1. Expensive `TypeEvalContext.codeAnalysis()` in `isAvailable()`

**Affected files:**
- `PopulateArgumentsIntention.kt` (line 54)
- `WrapWithExpectedTypeIntention.kt` (line 65)
- `UnwrapToExpectedTypeIntention.kt` (line 81)
- `IntroduceCustomTypeFromStdlibIntention.kt` (line 80)

**Problem:** Creating a `TypeEvalContext` is expensive as it initializes type inference machinery.

**Solution:** 
- Use fast, syntactic checks first (e.g., check if element is the right type, check text patterns)
- Only create `TypeEvalContext` after all cheap checks pass
- Consider caching the analysis result using `editor.putUserData()` (which some intentions already do, but they still compute it in `isAvailable()`)

```kotlin
// Bad - creates context immediately
override fun isAvailable(...): Boolean {
    val ctx = TypeEvalContext.codeAnalysis(project, file)
    return service.isAvailable(call, ctx)
}

// Better - defer expensive work
override fun isAvailable(...): Boolean {
    if (!cheapSyntacticCheck(editor, file)) return false
    val ctx = TypeEvalContext.codeAnalysis(project, file)
    return service.isAvailable(call, ctx)
}
```

---

#### 2. Reference Resolution in `isAvailable()`

**Affected files:**
- `CallableToProtocolIntention.kt` (lines 39-48) - calls `multiResolve()` and `resolve()`
- `ToggleTypeAliasIntention.kt` (lines 40, 104) - calls `reference.resolve()`

**Problem:** Reference resolution is expensive as it may traverse the entire project to find definitions.

**Solution:**
- Use textual/syntactic checks first (fast path)
- `CallableToProtocolIntention` already has a fast path (line 35), but the resolution fallback is still called frequently
- Consider making the intention available based on syntax alone, then validate during `invoke()`

---

#### 3. `PsiTreeUtil.hasErrorElements()` Full Tree Traversal

**Affected file:** `IntroduceCustomTypeFromStdlibIntention.kt` (line 64)

**Problem:** `PsiTreeUtil.hasErrorElements(pyFile)` traverses the entire PSI tree to check for errors.

**Solution:**
- Remove this check from `isAvailable()` or limit it to a smaller subtree
- Handle errors gracefully in `invoke()` instead
- Use `PsiTreeUtil.hasErrorElements(targetElement)` on a smaller scope if needed

---

#### 4. `DaemonCodeAnalyzerImpl.getHighlights()` in `isAvailable()`

**Affected file:** `IntroduceCustomTypeFromStdlibIntention.kt` (line 126)

**Problem:** Getting all highlights and iterating through them is expensive.

**Solution:**
- Move this check to `invoke()` or show a warning/error after the action
- If needed in `isAvailable()`, cache the result or use a more targeted approach

---

#### 5. Object Creation in `isAvailable()`

**Affected files:**
- `WrapWithExpectedTypeIntention.kt` (line 64) - creates `ExpectedTypeAnalyzer(project)` every call
- `PyParameterAnalyzer.kt` (line 75) - creates `PyDataclassFieldExtractor()` every call

**Problem:** Creating new objects in frequently-called methods adds GC pressure.

**Solution:**
- Make these objects instance fields or use object pooling
- `WrapWithExpectedTypeIntention` should store `ExpectedTypeAnalyzer` as an instance field

```kotlin
// Bad
override fun isAvailable(...): Boolean {
    val analyzer = ExpectedTypeAnalyzer(project)  // Created every time
    ...
}

// Better
private val analyzer = ExpectedTypeAnalyzer()  // Reuse instance
```

---

#### 6. Duplicate Expensive Calls

**Affected file:** `PyParameterAnalyzer.kt` (line 83)

**Problem:** `getMissingParameters()` is called in both `isAvailable()` (line 54) and `isRecursiveApplicable()` (line 83), duplicating expensive work.

**Solution:**
- Cache the result of `getMissingParameters()` or restructure to avoid duplicate calls
- Pass the already-computed list to `isRecursiveApplicable()`

---

#### 7. Duplicate Service Lookups

**Affected file:** `CallableToProtocolIntention.kt` (lines 77-78)

**Problem:** `InjectedLanguageManager.getInstance(project)` is called twice in `findSubscription()`.

**Solution:**
```kotlin
// Bad
if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) {
    val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset)
}

// Better
val injectedManager = InjectedLanguageManager.getInstance(project)
if (!injectedManager.isInjectedFragment(file)) {
    val injected = injectedManager.findInjectedElementAt(file, offset)
}
```

---

### General Recommendations

1. **Follow the "fast path first" pattern:** Always perform cheap syntactic checks before expensive semantic analysis.

2. **Use `editor.putUserData()` wisely:** Cache computed plans/results between `isAvailable()` and `invoke()`, but don't compute expensive data just for caching.

3. **Consider lazy evaluation:** Defer expensive computations until they're actually needed (in `invoke()` rather than `isAvailable()`).

4. **Profile with IntelliJ's built-in tools:** Use "Help > Diagnostic Tools > Activity Monitor" to identify slow intentions.

5. **Implement `DumbAware` carefully:** If your intention works in dumb mode, ensure `isAvailable()` doesn't use indices.

6. **Batch similar checks:** If multiple intentions need similar analysis, consider sharing computation through a service.
