
### Move Up/Down in Scope for Python — A Practical First Step

Yes, implementing **Move Up/Down in Scope** (also called "Move Inner to Upper Level" / "Move to Inner Scope") is an excellent first step. It's a smaller, self-contained refactoring that exercises many of the same subsystems (PSI manipulation, reference updating, import rewriting) needed for the full cross-module move, but with a much narrower blast radius.

---

### What "Move Up/Down in Scope" Means

| Direction | Example | Result |
|-----------|---------|--------|
| **Move Up** (to outer scope) | Nested class `Outer.Inner` → top-level `Inner` | `Inner` becomes a sibling of `Outer` |
| **Move Up** | Local function inside `def foo` → module-level function | Function promoted to top-level |
| **Move Up** | Method → top-level function (with `self` converted to parameter) | Already partially exists as `makeFunctionTopLevel` |
| **Move Down** (to inner scope) | Top-level `helper()` used only inside `MyClass` → `MyClass.helper()` | Function becomes a method |
| **Move Down** | Top-level class → nested class inside another class | Class becomes inner class |

---

### Why It's a Good First Step

1. **Single-file scope** — no cross-file import rewriting needed initially
2. **Reuses existing infrastructure** — `PyMoveModuleMembersProcessor`, `makeFunctionTopLevel` already exist
3. **Validates PSI manipulation** — copying/removing declarations, adjusting indentation, updating references
4. **Incremental** — each direction (up/down) and element type (class/function/method) can be delivered independently
5. **Feeds into full move** — the "move nested class to top-level" logic is directly reusable when moving to another module

---

### Implementation Guide

#### Phase 0: Intention Actions (Quickest Win)

Register as `IntentionAction` / `LocalQuickFix` entries rather than a full refactoring dialog. This avoids UI complexity and ships faster.

```xml
<!-- plugin.xml -->
<intentionAction>
  <language>Python</language>
  <className>com.jetbrains.python.refactoring.move.scope.PyMoveToOuterScopeIntention</className>
  <categoryKey>python.move.scope</categoryKey>
</intentionAction>
<intentionAction>
  <language>Python</language>
  <className>com.jetbrains.python.refactoring.move.scope.PyMoveToInnerScopeIntention</className>
  <categoryKey>python.move.scope</categoryKey>
</intentionAction>
```

#### Phase 1: Move Up — Nested Class to Top-Level

**File:** `PyMoveNestedClassToTopLevelProcessor.kt`

**Steps:**
1. Identify `PyClass` whose parent is another `PyClass`
2. Collect all references to `Outer.Inner` within the file
3. Copy the class PSI to top-level (after the outer class or at end of file)
4. Fix indentation (dedent by one level)
5. Update all `Outer.Inner` references → `Inner`
6. If `Inner` references `Outer` members via implicit scope, add an explicit parameter/import
7. Remove original nested class
8. Update `__all__` if present

```kotlin
class PyMoveToOuterScopeIntention : PyBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val element = file?.findElementAt(editor?.caretModel?.offset ?: return false) ?: return false
        val pyClass = element.parentOfType<PyClass>() ?: return false
        // Available if class is nested (parent is also a PyClass or PyFunction)
        return pyClass.containingClass != null || pyClass.parent is PyFunction
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = file?.findElementAt(editor?.caretModel?.offset ?: return) ?: return
        val pyClass = element.parentOfType<PyClass>() ?: return
        // Delegate to processor
        PyMoveToOuterScopeProcessor(project, pyClass).run()
    }
}
```

#### Phase 2: Move Up — Local Function to Module Level

**Steps:**
1. Identify `PyFunction` defined inside another `PyFunction`
2. Check for closure variables (`nonlocal`, free variables) → warn/block
3. Copy function to module level
4. Convert captured variables to parameters
5. Update all call sites to pass the new arguments
6. Remove original local function

**Conflict detection:**
- Function uses `nonlocal` → **block** (cannot move)
- Function reads enclosing variables → **convert to parameters** (prompt user)
- Name collision at module level → **warn**

#### Phase 3: Move Up — Method to Top-Level Function

This largely already exists as `makeFunctionTopLevel` in:
`python/src/com/jetbrains/python/refactoring/move/makeFunctionTopLevel/`

**Enhancement:** Integrate it into the unified "Move Up in Scope" action so the user gets one consistent entry point.

#### Phase 4: Move Down — Top-Level Function into Class

