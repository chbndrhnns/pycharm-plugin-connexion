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

### Long File Analysis

The following files were analyzed for potential splitting. Files are listed by line count.

#### 8. `TargetDetector.kt` (547 lines) - **Recommended to Split**

**File:** `src/main/kotlin/.../intention/customtype/TargetDetector.kt`

**Issue:**
This is the longest file in the codebase. It contains a single class with 20+ private helper methods covering multiple detection strategies:
- Annotation detection (`tryFromAnnotation`)
- Variable definition detection (`tryFromVariableDefinition`, `createTargetFromDefinition`)
- F-string reference detection (`tryFromFStringReference`)
- Expression detection (`tryFromExpression`) - the largest method
- Type determination helpers (`determineBuiltinType`, `detectFromLiteral`, `normalizeName`)
- Context detection (`detectKeywordArgumentName`, `detectAssignmentName`, `detectReturnAnnotationInfo`, `detectParameterDefaultInfo`, `detectDictionaryKeyInfo`)
- Validation helpers (`isAlreadyWrappedInCustomType`, `isEnumAssignment`, `hasConflictingDataclassType`, `isDataclassField`, `isImplicitReturnOfFunctionCall`)

**Suggested Refactoring:**
Split into focused detector classes:

1. **`AnnotationTargetDetector.kt`** - Detection from annotations and variable definitions
   - `tryFromAnnotation()`
   - `tryFromVariableDefinition()`
   - `createTargetFromDefinition()`
   - `createTargetFromParameter()`

2. **`ExpressionTargetDetector.kt`** - Detection from expressions
   - `tryFromExpression()` and its helpers
   - `detectKeywordArgumentName()`
   - `detectAssignmentName()`
   - `detectReturnAnnotationInfo()`
   - `detectParameterDefaultInfo()`
   - `detectDictionaryKeyInfo()`

3. **`TargetValidation.kt`** - Validation utilities
   - `isAlreadyWrappedInCustomType()`
   - `isEnumAssignment()`
   - `hasConflictingDataclassType()`
   - `isImplicitReturnOfFunctionCall()`
   - `isAssignmentTargetOfImplicitFunctionCall()`

4. **`TypeNameNormalizer.kt`** - Type name utilities
   - `normalizeName()`
   - `isTypingSymbol()`
   - `determineBuiltinType()`
   - `detectFromLiteral()`
   - `findExpressionMatchingBuiltin()`

The main `TargetDetector` class would become a coordinator delegating to these focused classes.

---

#### 9. `strategies.kt` (356 lines) - **Recommended to Split**

**File:** `src/main/kotlin/.../intention/wrap/strategies.kt`

**Issue:**
This file contains 7 different strategy classes all in one file:
- `WrapStrategy` (interface)
- `OuterContainerStrategy` (~85 lines)
- `ContainerItemStrategy` (~30 lines)
- `UnionStrategy` (~60 lines)
- `GenericCtorStrategy` (~30 lines)
- `UnwrapStrategy` (object, ~20 lines)
- `EnumStrategy` (~45 lines)
- `EnumMemberDefinitionStrategy` (~55 lines)

**Suggested Refactoring:**
Split each strategy into its own file following the single-class-per-file convention:

```
intention/wrap/strategies/
├── WrapStrategy.kt              # Interface definition
├── OuterContainerStrategy.kt
├── ContainerItemStrategy.kt
├── UnionStrategy.kt
├── GenericCtorStrategy.kt
├── UnwrapStrategy.kt
├── EnumStrategy.kt
└── EnumMemberDefinitionStrategy.kt
```

Alternatively, group related strategies:
- `ContainerStrategies.kt` - `OuterContainerStrategy`, `ContainerItemStrategy`
- `TypeStrategies.kt` - `UnionStrategy`, `GenericCtorStrategy`
- `EnumStrategies.kt` - `EnumStrategy`, `EnumMemberDefinitionStrategy`
- `UnwrapStrategy.kt` - standalone

---

#### Files Analyzed but NOT Recommended to Split

| File | Lines | Reason |
|------|-------|--------|
| `PyIntroduceParameterObjectProcessor.kt` | 429 | Cohesive refactoring processor - all methods work together for a single refactoring operation |
| `UsageRewriter.kt` | 357 | Cohesive rewriter class - all methods handle PSI rewrites for the same feature |
| `PyMissingInDunderAllInspection.kt` | 307 | Standard inspection pattern with nested Visitor - splitting would break the inspection structure |
| `UnionCandidates.kt` | 281 | Single responsibility - collecting union type candidates |
| `CustomTypeApplier.kt` | 272 | Cohesive applier class for the custom type feature |
| `ContainerTyping.kt` | 249 | Focused on container type analysis |

---

### Priority Recommendations

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| High | Duplicate `isBuiltinName` | Low | Reduces maintenance burden | ✓ Done |
| High | Duplicate `CONTAINERS` | Low | Single source of truth | ✓ Done |
| Medium | Split `ExpectedTypeInfo.kt` | Medium | Improves readability and testability |
| Medium | Split `TargetDetector.kt` | Medium | Improves maintainability of largest file |
| Medium | Split `strategies.kt` | Low | Better organization, easier navigation |
| Medium | Intention boilerplate | Medium | Reduces code duplication |
| Low | `PyWrapHeuristics` boundaries | Medium | Clarifies architecture |
| Low | Test SDK setup | Low | Minor DRY improvement | ✓ Done |
| Low | Test helpers organization | Low | Improves discoverability |

---

### Notes

- All file paths are relative to `src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/` for production code
- Test file paths are relative to `src/test/kotlin/`
- Line numbers are approximate and may shift as the codebase evolves
