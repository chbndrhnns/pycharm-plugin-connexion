### Refactoring Opportunities

This document identifies opportunities for refactoring to make the codebase cleaner, clearer, and better structured.

---

### Production Code

#### 1. Duplicate `isBuiltinName` Functions

**Files:**
- `src/main/kotlin/.../intention/populate/PopulateArgumentsService.kt` (lines 167-172)
- `src/main/kotlin/.../intention/populate/PyValueGenerator.kt` (lines 159-166)

**Issue:**
Two nearly identical `isBuiltinName` functions exist that check if a type name is a Python builtin:

```kotlin
// PopulateArgumentsService.kt
private fun isBuiltinName(name: String): Boolean {
    val lower = name.lowercase()
    return lower in setOf(
        "int", "str", "float", "bool", "bytes", "list", "dict", "set", "tuple", "range", "complex"
    )
}

// PyValueGenerator.kt
private fun isBuiltinName(name: String): Boolean {
    return when (name.lowercase()) {
        PyNames.TYPE_INT, PyNames.TYPE_STR, PyNames.FLOAT, "bool", PyNames.BYTES,
        "list", PyNames.DICT, PyNames.SET, PyNames.TUPLE, "range", "complex" -> true
        else -> false
    }
}
```

**Suggested Refactoring:**
Extract to a shared utility object (e.g., `PyBuiltinNames` or add to existing `PyTypeIntentions`):

```kotlin
object PyBuiltinNames {
    private val BUILTIN_NAMES = setOf(
        "int", "str", "float", "bool", "bytes", 
        "list", "dict", "set", "tuple", "range", "complex"
    )
    
    fun isBuiltin(name: String): Boolean = name.lowercase() in BUILTIN_NAMES
}
```

---

#### 2. Duplicate `CONTAINERS` Constant

**Files:**
- `src/main/kotlin/.../intention/shared/PyTypeIntentions.kt` (line 40)
- `src/main/kotlin/.../intention/wrap/PyWrapHeuristics.kt` (line 19)

**Issue:**
The same constant is defined in two places:

```kotlin
// PyTypeIntentions.kt
val CONTAINERS = setOf("list", "set", "tuple", "dict")

// PyWrapHeuristics.kt
val CONTAINERS = setOf("list", "set", "tuple", "dict")
```

**Suggested Refactoring:**
Keep the constant in one location (preferably `PyTypeIntentions` as the facade) and reference it from `PyWrapHeuristics`. Note that `PyWrapHeuristics` already delegates some methods to `PyTypeIntentions`, so this would be consistent:

```kotlin
// PyWrapHeuristics.kt - remove local definition, use:
val CONTAINERS get() = PyTypeIntentions.CONTAINERS
```

---

#### 3. Large `ExpectedTypeInfo.kt` with Multiple Responsibilities

**File:** `src/main/kotlin/.../intention/shared/ExpectedTypeInfo.kt` (434 lines)

**Issue:**
This file handles multiple distinct responsibilities:
- Type name computation and rendering
- Expected type resolution from various PSI contexts (assignments, returns, parameters, yields, lambdas, etc.)
- Constructor name extraction
- Parameter type info resolution for function calls and class constructors

**Suggested Refactoring:**
Split into focused modules:

1. **`TypeNameResolver.kt`** - Type name computation and constructor name extraction
   - `computeDisplayTypeNames()`
   - `expectedCtorName()`
   - `canonicalCtorName()`
   - `elementDisplaysAsCtor()`

2. **`ExpectedTypeResolver.kt`** - Expected type resolution from PSI context
   - `getExpectedTypeInfo()` and its helper methods for different contexts
   - `doGetExpectedTypeInfo()` (the large switch-like method)

3. **`ParameterTypeResolver.kt`** - Function/class parameter type resolution
   - `resolveParamTypeInfo()`
   - `functionParamTypeInfo()`
   - `classFieldTypeInfo()`
   - `positionalFieldTypeInfo()`

The existing `ExpectedTypeInfo` object could become a facade delegating to these focused modules.

---

#### 4. Similar Intention Boilerplate

**Files:**
- `src/main/kotlin/.../intention/WrapWithExpectedTypeIntention.kt`
- `src/main/kotlin/.../intention/UnwrapToExpectedTypeIntention.kt`
- `src/main/kotlin/.../intention/IntroduceCustomTypeFromStdlibIntention.kt`
- `src/main/kotlin/.../intention/populate/PopulateArgumentsIntention.kt`

**Issue:**
These intentions share common patterns:
- Settings check in `isAvailable()`: `if (!PluginSettingsState.instance().state.enableXxxIntention) return false`
- `PLAN_KEY` pattern for caching analysis results between `isAvailable()` and `invoke()`
- Icon implementation: `override fun getIcon(...): Icon = AllIcons.Actions.IntentionBulb`
- Preview handling patterns
- Dynamic `lastText` field for intention text

**Suggested Refactoring:**
Create an abstract base class:

```kotlin
abstract class BaseTypedIntention<P : Any> : IntentionAction, HighPriorityAction, DumbAware, Iconable {
    
    protected abstract val planKey: Key<P>
    protected abstract val settingsEnabled: () -> Boolean
    protected abstract val defaultText: String
    
    protected var lastText: String = defaultText
    
    override fun getIcon(flags: Int): Icon = AllIcons.Actions.IntentionBulb
    
    override fun getText(): String = lastText
    
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!settingsEnabled()) {
            editor.putUserData(planKey, null)
            lastText = defaultText
            return false
        }
        // ... common star-argument check, etc.
        return doIsAvailable(project, editor, file)
    }
    
    protected abstract fun doIsAvailable(project: Project, editor: Editor, file: PsiFile): Boolean
    // ...
}
```

