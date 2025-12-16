### Step-by-Step Plan to Improve the Wrap Quickfix Implementation

Based on the comprehensive analysis of the codebase, here is a prioritized improvement plan:

---

#### Phase 1: Fix Critical Bugs (High Priority)

**Step 1.1: Fix Inner Problem Priority Issue**
- **Problem**: Wrap offers outer container wrapping instead of inner element wrapping (inbox.md line 5-15)
- **Location**: `ExpectedTypeAnalyzer.kt` pipeline order, `OuterContainerStrategy.kt`
- **Action**: 
  1. Add a pre-analysis pass to detect if the caret is on an element with a direct type mismatch
  2. Modify `OuterContainerStrategy` to check if inner elements have type issues before suggesting outer wrapping
  3. Consider reordering pipeline: `ContainerItemStrategy` → `OuterContainerStrategy` for certain contexts

**Step 1.2: Fix Set Literal vs Constructor**
- **Problem**: Suggests `set(T)` instead of `{T}` for single elements (inbox.md line 17)
- **Location**: `WrapApplier.kt:31-36`
- **Action**: Add set literal handling similar to list:
  ```kotlin
  ctorName == "set" && !PyWrapHeuristics.isContainerExpression(unwrapped) -> "{${unwrapped.text}}"
  ```

**Step 1.3: Fix Incorrect Set Suggestion**
- **Problem**: Suggests `set` wrapping instead of element type wrapping for `vals: set[str] = {1}` (inbox.md line 18)
- **Location**: `OuterContainerStrategy.kt:30-36`
- **Action**: When inside a set literal, skip outer container strategy and let `ContainerItemStrategy` handle element wrapping

---

#### Phase 2: Improve Strategy Pipeline (Medium Priority)

**Step 2.1: Refactor Strategy Order Logic**
- **Location**: `ExpectedTypeAnalyzer.kt:12-19`
- **Action**:
  1. Make pipeline order context-aware (check if caret is on container vs element)
  2. Add a `StrategyContext` enum: `CONTAINER_LEVEL`, `ELEMENT_LEVEL`, `UNKNOWN`
  3. Filter strategies based on context before running pipeline

**Step 2.2: Unify Container Detection Logic**
- **Problem**: Inconsistent detection between `ContainerStrategies.kt:30-36` and `PyWrapHeuristics.kt:62-80`
- **Action**:
  1. Create a single `ContainerDetector` utility class
  2. Consolidate `isSameContainerLiteral`, `isContainerExpression`, `isContainerLiteral` into one coherent API
  3. Update all call sites to use the unified detector

**Step 2.3: Enable Disabled Tests**
- **Location**: `WrapCollectionsTest.kt:215-226` (`_testDoNotOfferListWrappingForList`)
- **Action**: 
  1. Implement the missing multi-element list scenario handling
  2. Remove the `_` prefix to enable the test
  3. Add similar tests for set and tuple

---

#### Phase 3: Improve Type Bucket Classification (Medium Priority)

**Step 3.1: Make Type Bucket Heuristics More Robust**
- **Problem**: Path-based heuristics fail on non-standard setups (TypeBuckets.kt:45-55)
- **Location**: `TypeBucketClassifier.kt`
- **Action**:
  1. Add fallback detection using `PyPackageManager` or SDK paths
  2. Support conda environments (`/envs/`, `/conda/`)
  3. Support poetry environments (`.poetry/`, `pypoetry`)
  4. Add unit tests for various virtualenv layouts

**Step 3.2: Make Bucket Priorities Configurable**
- **Location**: `TypeBuckets.kt`, `PluginSettingsState`
- **Action**:
  1. Add settings for bucket priority order
  2. Add option to always prefer OWN types or allow STDLIB in certain cases
  3. Update `UnionStrategy` to read from settings

---

#### Phase 4: Handle Edge Cases (Medium Priority)

**Step 4.1: Improve Forward Reference Handling**
- **Problem**: Overly restrictive for `"int | str | None"` forward refs (WrapForwardRefsTest.kt:132-151)
- **Location**: `UnionCandidates.kt`, `StringAnnotationResolver`
- **Action**:
  1. Document why wrap is not offered for builtin union forward refs
  2. If intentional, add clear skip reason; if bug, fix the filtering logic
  3. Add tests for edge cases with mixed forward refs

**Step 4.2: Support Custom Wrapper Types**
- **Problem**: `ignore_testWrapWithUnionCustomFirstPicksCustom` suggests missing feature (GenericTest.kt:207-239)
- **Location**: `UnionStrategy.kt`
- **Action**:
  1. Implement "box" detection for custom types that don't extend base type
  2. Add heuristic: if `CustomWrapper.__init__` accepts the actual type, offer wrapping
  3. Enable the ignored test

**Step 4.3: Improve Dict Comprehension Support**
- **Problem**: Dict is explicitly skipped (ContainerStrategies.kt:21)
- **Location**: `OuterContainerStrategy.kt`
- **Action**:
  1. Implement dict key/value wrapping for comprehensions
  2. Add tests for `{k: v for k, v in items}` scenarios

---

#### Phase 5: Code Quality & Maintainability (Lower Priority)

**Step 5.1: Make Container Type Mapping Configurable**
- **Problem**: Hardcoded mapping `Sequence` → `list` may not be desired (ContainerStrategies.kt:91-97)
- **Action**:
  1. Move mapping to a configuration object
  2. Add setting to preserve abstract types or map to concrete
  3. Consider user-defined mappings

**Step 5.2: Improve Preview Generation**
- **Problem**: `WrapPreview` reconstructs container text manually, may lose formatting
- **Location**: `WrapPreview.kt:64-110`
- **Action**:
  1. Use PSI-based text manipulation where possible
  2. Preserve comments and formatting in preview
  3. Add tests for preview accuracy

**Step 5.3: Add Comprehensive Logging**
- **Action**:
  1. Add debug logging to each strategy's `run()` method
  2. Log skip reasons for debugging
  3. Add a "Wrap Debug Mode" setting for troubleshooting

---

#### Phase 6: Testing & Documentation (Ongoing)

**Step 6.1: Add Missing Test Coverage**
- Add tests for:
  - Nested container scenarios
  - TypeVar bounds
  - Complex callable signatures
  - Async patterns beyond basic coroutine
  - Star argument edge cases

**Step 6.2: Update Spec Documentation**
- Create/update `spec/wrap/` folder with:
  - Feature specification document
  - Strategy pipeline documentation
  - Known limitations and workarounds

**Step 6.3: Add Integration Tests**
- Test full workflows:
  - Wrap → Unwrap round-trip
  - Wrap with import addition
  - Union chooser selection

---

#### Implementation Order Summary

| Phase | Steps | Estimated Effort | Impact |
|-------|-------|------------------|--------|
| 1 | 1.1, 1.2, 1.3 | 2-3 days | High - fixes user-facing bugs |
| 2 | 2.1, 2.2, 2.3 | 3-4 days | High - improves reliability |
| 3 | 3.1, 3.2 | 2 days | Medium - better type handling |
| 4 | 4.1, 4.2, 4.3 | 3-4 days | Medium - expands coverage |
| 5 | 5.1, 5.2, 5.3 | 2-3 days | Lower - maintainability |
| 6 | 6.1, 6.2, 6.3 | Ongoing | Quality assurance |

**Recommended Starting Point**: Phase 1, Step 1.1 (Inner Problem Priority) as it addresses the most impactful user-facing issue identified in `inbox.md`.

