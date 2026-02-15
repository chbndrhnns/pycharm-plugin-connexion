
### Specification: Python Move Refactoring Plugin for PyCharm

#### Overview

Implement a proper **Move Refactoring** for Python top-level classes, inline/nested classes, top-level functions, and methods — modeled after the Kotlin K2 move refactoring architecture (`K2MoveHandler` → `K2MoveModel` → `K2MoveDialog` → descriptor → processor). The dialog should include autocompletion for target module/package paths, similar to Kotlin's `PackageNameReferenceEditorCombo` and file/class choosers.

---

### Current State (What Already Exists)

Python in IntelliJ already has:
- `PyMoveSymbolDelegate` — a `MoveHandlerDelegate` that handles moving top-level functions/classes and instance methods
- `PyMoveModuleMembersProcessor` / `PyMoveSymbolProcessor` — processors for executing the move
- `PyMoveModuleMembersDialog` — a basic Swing form-based dialog (`.form` file)
- `PyMoveFileHandler` — handles import updates when files are moved
- `PyMoveRefactoringUtil` — utility for import rewriting
- `makeFunctionTopLevel` — sub-package for "Make Function Top-Level" refactoring

**Gaps:** No support for moving nested/inner classes, no modern Kotlin UI DSL dialog with autocompletion, limited method move (only instance methods to parameter type), no move-to-class target.

---

### Phase 1: Architecture & Handler

#### 1.1 `PyMoveDeclarationsHandler` (new `MoveHandlerDelegate`)

```
PyMoveDeclarationsHandler extends MoveHandlerDelegate
├── supportsLanguage() → PythonLanguage
├── canMove() → validates element types:
│   - PyClass (top-level and nested)
│   - PyFunction (top-level)
│   - PyFunction (method — instance/static/class methods)
├── tryToMove() → resolves caret element to movable declaration
└── doMove() → creates PyMoveModel, opens PyMoveDialog
```

**Key decisions:**
- Replaces/extends `PyMoveSymbolDelegate` with richer element support
- Uses `parentOfTypes(PyClass, PyFunction, PyFile)` pattern from K2 to resolve caret to movable element
- Constructors (`__init__`) → move the containing class instead

#### 1.2 Element Resolution

```kotlin
fun PsiElement.findPyElementToMove(editor: Editor): PsiElement? {
    // Walk up to nearest PyClass, PyFunction, or PyFile
    // If on __init__, select parent PyClass
    // If on decorator, select decorated function/class
    // Handle whitespace/EOF edge cases (like K2MoveHandler)
}
```

---

### Phase 2: Model Layer

#### 2.1 `PyMoveModel` (analogous to `K2MoveModel`)

```
PyMoveModel
├── source: PyMoveSourceModel
│   ├── ElementSource (individual declarations)
│   └── FileSource (whole files/modules)
├── target: PyMoveTargetModel
│   ├── Module (target .py file — new or existing)
│   └── Class (target class for nested class / method moves)
├── settings: searchReferences, searchInComments, searchInStrings
├── toDescriptor() → PyMoveOperationDescriptor
├── isValidRefactoring() → validation logic
└── create(elements, targetContainer, editor) → factory
```

#### 2.2 `PyMoveTargetModel`

```
PyMoveTargetModel
├── Module target:
│   ├── modulePath: String (dotted Python module path, e.g. "mypackage.utils")
│   ├── fileName: String (e.g. "utils.py")
│   ├── directory: PsiDirectory
│   └── toDescriptor() → PyMoveTargetDescriptor.Module
├── Class target:
│   ├── targetClass: PyClass
│   └── toDescriptor() → PyMoveTargetDescriptor.Class
└── buildPanel() → installs UI components with autocompletion
```

#### 2.3 `PyMoveOperationDescriptor`

```
PyMoveOperationDescriptor
├── Declarations(elements, target, searchOptions)
│   └── refactoringProcessor() → PyMoveDeclarationsProcessor
└── Files(files, target, searchOptions)
    └── refactoringProcessor() → PyMoveFilesProcessor
```

---

### Phase 3: Dialog (Kotlin UI DSL with Autocompletion)

#### 3.1 `PyMoveDialog` (analogous to `K2MoveDialog`)

Extends `RefactoringDialog`. Uses `com.intellij.ui.dsl.builder.panel {}` (Kotlin UI DSL 2).

**Layout:**

```
┌─────────────────────────────────────────────┐
│ Move                                    [?] │
├─────────────────────────────────────────────┤
│ Members to move:                            │
│ ┌─────────────────────────────────────────┐ │
│ │ ☑ class MyClass          (mymod.py)     │ │
│ │ ☑ def helper_func        (mymod.py)     │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ To module: [mypackage.newmodule_____] [📁]  │  ← autocomplete
│                                             │
│ ○ To module file                            │
│   File: [newmodule.py_______________] [📁]  │
│                                             │
│ ○ To class                                  │
│   Class: [mypackage.TargetClass_____] [📁]  │  ← autocomplete
│                                             │
│ ☑ Search for references                     │
│ ☐ Search in comments and strings            │
│                                             │
│              [Preview] [Refactor] [Cancel]  │
└─────────────────────────────────────────────┘
```