---

#### 5. Unclear Boundaries Between `PyWrapHeuristics` and `PyTypeIntentions`

**Files:**
- `src/main/kotlin/.../intention/wrap/PyWrapHeuristics.kt`
- `src/main/kotlin/.../intention/shared/PyTypeIntentions.kt`

**Issue:**
`PyWrapHeuristics` delegates several methods directly to `PyTypeIntentions`:

```kotlin
// PyWrapHeuristics.kt (lines 140-147)
fun getWrapperCallInfo(element: PyExpression): WrapperInfo? =
    PyTypeIntentions.getWrapperCallInfo(element)

fun expectedCtorName(expr: PyExpression, ctx: TypeEvalContext): String? =
    PyTypeIntentions.expectedCtorName(expr, ctx)

fun expectedItemCtorsForContainer(expr: PyExpression, ctx: TypeEvalContext): List<ExpectedCtor> =
    PyTypeIntentions.expectedItemCtorsForContainer(expr, ctx)
```

This creates confusion about which class to use and adds unnecessary indirection.

**Suggested Refactoring:**
Options:
1. **Merge**: Move wrap-specific heuristics into `PyTypeIntentions` (or a dedicated `WrapTypeAnalysis` module)
2. **Clarify boundaries**: `PyTypeIntentions` handles general type queries; `PyWrapHeuristics` handles wrap-specific logic (container checks, already-wrapped detection). Remove the delegating methods and have callers use `PyTypeIntentions` directly for those operations.
3. **Composition**: Have `PyWrapHeuristics` take `PyTypeIntentions` as a dependency rather than calling static methods.

---

### Test Code

#### 6. Shared Python SDK Setup Logic

**Files:**
- `src/test/kotlin/fixtures/TestBase.kt`
- `src/test/kotlin/fixtures/HeavyTestBase.kt`

**Issue:**
Both test base classes contain similar Python SDK setup logic:

```kotlin
// TestBase.kt (lines 14-19, 44-49)
System.setProperty(
    "idea.python.helpers.path",
    Paths.get(PathManager.getHomePath(), "plugins", "python-ce", "helpers").toString()
)
// ...
val sdk = PythonMockSdk.create(LanguageLevel.PYTHON311, myFixture.tempDirFixture.getFile("/")!!)
Disposer.register(testRootDisposable) {
    runWriteAction {
        ProjectJdkTable.getInstance().removeJdk(sdk)
    }
}

// HeavyTestBase.kt (lines 64-91) - similar but with slight variations
```

**Suggested Refactoring:**
Extract shared setup into a utility:

```kotlin
object PythonTestSetup {
    fun configurePythonHelpers() {
        System.setProperty(
            "idea.python.helpers.path",
            Paths.get(PathManager.getHomePath(), "plugins", "python-ce", "helpers").toString()
        )
    }
    
    fun createAndRegisterSdk(
        root: VirtualFile,
        disposable: Disposable,
        languageLevel: LanguageLevel = LanguageLevel.PYTHON311
    ): Sdk {
        val sdk = PythonMockSdk.create(languageLevel, root)
        Disposer.register(disposable) {
            runWriteAction {
                ProjectJdkTable.getInstance().removeJdk(sdk)
            }
        }
        return sdk
    }
}
```

---

#### 7. Test Helper Functions Organization

**Files in `src/test/kotlin/fixtures/`:**
- `IntentionHelpers.kt` - intention test utilities
- `InspectionHelpers.kt` - inspection test utilities  
- `WrapHelpers.kt` - wrap-specific test utilities
- `PopulateHelpers.kt` - populate-specific test utilities
- `SettingsTestUtils.kt` - settings manipulation utilities

**Issue:**
The helper functions are spread across multiple files. While some separation is logical, there's potential for:
- Consolidation of small, related utilities
- Clearer naming conventions
- Better discoverability

**Suggested Refactoring:**
Options:
1. **Consolidate by concern**: Group all intention-related helpers (including wrap/populate-specific ones) into `IntentionTestHelpers.kt`
2. **Create a test DSL**: Build a fluent API for common test patterns:

```kotlin
// Example DSL usage
intentionTest("a.py") {
    before("""
        from pathlib import Path
        a: str = Path(<caret>"val")
    """)
    after("""
        from pathlib import Path
        a: str = str(Path("val"))
    """)
    intention("Wrap with str()")
}
```

3. **Document the helpers**: Add KDoc to each helper file explaining its purpose and when to use it

---

### Priority Recommendations

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| High | Duplicate `isBuiltinName` | Low | Reduces maintenance burden |
| High | Duplicate `CONTAINERS` | Low | Single source of truth |
| Medium | Split `ExpectedTypeInfo.kt` | Medium | Improves readability and testability |
| Medium | Intention boilerplate | Medium | Reduces code duplication |
| Low | `PyWrapHeuristics` boundaries | Medium | Clarifies architecture |
| Low | Test SDK setup | Low | Minor DRY improvement |
| Low | Test helpers organization | Low | Improves discoverability |

---

### Notes

- All file paths are relative to `src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/` for production code
- Test file paths are relative to `src/test/kotlin/`
- Line numbers are approximate and may shift as the codebase evolves
