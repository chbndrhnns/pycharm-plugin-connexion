### Wrap Quickfix Feature Specification

#### Overview

The "Wrap with Expected Type" quickfix is an IntelliJ intention that automatically wraps expressions with the expected type constructor when there's a type mismatch. It analyzes the expected type from context (assignments, function parameters, return statements, etc.) and offers to wrap the current expression with the appropriate constructor.

---

### Feature Requirements (Derived from Code and Tests)

#### Core Functionality

1. **Type Mismatch Detection**: Detect when an expression's actual type differs from the expected type based on:
   - Assignment statements with type annotations
   - Function/method parameter types
   - Return statement types
   - Default parameter values
   - Lambda body types
   - Yield expression types
   - Dict key/value types
   - Container element types

2. **Wrap Plan Types** (`WrapPlan.kt`):
   - `Single`: Wrap with a single constructor (e.g., `Path("val")`)
   - `UnionChoice`: Present a chooser popup for union types with multiple candidates
   - `Elementwise`: Wrap container items using comprehension (e.g., `[Item(v) for v in items]`)
   - `ElementwiseUnionChoice`: Elementwise with union type chooser
   - `ReplaceWithVariant`: Replace literal with enum variant (e.g., `"variant"` → `MyEnum.VARIANT`)

3. **Strategy Pipeline** (`ExpectedTypeAnalyzer.kt`):
   ```
   OuterContainerStrategy → ContainerItemStrategy → UnionStrategy → 
   EnumMemberDefinitionStrategy → EnumStrategy → GenericCtorStrategy
   ```

#### Container Handling

4. **List Wrapping**:
   - Single element: wrap as `["abc"]` (literal syntax)
   - Tuple/set/comprehension/generator: wrap as `list(expr)` (constructor call)
   - Inside list literal: don't offer `list()` wrapping

5. **Container Item Wrapping**:
   - Wrap individual items inside containers with expected element type
   - Support `list[T]`, `set[T]`, `tuple[T, ...]`, `dict[K, V]`

6. **Elementwise Wrapping**:
   - For variables passed to `list[CustomType]` parameters: `[CustomType(v) for v in items]`

#### Union Type Handling

7. **Union Type Prioritization** (Type Buckets):
   - Priority order: `OWN (3) > THIRDPARTY (2) > STDLIB (1) > BUILTIN (0)`
   - Auto-select if only one candidate in highest bucket
   - Show chooser popup if multiple candidates in same bucket

8. **Union Syntax Support**:
   - PEP 604: `A | B | C`
   - `typing.Union[A, B, C]`
   - `typing.Optional[A]` (treated as `A | None`)
   - Nested: `Union[A | B]`
   - Forward references in quotes: `"A | B"`

9. **None Handling**:
   - Filter out `None`/`NoneType` from union candidates
   - For `Optional[T]` or `T | None`, prefer `T`

#### Enum Handling

10. **Enum Variant Replacement**:
    - Match literal value to enum member value
    - Replace `"variant"` with `MyEnum.VARIANT`

11. **Enum Mixin Wrapping**:
    - For `class MyEnum(str, Enum)`, wrap member values with mixin type
    - E.g., `ONE = 1` → `ONE = str(1)`

#### Special Cases

12. **String Wrapping**:
    - Numeric literal to string: convert to string literal (`123` → `"123"`)

13. **Parentheses Handling**:
    - Flatten nested parentheses before wrapping
    - `(("val"))` → `Path("val")`

14. **Import Management**:
    - Auto-add imports for non-builtin types
    - Skip import if already in scope or builtin

15. **Already Wrapped Detection**:
    - Don't offer wrap if expression is already wrapped with target constructor
    - Check both direct calls and parent calls

---

### Identified Inconsistencies and Issues

#### From `inbox.md` (Pending Issues)

1. **Inner Problem Priority** (Line 5-15):
   > "Wrap should pick inner problem first, offers `Prefix` here"
   
   When nested constructors have type mismatches, the feature offers wrapping the outer expression instead of the inner problematic one.

2. **Set Literal vs Constructor** (Line 17):
   > "Wrap should use set literal instead of set call"
   
   For `vals: set[T] = T`, should suggest `{T}` not `set(T)`.

3. **Incorrect Set Suggestion** (Line 18):
   > "Wrap suggests set instead of type: `vals: set[str] = {1}`"
   
   Should suggest `str(1)` for the element, not wrapping with `set`.

#### Code Structure Issues