#### 3.2 Autocompletion Components

**Module path autocompletion:**
- Create `PyModulePathReferenceEditorCombo` (analogous to `PackageNameReferenceEditorCombo`)
- Uses `PsiReferenceProvider` that resolves dotted Python module paths
- Completion contributes all importable module paths from project scope
- Implementation: `EditorComboBoxRenderer` + `TextFieldWithAutoCompletion` backed by a `CompletionProvider` that indexes `PyFile` names via `PyModuleNameIndex`

**Class chooser autocompletion:**
- `TextFieldWithBrowseButton` + browse action opening a `TreeClassChooserDialog` filtered to `PyClass`
- On text change, resolve FQN via `PyPsiFacade.findShortestImportableName()` / `PyResolveImportUtil`
- Validate target class exists and is accessible

**File chooser:**
- `TextFieldWithBrowseButton` with `FileChooserDescriptor` restricted to `.py` files
- Auto-populate module path from selected file's location relative to source roots

#### 3.3 Source Panel (`PyMoveSourceModel`)

- Member list with checkboxes (reuse pattern from `PyModuleMemberInfoModel`)
- Show dependent members that should also be moved (using `PyDependentModuleMembersCollector`)

---

### Phase 4: Processor

#### 4.1 `PyMoveDeclarationsProcessor` (extends `BaseRefactoringProcessor`)

**Steps:**
1. **Find usages** — collect all references to moved elements across the project
2. **Analyze conflicts** — detect naming collisions, circular imports, visibility issues
3. **Perform move:**
   a. Copy PSI elements to target location
   b. Update `__all__` in source and target modules
   c. Add necessary imports in target module
   d. Update all references (rewrite imports)
   e. Remove original declarations
   f. Clean up unused imports in source module
   g. Add re-exports in source if configured (optional)
4. **Post-processing** — optimize imports, reformat

#### 4.2 Import Rewriting Strategy

```
For each usage of moved element:
  if usage is "from source_module import element":
    → rewrite to "from target_module import element"
  if usage is "import source_module" + "source_module.element":
    → add "import target_module" + rewrite to "target_module.element"
  if usage is relative import:
    → recalculate relative path or convert to absolute
  if usage is in __init__.py re-export:
    → update re-export path
```

#### 4.3 Method Move Specifics

Moving a method to another class:
1. Adjust `self` parameter references
2. If method references source class members → add parameter or import
3. Handle `super()` calls
4. Update decorators (`@staticmethod`, `@classmethod`) as needed
5. Leave a delegation stub in the original class (optional, configurable)

#### 4.4 Nested Class Move

Moving a nested class out (to top-level or another class):
1. Update all `OuterClass.InnerClass` references
2. Handle references to outer class members (may need to pass as constructor arg)
3. Update type annotations referencing the nested class

---

### Phase 5: Conflict Detection

#### `PyMoveConflictChecker`

Detect and report:
- **Name collisions** in target module/class
- **Circular import** creation
- **Visibility issues** — moving `_private` members to public modules
- **Star import breakage** — `from module import *` affected
- **`__all__` inconsistencies**
- **Relative import breakage** in packages
- **Type annotation breakage** — `TYPE_CHECKING` imports

---

### Phase 6: Extension Points & Integration

```xml
<moveHandlerDelegate implementation="com.jetbrains.python.refactoring.move.PyMoveDeclarationsHandler"
                      order="before pyMoveSymbol"/>

<!-- Autocompletion for module paths -->
<completion.contributor language="Python"
    implementationClass="com.jetbrains.python.refactoring.move.ui.PyModulePathCompletionContributor"/>
```

---

### Test Cases

#### Basic Move Tests

| # | Test | Description |
|---|------|-------------|
| T1 | Move top-level function to new module | Function moved, imports updated everywhere |
| T2 | Move top-level function to existing module | Function appended, imports rewritten |
| T3 | Move top-level class to new module | Class + all methods moved, references updated |
| T4 | Move top-level class to existing module | No name collision, imports updated |
| T5 | Move multiple declarations at once | 2 functions + 1 class moved together |
| T6 | Move nested class to top-level | `Outer.Inner` references → `Inner` with new import |
| T7 | Move nested class to another class | `A.Inner` → `B.Inner`, all refs updated |
| T8 | Move instance method to another class | `self` handling, delegation stub |
| T9 | Move static method to another class | Straightforward, update `ClassName.method()` refs |
| T10 | Move class method to another class | `cls` parameter handling |

#### Import Rewriting Tests

