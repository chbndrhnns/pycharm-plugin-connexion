### Plan

To implement "Make Public" and "Make Private" QuickFixes (Intention Actions) for Python symbols, similar to Java/Kotlin,
we will create two new intention actions that leverage the existing rename refactoring infrastructure.

#### 1. Create Abstract Base Class

Create `PyToggleVisibilityIntention` extending `PyBaseIntentionAction` in `com.jetbrains.python.codeInsight.intentions`.

* **Purpose**: Shared logic for identifying valid targets.
* **Logic**:
    * `isAvailable`: Verify the element under the caret is a `PyFunction`, `PyClass`, or `PyTargetExpression` (
      fields/variables).
    * Helper methods to check naming conventions (e.g., starts with `_`).

#### 2. Create `PyMakePublicIntention`

Create a class extending `PyToggleVisibilityIntention`.

* **Availability**:
    * Element name starts with `_`.
    * Element name is NOT a "dunder" name (starts and ends with `__`), e.g., `__init__` should be excluded.
* **Action**:
    * Calculate new name by stripping leading underscores (e.g., `_foo` -> `foo`).
    * Invoke `PythonUiService.getInstance().runRenameProcessor` to perform a safe rename refactoring.

#### 3. Create `PyMakePrivateIntention`

Create a class extending `PyToggleVisibilityIntention`.

* **Availability**:
    * Element name does **not** start with `_`.
* **Action**:
    * Calculate new name by adding a leading underscore (e.g., `foo` -> `_foo`).
    * Invoke `PythonUiService.getInstance().runRenameProcessor`.

#### 4. Registration

Register the new intentions in `python-psi-impl/resources/intellij.python.psi.impl.xml` (or `plugin.xml`) using the
`<intentionAction>` tag.

#### 5. Tests

Create `PyToggleVisibilityIntentionTest` extending `PyIntentionTestCase` in
`python/testSrc/com/jetbrains/python/intentions/`.

### Suggested Tests

**Positive Cases:**

* **Class**: `class _Internal` -> `class Internal` (Make Public)
* **Function**: `def _helper(self):` -> `def helper(self):` (Make Public)
* **Field**: `self._value = 1` -> `self.value = 1` (Make Public)
* **Class**: `class Public` -> `class _Public` (Make Private)
* **Function**: `def public_method(self):` -> `def _public_method(self):` (Make Private)
* **Field**: `self.data = []` -> `self._data = []` (Make Private)

**Negative Cases (Intention should not be available):**

* **Dunder methods**: `def __init__(self):` (Both actions unavailable)
* **Already Public**: `class Foo` (Make Public unavailable)
* **Already Private**: `class _Foo` (Make Private unavailable)
* **Single Underscore**: `_` (Used in translations or REPL, usually unavailable)
* **Magic names**: `__all__`, `__slots__`.

**Edge Cases:**

* **Name Mangling**: `__private` (Double underscore). "Make Public" could rename to `private`. "Make Private" on
  `private` should likely rename to `_private` (single underscore) as per standard convention, unless `__private` is
  explicitly desired (usually discouraged).
* **Name Conflicts**: Renaming `_foo` to `foo` when `foo` already exists in the scope. The underlying `RenameProcessor`
  should handle this (show warning or conflict dialog).

### Edge Cases to Consider

1. **Inheritance**: Renaming a method in a base class should rename overrides in subclasses. The `RenameProcessor`
   handles this.
2. **Imported Symbols**: The intention should strictly operate on the **definition** of the symbol to avoid accidental
   renaming of a reference from a different file (unless `refactoring` behavior is desired at usages, but Intention
   Actions usually target the declaration).
3. **Empty Name**: Stripping `_` from a name that is just `_` results in an empty string.
4. **Keywords**: Renaming `_class` to `class` would create a syntax error. The rename validator should prevent this.