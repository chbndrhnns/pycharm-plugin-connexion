### Explanation of the error

`ProblemsHolder.registerProblem` has a strict contract: the `PsiElement` you pass **must belong to the file currently being inspected**.

In your stacktrace, the inspection is visiting `entities.py`:

- Expected containing file: `PyFile:entities.py`
- Actual containing file of the element you passed: `PyiFile:dataclasses.pyi`

So your `ProblemDescriptor` is created on an element from `dataclasses.pyi` (the defining file of `dataclass`), while the inspection is running on `entities.py` (the file that imports/uses it). The platform detects this and throws:

> generated invalid ProblemDescriptor … It contains PsiElement with getContainingFile(): 'PyiFile:dataclasses.pyi'; but expected: 'PyFile:entities.py'

### The core rule

For **local inspections** (`LocalInspectionTool` / `PyInspectionExtension` etc.):

> Every `PsiElement` you pass to `ProblemsHolder.registerProblem(...)` must come from the same `PsiFile` that the inspection is currently visiting.

You *can* resolve to other files and inspect them, but you **cannot** register a problem on PSI elements from those other files in that inspection visitor.

### How to do it correctly in your case

You are:

1. Inspecting file **b** (`entities.py`).
2. Looking at an imported symbol that actually comes from file **a** (`__init__.py`, `__all__`, or some module).
3. Wanting to report that the symbol is not exported in `__all__` of its package/file.

Correct approach:

1. **Find the PSI element in the current file that you want to highlight.**
   Typically one of:
   - The imported name in the `import` or `from ... import ...` statement (`PyImportElement`, `PyImportedNameDefiner`, `PyTargetExpression` etc.).
   - Or the whole import statement.

2. **Register the problem on that element, not on the definition in the other file.**
   
   Roughly:

   ```kotlin
   override fun visitPyFile(node: PyFile) {
       // ... resolve import, inspect exported symbols in other file/package ...
       val importElement: PsiElement = /* element in entities.py */

       if (!isExportedInDunderAll(resolvedSymbol)) {
           holder.registerProblem(
               importElement, // MUST belong to entities.py
               "Symbol '${resolvedSymbol.name}' is not exported in package __all__",
               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
               MyQuickFix()
           )
       }
   }
   ```

   Here, `importElement.getContainingFile()` must be `entities.py`.

3. **If the quickfix needs to change file a (where `__all__` lives), do that inside the quickfix**, *not* by changing what file the problem is attached to.

   In your `LocalQuickFix.applyFix` implementation you can:

   - Use `descriptor.psiElement` (which is in `entities.py`) to
     - resolve back to the original definition (in `dataclasses.pyi` or wherever),
     - navigate from that definition to the package/module that owns `__all__`,
     - then perform a `WriteCommandAction` that edits that file and updates `__all__`.

   The fact that the fix edits another file is fine; only the **problem descriptor's element** must be in the inspected file.

### When you *do* want to highlight the defining file

If conceptually the problem really “belongs” to the file that defines/export the symbol (file **a**), you have two options:

1. **Write a separate inspection that runs on that file type** (e.g. the package’s `__init__.py`) and registers problems there. Your current inspection that runs on the usage/import side should then only:
   - either do nothing, or
   - show a usage-side warning but still attach it to the import in the current file.

2. **Use a global inspection (`GlobalInspectionTool`)** if you truly need a global view and want to report problems in multiple files. In that case, for each reported problem you must still:
   - create a `ProblemDescriptor` whose `PsiElement` belongs to the specific file for that descriptor.

### Checklist to avoid this exception

When calling `holder.registerProblem(...)` in a local inspection visitor:

- [ ] Is `holder.getFile()` (or the file from the visitor parameter) equal to `element.getContainingFile()`?
- [ ] If not, change `element` to something in the current file (e.g. import/usage expression).
- [ ] Do all cross-file navigation / edits **inside the quickfix**, not in the problem registration.

If you share the code of `checkModuleExportsFromContainingPackage` (around the line shown in the stacktrace), I can point out the exact element that should be used for `registerProblem` in your `PyMissingInDunderAllInspection`.