### Implementation Suggestion

To support importing nested classes, we need to modify the auto-import mechanism to detect nested classes, find their top-level parent, and import that parent while updating the reference at the usage site.

The implementation involves changes in `PyImportCollector` and potentially extending `AutoImportQuickFix` or `ImportCandidateHolder`.

#### 1. Modify `PyImportCollector.java`

**a. Relax `isIndexableTopLevel` check**
Currently, `isIndexableTopLevel` strictly enforces `PyUtil.isTopLevel(symbol)`. We need to allow nested classes.
```java
private static boolean isIndexableTopLevel(PsiElement symbol) {
  if (symbol instanceof PsiFileSystemItem) return true;
  if (symbol instanceof PyClass) {
    // Allow if top-level OR if it's a nested class
    return PyUtil.isTopLevel(symbol) || isNestedClass(symbol);
  }
  if (symbol instanceof PyFunction) return PyUtil.isTopLevel(symbol);
  // ...
}
```

**b. Update `addSymbolImportCandidates`**
When a candidate is a nested class, we cannot import it directly. We must find its top-level parent and import that.
In `addSymbolImportCandidates(PsiFile existingImportFile)`:
1.  Iterate over symbols found by `PyClassNameIndex`.
2.  If the symbol is a nested class:
    *   Find the top-level parent class (`topLevelClass`) using `PsiTreeUtil.getParentOfType(symbol, PyClass.class)`.
    *   Calculate the import path for `topLevelClass` (not the nested class).
    *   Create a candidate that imports `topLevelClass`.
    *   **Crucial**: We need to signal that this import requires qualifying the reference. The user searched for `Nested`, but we are importing `TopLevel`. The reference `Nested` in the code must become `TopLevel.Nested`.

#### 2. Handle Reference Qualification

Since `ImportCandidateHolder` is a data class, we can add a field to it, e.g., `myQualifiedNameForUsage`.
When creating the candidate for a nested class `TopLevel.Nested`:
*   `myImportable`: `TopLevel` class.
*   `myPath`: Path to `TopLevel`.
*   `myQualifiedNameForUsage`: "TopLevel.Nested".

Then, in `AutoImportQuickFix` (or `ImportFromExistingAction`), when the action is executed:
1.  Perform the import of `TopLevel`.
2.  After the import is added, check if `candidate.myQualifiedNameForUsage` is present.
3.  If present, replace the reference at the usage site (e.g., `Nested`) with `TopLevel.Nested`.

Alternatively, create a specific `NestedClassAutoImportQuickFix` that wraps the standard logic and performs the PSI modification after the import.

### Edge Cases

1.  **Deep Nesting**: Classes nested multiple levels deep (e.g., `class A: class B: class C:`). Importing `C` should import `A` and qualify usage as `A.B.C`.
2.  **Parent Already Imported**: If `TopLevel` is already imported, we shouldn't add a new import but still update the usage to `TopLevel.Nested`. The existing logic might filter out `TopLevel` if it's already imported, so we need to ensure "Qualification Fix" is still available.
3.  **Name Collisions**: If `TopLevel` conflicts with a local variable or another import. The import logic should handle aliasing (e.g., `import TopLevel as T`), and the usage update must respect the alias (`T.Nested`).
4.  **Local Classes**: Classes defined inside functions cannot be imported. `isNestedClass` should ensure the parent chain consists only of classes and modules, not functions.
5.  **Inheritance**: If `Nested` is accessed via a subclass, we should prefer importing the defining class.

### Test Cases

We should implement the following test cases in `PyAutoImportTest` (or similar):

1.  **Basic Nested Import**:
    *   **File:** `main.py`: `x = Nested()`
    *   **File:** `my_pkg.py`: `class Top: class Nested: pass`
    *   **Result:** `from my_pkg import Top` added, usage becomes `x = Top.Nested()`.

2.  **Deeply Nested Import**:
    *   **File:** `main.py`: `x = Deep()`
    *   **File:** `my_pkg.py`: `class Top: class Mid: class Deep: pass`
    *   **Result:** `from my_pkg import Top` added, usage becomes `x = Top.Mid.Deep()`.

3.  **Parent Already Imported**:
    *   **File:** `main.py`:
        ```python
        import my_pkg
        x = Nested()
        ```
    *   **Result:** usage becomes `x = my_pkg.Top.Nested()` (or `Top.Nested` if `from my_pkg import Top` exists).

4.  **Aliased Import**:
    *   **File:** `main.py`: `x = Nested()`
    *   **Result:** If user chooses to alias `Top` as `T`, usage becomes `x = T.Nested()`.

5.  **Invalid Nesting**:
    *   **File:** `my_pkg.py`: `def func(): class Local: pass`
    *   **Result:** `Local` should NOT be suggested.