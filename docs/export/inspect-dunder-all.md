### 1. Inspection Implementation

You need to create a new inspection class `PyMissingInDunderAllInspection` that extends `PyInspection`. This inspection
will visit the file, check if `__all__` is defined, and then verify if all public top-level symbols are included in it.

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
      List<String> dunderAll = node.getDunderAll();
      if (dunderAll == null) {
        return;
      }

      for (PyElement element : node.iterateNames()) {
        if (!isExportable(element)) continue;

        String name = element.getName();
        if (name == null || StringUtil.isEmpty(name) || name.startsWith("_")) {
          continue;
        }

        if (!dunderAll.contains(name)) {
          PsiElement nameIdentifier = getNameIdentifier(element);
          if (nameIdentifier != null) {
            registerProblem(nameIdentifier,
                            "Symbol '" + name + "' is not exported in __all__",
                            new PyAddSymbolToAllQuickFix(name));
          }
        }
      }
    }

    private boolean isExportable(PyElement element) {
      return element instanceof PyClass ||
             element instanceof PyFunction ||
             (element instanceof PyTargetExpression && !PyNames.ALL.equals(element.getName()));
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
  
  public void testAddToAllFix() {
    doTest(); // Checks quickfix application
  }

  private void doTest() {
    myFixture.configureByFile("inspections/PyMissingInDunderAllInspection/" + getTestName(false) + ".py");
    myFixture.enableInspections(PyMissingInDunderAllInspection.class);
    myFixture.checkHighlighting(true, false, false);
    
    // Apply quickfix if available (heuristic for test name ending in 'Fix')
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