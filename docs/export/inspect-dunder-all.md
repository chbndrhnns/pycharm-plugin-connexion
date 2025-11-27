### 1. Inspection Implementation

You need to create a new inspection class `PyMissingInDunderAllInspection` that extends `PyInspection`.

This inspection has **two responsibilities**:

1. For any module that defines `__all__`, verify that all public top-level symbols in that module are listed there.
2. For **private modules** (file name starts with an underscore) that live inside a package which itself defines
   `__all__`, verify that all public top-level symbols from the private module are exported via the **package’s**
   `__all__`.

```java
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyMissingInDunderAllInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      super.visitPyFile(node);
      // 1) Check the module's own __all__ (if present)
      List<String> dunderAll = node.getDunderAll();
      if (dunderAll != null) {
        checkSymbolsExportedIn(node, dunderAll, ExportScope.MODULE, null);
      }

      // 2) If this is a *private* module inside a package that defines __all__,
      //    also require that its public symbols are exported from the *package*.
      if (node.getName() != null && node.getName().startsWith("_") && node instanceof PyFileImpl) {
        PyFileImpl fileImpl = (PyFileImpl) node;
        PyFile containingPackage = fileImpl.getContainingPackage();
        if (containingPackage != null) {
          List<String> packageAll = containingPackage.getDunderAll();
          if (packageAll != null) {
            checkSymbolsExportedIn(node, packageAll, ExportScope.PACKAGE, containingPackage);
          }
        }
      }
    }

    private enum ExportScope {
      MODULE,
      PACKAGE
    }

    private boolean isExportable(PyElement element) {
      return element instanceof PyClass ||
             element instanceof PyFunction ||
             (element instanceof PyTargetExpression && !PyNames.ALL.equals(element.getName()));
    }

    private void checkSymbolsExportedIn(@NotNull PyFile sourceFile,
                                        @NotNull List<String> dunderAll,
                                        @NotNull ExportScope scope,
                                        @Nullable PyFile packageFile) {
      String packageName = packageFile != null ? packageFile.getName() : null;

      for (PyElement element : sourceFile.iterateNames()) {
        if (!isExportable(element)) continue;

        String name = element.getName();
        if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) {
          continue;
        }

        if (!dunderAll.contains(name)) {
          PsiElement nameIdentifier = getNameIdentifier(element);
          if (nameIdentifier == null) continue;

          String message;
          LocalQuickFix[] fixes;

          if (scope == ExportScope.MODULE) {
            message = "Symbol '" + name + "' is not exported in __all__";
            fixes = new LocalQuickFix[]{new PyAddSymbolToAllQuickFix(name)};
          }
          else {
            // Package-level check: public symbols defined in a private module must be re-exported
            // from the package's __all__.
            String pkg = packageName != null ? packageName : "package";
            message = "Public symbol '" + name + "' from private module is not exported in __all__ of " + pkg;
            // For now we only highlight; a package-level quick fix can be added later.
            fixes = LocalQuickFix.EMPTY_ARRAY;
          }

          registerProblem(nameIdentifier, message, fixes);
        }
      }
    }

    private @Nullable PsiElement getNameIdentifier(PyElement element) {
      if (element instanceof PsiNameIdentifierOwner) {
        return ((PsiNameIdentifierOwner) element).getNameIdentifier();
      }
      return element;
    }
  }
}
```

### 2. QuickFix Implementation

The QuickFix will find the `__all__` assignment and append the missing symbol name to the list (or tuple).

```java
package com.jetbrains.python.inspections;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyAddSymbolToAllQuickFix extends PsiUpdateModCommandQuickFix {
  private final String myName;

  public PyAddSymbolToAllQuickFix(String name) {
    myName = name;
  }

  @Override
  public @NotNull String getFamilyName() {
    return "Add to __all__";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile)) return;

    PyAssignmentStatement dunderAllAssignment = findDunderAllAssignment((PyFile) file);
    if (dunderAllAssignment == null) return;

    PyExpression value = dunderAllAssignment.getAssignedValue();
    if (value instanceof PySequenceExpression) { // List or Tuple
      PyElementGenerator generator = PyElementGenerator.getInstance(project);
      PyStringLiteralExpression stringLiteral = generator.createStringLiteralFromString(myName);
      
      generator.insertItemIntoListRemoveRedundantCommas(
        (PyElement) value,
        null,
        stringLiteral
      );
    }
  }

  private @Nullable PyAssignmentStatement findDunderAllAssignment(PyFile file) {
    for (PyStatement stmt : file.getStatements()) {
      if (stmt instanceof PyAssignmentStatement) {
        PyAssignmentStatement assignment = (PyAssignmentStatement) stmt;
        for (PyExpression target : assignment.getTargets()) {
          if (PyNames.ALL.equals(target.getName())) {
            return assignment;
          }
        }
      }
    }
    return null;
  }
}
```