**Steps:**
1. Identify `PyFunction` at module level
2. User selects target `PyClass` (simple popup chooser listing classes in the file)
3. Analyze first parameter — if it's typed as the target class, convert to `self`
4. Move function into class body
5. Update all call sites: `func(obj, ...)` → `obj.func(...)`
6. Add `@staticmethod` if no `self` parameter applies

#### Phase 5: Move Down — Top-Level Class to Nested

**Steps:**
1. Identify `PyClass` at module level
2. User selects target outer `PyClass`
3. Move class PSI inside target class (indent)
4. Update all references: `Inner` → `Outer.Inner`
5. Handle `__all__`

---

### Key Classes to Create

```
com.jetbrains.python.refactoring.move.scope/
├── PyMoveToOuterScopeIntention.kt        # Intention: move up
├── PyMoveToInnerScopeIntention.kt        # Intention: move down
├── PyMoveToOuterScopeProcessor.kt        # Handles nested→top-level
├── PyMoveToInnerScopeProcessor.kt        # Handles top-level→nested
├── PyScopeMoveCandidateResolver.kt       # Determines what can move where
└── PyScopeMoveConflictChecker.kt         # Detects closure vars, name collisions
```

---

### Test Cases

#### Move Up Tests

| # | Test | Description |
|---|------|-------------|
| U1 | Nested class → top-level | `Outer.Inner` refs become `Inner` |
| U2 | Nested class referencing outer members | Conflict: outer member access needs resolution |
| U3 | Local function → module level (no captures) | Clean move, call sites unchanged |
| U4 | Local function → module level (with captures) | Captured vars become parameters, call sites updated |
| U5 | Local function with `nonlocal` | Move blocked with error message |
| U6 | Method → top-level function | `self` becomes first parameter (existing `makeFunctionTopLevel`) |
| U7 | Doubly-nested class → one level up | `A.B.C` → `A.C`, refs updated |
| U8 | Name collision at target scope | Warning: target scope already has same name |

#### Move Down Tests

| # | Test | Description |
|---|------|-------------|
| D1 | Top-level function → method | First param typed as class → becomes `self` |
| D2 | Top-level function → static method | No class-typed param → `@staticmethod` added |
| D3 | Top-level class → nested class | All refs `MyClass` → `Outer.MyClass` |
| D4 | Function used outside file | Warning: external callers will break |
| D5 | Move into class with name collision | Conflict detected and reported |

#### Edge Cases

| # | Edge Case |
|---|-----------|
| E1 | Moving a decorated nested class — decorators move with it |
| E2 | Moving a class that inherits from the outer class — circular reference |
| E3 | Moving a local function that is returned as a closure — semantics change |
| E4 | Moving a function with `yield` (generator) — must preserve generator nature |
| E5 | Moving a nested class used as a type annotation in the outer class |
| E6 | Moving an `async def` local function — `async` must be preserved |
| E7 | Moving a method with `super()` calls — MRO changes |
| E8 | Moving a `@property` method — must move getter/setter/deleter together |
| E9 | Moving a nested class with `__slots__` referencing outer class |
| E10 | Moving a function with `*args`/`**kwargs` that are closure-captured |
| E11 | Caret on whitespace between two nested classes — pick nearest |
| E12 | Moving the only method out of a class — warn about empty class |

---

### How This Feeds Into the Full Move Refactoring

```
Move Up/Down in Scope (Phase 0)     Full Cross-Module Move (Phases 1-6)
─────────────────────────────────    ──────────────────────────────────
PSI copy + remove logic          →   Reused in PyMoveDeclarationsProcessor
Reference updating (intra-file)  →   Extended to cross-file reference rewriting
Conflict detection (closures,    →   Extended with circular imports, visibility
  name collisions)
Scope analysis                   →   Reused for determining valid move targets
```

The scope-move processors become building blocks: "move nested class to top-level in same file" is just "move nested class to another module" minus the import rewriting. Implementing scope moves first lets you validate the PSI manipulation and reference-updating logic in isolation before adding the complexity of cross-file import management.

---

### Recommended Implementation Order

1. **Week 1:** `PyMoveToOuterScopeIntention` — nested class → top-level (simplest case)
2. **Week 2:** Local function → module level (with closure analysis)
3. **Week 3:** `PyMoveToInnerScopeIntention` — top-level function → method/static method
4. **Week 4:** Top-level class → nested class + conflict detection + tests
5. **Then:** Proceed to Phase 1 of the full move refactoring spec, reusing the scope-move processors
