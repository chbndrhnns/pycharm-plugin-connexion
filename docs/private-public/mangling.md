### Analysis & Recommendation

You are absolutely right to question the use of "name mangling" (double underscore `__`). In the context of Python,
sticking to **Public** vs **Non-Public** (single underscore) is the standard convention.

#### 1. Public / Protected / Private Terminology

While Java/Kotlin use `public`/`protected`/`private`, Python effectively only has "Public" (no underscore) and "
Non-Public" (single underscore).

* **Single Underscore (`_foo`)**: This is the standard way to signal "internal API" or "private implementation". It
  matches what most users want when they "Make Private".
* **Double Underscore (`__foo`)**: This triggers name mangling (`_ClassName__foo`). It is **not** equivalent to Java's
  `private`. It is strictly designed to prevent name collisions in inheritance scenarios (e.g., mixins). Using it for
  standard privacy is widely considered "un-Pythonic" and makes debugging/testing harder.

#### 2. Decision

* **Avoid Name Mangling by default**: We should **not** make `__` the result of a standard "Make Private" action.
* **Map "Make Private" to `_`**: Since standard Python lacks a strict `private` modifier, the "Make Private" intention
  should add a **single underscore**. This aligns user intent (hiding the symbol) with Python best practices.
* **Action Names**:
    * **"Make Public"**: Rename `_foo` or `__foo` -> `foo`.
    * **"Make Private"**: Rename `foo` -> `_foo`.
    * (We do not need a separate "Make Protected" action, as "Make Private" covers the standard non-public convention).

### Revised Plan

We will proceed with two actions that toggle between "No Underscore" and "Single Underscore".

#### 1. `PyMakePublicIntention`

* **Availability**: Element name starts with `_` (covers both `_` and `__`).
* **Action**: Removes **all** leading underscores (e.g., `__foo` -> `foo`).
* **Logic**: Uses `StringUtil.trimLeading(name, '_')` (consistent with existing `PyMakePublicQuickFix`).

#### 2. `PyMakePrivateIntention`

* **Availability**: Element name does **not** start with `_`.
* **Action**: Adds a **single** leading underscore (e.g., `foo` -> `_foo`).
* **Note**: We will **not** support creating double underscores automatically.

### Updated Edge Cases & Tests

#### New/Refined Test Cases

* **Avoid Mangling**: `class Foo` -> "Make Private" -> `class _Foo` (Not `__Foo`).
* **Demangle to Public**: `def __mangled(self):` -> "Make Public" -> `def mangled(self):`.
* **Already "Private"**:
    * `def _internal(self):`: "Make Private" should be **unavailable** (already has `_`).
    * `def __mangled(self):`: "Make Private" should be **unavailable** (already starts with `_`).

#### Edge Cases to Handle

* **Dunder Names**: `__init__`, `__call__` must be excluded from both actions.
* **Conflicts**: If `foo` exists and we rename `_foo` -> `foo`, the standard `RenameProcessor` conflict dialog should
  appear.
* **Import Updates**: Since this uses `RenameProcessor`, usages in other files (imports) will be updated automatically (
  e.g., `from mod import _foo` -> `from mod import foo`).

### Implementation Note

Existing code in `PyMakePublicQuickFix` (which you can reuse or reference) already implements the "strip all
underscores" logic:

```java
final String publicName = StringUtil.trimLeading(name, '_');
```

We will mirror this for the intentions.