| # | Test | Description |
|---|------|-------------|
| T11 | `from mod import func` rewritten | Direct import updated to new module |
| T12 | `import mod; mod.func()` rewritten | Qualified access updated |
| T13 | Relative import rewritten | `from .mod import func` → recalculated |
| T14 | `__init__.py` re-export updated | Package-level import chain maintained |
| T15 | Star import — `from mod import *` | `__all__` updated in source, element accessible from target |
| T16 | `TYPE_CHECKING` import updated | Imports inside `if TYPE_CHECKING:` blocks handled |
| T17 | Unused imports cleaned in source | After move, source module has no dangling imports |

#### Dialog / UI Tests

| # | Test | Description |
|---|------|-------------|
| T18 | Module path autocompletion | Typing "mypack" suggests "mypackage.module1", etc. |
| T19 | Class chooser filters to PyClass | Only Python classes shown in tree |
| T20 | Validation: invalid module path | Error shown, Refactor button disabled |
| T21 | Validation: name collision | Conflict warning displayed |
| T22 | Dependent members shown | Moving `func_a` that calls private `_helper` → `_helper` shown as dependent |
| T23 | Preview usages | Preview panel shows all affected files |

#### Processor / Conflict Tests

| # | Test | Description |
|---|------|-------------|
| T24 | Circular import detected | Moving `a.func` to `b` when `b` imports from `a` and vice versa → warning |
| T25 | Name collision detected | Target already has `def func` with same name → conflict |
| T26 | `__all__` updated | Source `__all__` entry removed, target `__all__` entry added |
| T27 | Undo works | Full move is undoable in one step |
| T28 | Multi-file usage update | References in 10+ files all updated correctly |

---

### Edge Cases

1. **Moving `__init__` method** — should move the entire class, not just `__init__`
2. **Moving decorated functions** — decorators must move with the function; decorator imports must be added to target
3. **Moving functions with closures** — function references variables from enclosing scope → conflict/error
4. **Moving to `__init__.py`** — special handling for package-level exports
5. **Moving between packages with relative imports** — all relative imports in the package must be recalculated
6. **Circular import creation** — moving element creates a new circular dependency chain
7. **Moving elements referenced in `__all__`** — must update `__all__` in both source and target
8. **Moving elements used in `if TYPE_CHECKING:` blocks** — these imports are type-only and need special handling
9. **Moving a class that is a base class** — subclasses in other files must update their import
10. **Moving overridden methods** — method is part of an inheritance chain; moving breaks LSP contract → warning
11. **Moving a function used as a default argument** — `def foo(x=moved_func)` — must update default arg reference
12. **Moving to a module that doesn't exist yet** — must create the `.py` file and any intermediate `__init__.py` files
13. **Moving elements with `global` / `nonlocal` references** — should be blocked or warned
14. **Moving a class with metaclass** — metaclass import must also be added to target
15. **Moving between source roots** (e.g., `src/` → `tests/`) — different import resolution contexts
16. **Moving a function that is registered as an entry point** (e.g., in `setup.py` / `pyproject.toml`) — cannot auto-update, warn user
17. **Moving elements with conditional imports** — `try: import X except: import Y` patterns
18. **Moving a dataclass / NamedTuple** — field references and unpacking patterns must be updated
19. **Moving a Protocol class** — structural subtyping means usages may not have explicit imports
20. **Star imports masking** — `from target import *` already exists, moved name may shadow existing names
21. **Moving an `__init__.py`-only symbol** — symbol defined in `__init__.py` and re-exported; moving changes package API
22. **Caret on whitespace between declarations** — should pick the nearest declaration (like K2 handler)
23. **Moving a method that uses `super()`** — MRO changes if moved to a different class hierarchy
24. **Empty `__init__.py` creation** — when moving to a new sub-package, intermediate `__init__.py` files may need to be created

---

### Implementation Order

1. **Phase 1** — Handler + element resolution (1 week)
2. **Phase 2** — Model layer + descriptors (1 week)
3. **Phase 3** — Dialog with Kotlin UI DSL + autocompletion (1.5 weeks)
4. **Phase 4** — Processor: top-level function/class move + import rewriting (2 weeks)
5. **Phase 5** — Processor: nested class + method move (1.5 weeks)
6. **Phase 6** — Conflict detection + edge case handling (1 week)
7. **Phase 7** — Tests + polish (1 week)

**Total estimate: ~8–9 weeks**

---

### Key Files to Reference

| Kotlin (model to follow) | Python (existing to extend) |
|---|---|
| `K2MoveHandler.kt` | `PyMoveSymbolDelegate.java` |
| `K2MoveDialog.kt` | `PyMoveModuleMembersDialog.java` |
| `K2MoveModel.kt` | — (new) |
| `K2MoveTargetModel.kt` | — (new) |
| `K2MoveSourceModel.kt` | `PyModuleMemberInfo.java` |
| `PackageNameReferenceEditorCombo` | — (new `PyModulePathEditorCombo`) |
| `KotlinDestinationFolderComboBox` | — (reuse or adapt) |
| — | `PyMoveModuleMembersProcessor.java` (extend) |
| — | `PyMoveRefactoringUtil.java` (extend) |
| — | `PyDependentModuleMembersCollector.java` (reuse) |