4. **Strategy Order Sensitivity** (`ExpectedTypeAnalyzer.kt:12-19`):
   The pipeline order matters significantly. `OuterContainerStrategy` runs first, which can cause it to suggest container wrapping before checking if individual items need wrapping.

5. **Inconsistent Container Detection** (`ContainerStrategies.kt:30-36` vs `PyWrapHeuristics.kt:62-80`):
   - `OuterContainerStrategy.isSameContainerLiteral` checks specific literal types
   - `PyWrapHeuristics.isContainerExpression` has different logic including `PyCallExpression` checks
   - These can produce different results for the same expression

6. **Hardcoded Container Names** (`ContainerStrategies.kt:91-97`):
   ```kotlin
   fun mapToConcrete(name: String): String? = when (name) {
       "list", "sequence", "collection", "iterable", "mutablesequence", "generator", "iterator" -> "list"
       ...
   }
   ```
   Maps abstract types to concrete ones, but `Sequence` → `list` may not always be desired.

7. **Type Bucket Classification Heuristics** (`TypeBuckets.kt:45-55`):
   Uses path-based heuristics (`/site-packages/`, `/.venv/`) which may fail on non-standard setups.

#### Test vs Production Code Assumptions

8. **Disabled Test** (`WrapCollectionsTest.kt:215-226`):
   ```kotlin
   fun _testDoNotOfferListWrappingForList() { ... }
   ```
   Test is disabled (prefixed with `_`), suggesting incomplete implementation for multi-element list scenarios.

9. **Ignored Test** (`GenericTest.kt:207-239`):
   ```kotlin
   fun ignore_testWrapWithUnionCustomFirstPicksCustom() { ... }
   ```
   Comments indicate "case for introducing a box" - suggests missing feature for custom wrapper types that don't extend the base type.

10. **Forward Reference Edge Cases** (`WrapForwardRefsTest.kt:132-151`):
    Tests explicitly check that wrap is NOT offered for `"int | str | None"` forward refs, but the reason isn't clear from code - may be overly restrictive.

#### Edge Cases Needing More Flexibility

11. **Generic Type Parameters**:
    - No clear handling for `TypeVar` bounds
    - `Annotated[T, ...]` is handled but may lose metadata

12. **Callable Types** (`ExpectedTypeInfo.kt:143-148`):
    Lambda return type extraction works, but complex callable signatures may not be fully supported.

13. **Async Functions** (`ExpectedTypeInfo.kt:234-238`):
    Special handling for `Coroutine` unwrapping exists but may not cover all async patterns.

14. **Dict Comprehensions** (`ContainerStrategies.kt:33`):
    Detected but `dict` is explicitly skipped (`Line 21: if (outerCtor.name == "dict") return StrategyResult.Skip`).

15. **Star Arguments** (`WrapWithExpectedTypeIntention.kt:57-61`):
    `PyStarArgument` is explicitly excluded, but `*args` unpacking in calls may still have issues.

---

### Areas Requiring More Flexibility

1. **Configurable Type Bucket Priorities**: Currently hardcoded; users may want different prioritization.

2. **Custom Wrapper Detection**: The `isAlreadyWrappedWith` check only looks at direct calls; factory functions or builder patterns aren't detected.

3. **Multi-file Type Resolution**: `StringAnnotationResolver` has limited cross-module resolution capabilities.

4. **Container Type Mapping**: The mapping from abstract types (`Sequence`, `Iterable`) to concrete (`list`) should be configurable.

5. **Enum Value Matching**: Only exact string/numeric matches are supported; no fuzzy matching or case-insensitive options.

6. **Preview Generation**: `WrapPreview` reconstructs container text manually which may lose formatting/comments.

---

### Summary of Incorrect Assumptions

| Location | Assumption | Issue |
|----------|------------|-------|
| `OuterContainerStrategy` | Outer container issues should be checked first | Inner element issues may be more relevant |
| `TypeBucketClassifier` | Path contains `/site-packages/` = third-party | Doesn't work for all virtualenv setups |
| `ContainerStrategies:91-97` | `Sequence` should become `list` | May want to preserve abstract type |
| `EnumStrategy:39-48` | Enum values are always string or numeric literals | Doesn't handle computed values |
| `WrapApplier:32` | `str` wrapping of numbers should use quotes | May want `str(123)` in some contexts |
| `UnionCandidates:188` | Filter out builtins from union candidates | Builtins may be the correct choice |
