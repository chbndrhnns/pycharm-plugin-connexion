### Implementation for a Community Plugin

Since you are developing a separate plugin (or extension) and cannot modify the core `PyImportCollector` directly, you should use the `com.jetbrains.python.unresolvedReferenceQuickFixProvider` extension point. This allows you to provide custom "Quick Fixes" (Alt+Enter actions) for unresolved references without altering the core IDE code.

Here is the complete implementation strategy using the IntelliJ Platform SDK and Python plugin APIs.

#### 1. Register the Extension

In your `plugin.xml`, register the extension point:

```xml
<extensions defaultExtensionNs="Pythonid">
  <unresolvedReferenceQuickFixProvider implementation="com.example.plugin.NestedClassImportProvider"/>
</extensions>
```

#### 2. Implement the Provider

Create `NestedClassImportProvider` to search for nested classes and register a fix.

```java
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.intellij.psi.PsiReference;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiElement;
import java.util.Collection;
import java.util.List;

public class NestedClassImportProvider implements PyUnresolvedReferenceQuickFixProvider {
    @Override
    public void registerQuickFixes(PsiReference reference, List<LocalQuickFix> fixes) {
        if (!(reference instanceof PyReferenceExpression)) return;
        PyReferenceExpression refExpr = (PyReferenceExpression) reference;
        
        // Only handle unqualified names (e.g. "Nested" not "Top.Nested")
        if (refExpr.isQualified()) return;
        
        String name = refExpr.getReferencedName();
        if (name == null) return;

        PsiElement element = reference.getElement();
        GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(element);
        
        // PyClassNameIndex includes nested classes by default
        Collection<PyClass> classes = PyClassNameIndex.find(name, element.getProject(), scope);
        
        for (PyClass pyClass : classes) {
            // Filter: Must be a nested class (not top-level)
            if (PyUtil.isTopLevel(pyClass)) continue;
            
            // Find the Top-Level Parent
            PyClass topLevelParent = getTopLevelParent(pyClass);
            if (topLevelParent == null) continue;
            
            // Avoid suggesting if the parent is defined in the same file (unless you want to qualify local usages)
            if (topLevelParent.getContainingFile() == element.getContainingFile()) continue;

            fixes.add(new ImportNestedClassQuickFix(refExpr, topLevelParent, pyClass));
        }
    }

    private PyClass getTopLevelParent(PyClass nested) {
        PyClass current = nested;
        while (true) {
            PyClass parent = com.intellij.psi.util.PsiTreeUtil.getParentOfType(current, PyClass.class);
            if (parent == null) return current; // current is the top-most class found
            current = parent;
        }
    }
}
```

#### 3. Implement the Quick Fix

The fix needs to:
1. Import the top-level parent class.
2. Update the reference in the code to be fully qualified (e.g., change `Nested` to `Top.Nested`).

```java
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

public class ImportNestedClassQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PyReferenceExpression> myRefPtr;
    private final SmartPsiElementPointer<PyClass> myTopLevelPtr;
    private final String myNestedName;

    public ImportNestedClassQuickFix(PyReferenceExpression ref, PyClass topLevel, PyClass nested) {
        SmartPointerManager manager = SmartPointerManager.getInstance(ref.getProject());
        myRefPtr = manager.createSmartPsiElementPointer(ref);
        myTopLevelPtr = manager.createSmartPsiElementPointer(topLevel);
        myNestedName = nested.getName();
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Import nested class";
    }

    @Override
    public @NotNull String getName() {
        PyClass topLevel = myTopLevelPtr.getElement();
        if (topLevel == null) return getFamilyName();
        return "Import '" + topLevel.getName() + "." + myNestedName + "'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PyReferenceExpression ref = myRefPtr.getElement();
        PyClass topLevel = myTopLevelPtr.getElement();
        if (ref == null || topLevel == null) return;

        // 1. Add Import for TopLevel
        // logic to prefer simple import or from-import based on style
        AddImportHelper.addImportStatement(ref.getContainingFile(), topLevel.getName(), null, 
                                           AddImportHelper.ImportPriority.THIRD_PARTY, ref);

        // 2. Qualify the reference: "Nested" -> "TopLevel.Nested"
        // We need to construct the qualified path relative to the top-level class.
        // For deep nesting (A.B.C), importing A requires usage A.B.C
        String qualifiedUsage = getQualifiedPath(topLevel, myNestedName); 
        
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyReferenceExpression newRef = generator.createExpressionFromText(
                com.jetbrains.python.psi.LanguageLevel.forElement(ref), 
                qualifiedUsage
        );
        ref.replace(newRef);
    }
    
    private String getQualifiedPath(PyClass topLevel, String nestedName) {
         // Simplified logic: assume direct child for this example. 
         // For deep nesting, you'd calculate the path from topLevel to nested.
         return topLevel.getName() + "." + nestedName;
    }
}
```

### Edge Cases to Handle

1.  **Deep Nesting**: If you have `class A: class B: class C`, and the user types `C`, you must import `A` and replace `C` with `A.B.C`. Your `getQualifiedPath` method logic needs to traverse down from `A` or up from `C` to build this string.
2.  **Existing Imports**: `AddImportHelper` handles this gracefully. If `A` is already imported, it won't add a duplicate import. However, your fix **must** still qualify the reference (change `C` to `A.B.C`).
3.  **Name Collisions**: If `TopLevel` (e.g., `Config`) is already imported from another module, `AddImportHelper` might not handle aliasing automatically for your custom usage string. You might need to check if `TopLevel` is already visible in the file scope and if it resolves to a different symbol.

### Test Cases

You should implement a test extending `PyQuickFixTestCase`.

```java
public class NestedClassImportTest extends PyQuickFixTestCase {

    // Test Case 1: Basic Nested Import
    public void testImportNestedClass() {
        myFixture.configureByText("pkg.py", "class Top:\n    class Nested: pass");
        myFixture.configureByText("main.py", "x = <caret>Nested()");
        
        // This triggers your provider
        myFixture.launchAction(myFixture.findSingleIntention("Import 'Top.Nested'")); 
        
        myFixture.checkResult("import pkg\n\nx = pkg.Top.Nested()"); 
        // OR depending on import style:
        // myFixture.checkResult("from pkg import Top\n\nx = Top.Nested()");
    }

    // Test Case 2: Deep Nesting
    public void testDeeplyNested() {
        myFixture.configureByText("pkg.py", "class A: class B: class C: pass");
        myFixture.configureByText("main.py", "x = <caret>C()");
        
        myFixture.launchAction(myFixture.findSingleIntention("Import 'A.C'")); // Title depends on your getName implementation
        
        myFixture.checkResult("from pkg import A\n\nx = A.B.C()");
    }
}
```