### 3. Testing

You should create a test class inheriting from `PyTestCase` (or `PythonInspectionsTest` if available in your hierarchy)
to verify the behavior.

```java
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyTestCase;

public class PyMissingInDunderAllInspectionTest extends PyTestCase {

  public void testMissingInAll() {
    doTest();
  }

  public void testPresentInAll() {
    doTest();
  }

  public void testNoDunderAll() {
    doTest();
  }

  public void testPrivateSymbol() {
    doTest();
  }

  /**
   * Private module (file starts with underscore) inside a package with __all__,
   * where the symbol is NOT exported from the package's __all__.
   */
  public void testPrivateModuleNotExportedFromPackage() {
    doTest();
  }

  /**
   * Private module inside a package with __all__, where the symbol *is*
   * exported from the package's __all__. No warning is expected.
   */
  public void testPrivateModuleExportedFromPackage() {
    doTest();
  }
  
  public void testAddToAllFix() {
    doTest(); // Checks quickfix application for module-level __all__.
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/" + getTestName(false) + ".py");
    myFixture.enableInspections(PyMissingInDunderAllInspection.class);
    myFixture.checkHighlighting(true, false, false);
    
    // Apply quickfix if available (heuristic for test name ending in 'Fix').
    // This only exercises the module-level quick fix; there is currently no
    // package-level quick fix wired for the private-module / package __all__ case.
    if (getTestName(false).contains("Fix")) {
        myFixture.getAllQuickFixes().forEach(fix -> {
            if (fix.getFamilyName().equals("Add to __all__")) {
                myFixture.launchAction(fix);
            }
        });
        myFixture.checkResultByFile("inspections/PyMissingInDunderAllInspection/" + getTestName(false) + "_after.py");
    }
  }
}
```

For the two new package-related tests, the test data layout could look like this:

```text
inspections/PyMissingInDunderAllInspection/
  pkg/
    __init__.py                      # defines package-level __all__
    _private_module_not_exported.py  # defines a public symbol, not mentioned in pkg __all__
    _private_module_exported.py      # defines a public symbol, re-exported via pkg __all__
```

- `testPrivateModuleNotExportedFromPackage` uses caret/highlight markers in
  `_private_module_not_exported.py` to assert that the public symbol defined in
  the private module is flagged as
  `Public symbol 'X' from private module is not exported in __all__ of pkg`.
- `testPrivateModuleExportedFromPackage` uses the same structure but ensures
  that when the symbol is listed in `pkg.__all__`, the inspection produces no
  warnings.

### 4. Edge Cases and Considerations

1. **Tuples vs Lists**: `__all__` can be a tuple `__all__ = ("a",)`. The `insertItemIntoListRemoveRedundantCommas`
   method usually handles sequence expressions (lists and tuples), but you should verify it supports tuples. If not, you
   might need to cast to `PyTupleExpression` and handle it specifically or convert it to a list (which changes semantics
   slightly but is usually fine).
2. **Complex Assignments**: `__all__` might be constructed dynamically (e.g., `__all__ = ["a"] + ["b"]`). The simple
   `getAssignedValue()` check might fail or return a `PyBinaryExpression`. The current fix assumes a literal list or
   tuple. For dynamic construction, the quickfix should probably bail out or try to append to the last list literal if
   possible, but it's safer to ignore complex cases.
3. **Ordering**: The quickfix appends to the end. Some projects prefer alphabetical order. Doing that requires parsing
   all elements, sorting, and re-creating the list, which is more invasive.
4. **Re-exports**: The inspection currently checks `node.iterateNames()`. This usually includes defined symbols. If you
   want to include imported symbols that are intended to be re-exported, you'd need to check `PyImportElement` usages,
   which is much harder to distinguish from regular imports. The current scope "symbol ... of the current package"
   suggests defined symbols.
5. **Type Aliases**: `TypeAlias` statements (PEP 695) should also be considered exportable. `PyTypeAliasStatement` is a
   `PyStatement` and `PsiNamedElement`. You might want to add `element instanceof PyTypeAliasStatement` to
   `isExportable`.

### 5. Registration

Register the inspection in your `plugin.xml` (or `python-inspections.xml`):

```xml
<extensions defaultExtensionNs="com.intellij">
  <localInspection language="Python"
                   shortName="PyMissingInDunderAllInspection"
                   displayName="Symbol not exported in __all__"
                   groupName="Python"
                   enabledByDefault="true"
                   level="WARNING"
                   implementationClass="com.jetbrains.python.inspections.PyMissingInDunderAllInspection"/>
</extensions>
```