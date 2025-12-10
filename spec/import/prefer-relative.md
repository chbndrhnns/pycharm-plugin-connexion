### Hooking into Auto-Import System

To hook into the auto-import system in PyCharm/IntelliJ Python plugin, you need to implement the `PyImportCandidateProvider` extension point. This allows you to provide custom import candidates, including relative imports.

Since `QualifiedName` uses dots as separators, you can represent a relative path like `.` or `.module` by using empty strings as components (e.g., `["", ""]` becomes `.` and `["", "module"]` becomes `.module`).

The `ImportCandidateHolder` sorts candidates by relevance and then by path. Since `.` (ASCII 46) comes before letters, a relative import candidate will naturally appear before an absolute one (e.g., `pkg.module`) if their relevance is the same.

#### 1. Implementation

Register the extension in your `plugin.xml`:
```xml
<extensions defaultExtensionNs="Pythonid">
    <importCandidateProvider implementation="com.example.plugin.RelativeImportCandidateProvider"/>
</extensions>
```

Create the provider class:

```java
package com.example.plugin;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

public class RelativeImportCandidateProvider implements PyImportCandidateProvider {
    @Override
    public void addImportCandidates(@NotNull PsiReference reference, @NotNull String name, @NotNull AutoImportQuickFix quickFix) {
        PsiFileSystemItem file = reference.getElement().getContainingFile();
        if (!(file instanceof PyFile)) return;

        PsiDirectory fileParent = file.getParent();
        if (fileParent == null || !PyUtil.isPackage(fileParent, file)) return;

        for (var candidate : quickFix.getCandidates()) {
            PsiNamedElement importable = candidate.getImportable();
            if (importable == null) continue;

            PsiFileSystemItem importableFile = importable.getContainingFile();
            // If the symbol is a module (e.g. pkg.mod), the file is the module itself.
            // If the symbol is a function/class, the file is the containing file.
            
            if (importableFile == null) continue;

            PsiDirectory importableParent = importableFile.getParent();
            
            // Check if they share the same direct parent package
            if (fileParent.equals(importableParent)) {
                QualifiedName relativePath = null;
                String importableFileName = FileUtil.getNameWithoutExtension(importableFile.getName());

                if (importable instanceof PyFile) {
                     // Importing a module sibling: from . import module
                     // Path "." represented as ["", ""]
                     relativePath = QualifiedName.fromComponents("", "");
                } else {
                    // Importing a symbol from a sibling module: from .module import symbol
                    // Path ".module" represented as ["", "module"]
                    // If importing from __init__.py (package itself), path is "." -> ["", ""]
                    if (PyUtil.isPackage(importableFile)) { // __init__.py
                        relativePath = QualifiedName.fromComponents("", "");
                    } else {
                        relativePath = QualifiedName.fromComponents("", importableFileName);
                    }
                }

                if (relativePath != null) {
                    // Add the relative import candidate
                    // AutoImportQuickFix handling of candidates will generally prefer this 
                    // due to alphabetical sorting of paths if relevance is equal.
                    quickFix.addImport(importable, file, relativePath);
                }
            }
        }
    }
}
```

#### 2. Test

To test this, you can use `PyAddImportQuickFixTest` logic, but since that class might be abstract or specific to the platform tests, here is a standalone test case pattern using `PyQuickFixTestCase` (standard for plugin testing).

```java
package com.example.plugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.fixtures.PyQuickFixTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;

public class RelativeImportTest extends PyQuickFixTestCase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    }

    public void testRelativeImportPreference() {
        // Create a sibling module with a symbol
        myFixture.addFileToProject("pkg/__init__.py", "");
        myFixture.addFileToProject("pkg/sibling.py", "def foo(): pass");

        // Create the file where we want to import
        myFixture.configureByText("pkg/main.py", "foo<caret>()");

        // Get available intentions
        var intentions = myFixture.filterAvailableIntentions("Import 'pkg.sibling.foo'");
        
        // Since we hooked in, we expect a relative import option to be available.
        // The exact text depends on how the quickfix renders it, usually "Import 'foo from .sibling'"
        // or simply it might be one of the candidates in the "Import" popup.
        
        // If there are multiple candidates, "Import this name" might be the action name, 
        // and executing it shows a popup. In tests, usually the best way is to check candidates directly 
        // or verify the text if it's a single action.
        
        // However, assuming our provider works, we can try to find the specific relative import action 
        // or verify the relative import is inserted when we chose the right candidate.
        
        // Let's verify that an action with relative path exists or is preferred.
        // If our relative candidate is the FIRST one, it might be automatically chosen if we invoke "Import".
        
        // Check for the existence of the relative import candidate string in available intentions 
        // (if the UI exposes it directly) or check the result of applying the fix.
        
        // For this test, we'll simulate choosing the relative one if multiple exist, 
        // or asserting it is present.
        
        boolean relativeOptionFound = false;
        for (IntentionAction action : myFixture.getAvailableIntentions()) {
            // The text might be "Import 'foo from .sibling'" or similar
            if (action.getText().contains("from .sibling")) {
                relativeOptionFound = true;
                myFixture.launchAction(action);
                break;
            }
        }
        
        // If it wasn't a separate action but a popup, we might need to inspect the QuickFix object directly.
        // But often distinct import paths appear as distinct intentions or sub-options.
        
        // If the "Import" action triggers a popup, we can't easily test the popup content with simple myFixture methods.
        // Instead, we can verify the result of applying the fix if we can select it.
        
        // Alternative: Verify the provider logic directly
        // accessing the AutoImportQuickFix on the element
        
        // Let's verify the code matches expectation after import (assuming we found and clicked it)
        if (relativeOptionFound) {
            myFixture.checkResult("from .sibling import foo\n\nfoo()");
        } else {
            // Fallback: Try to invoke the standard 'Import' and see if it picked the relative one (preference)
             IntentionAction importAction = myFixture.findSingleIntention("Import");
             myFixture.launchAction(importAction);
             // NOTE: This assertion might fail if the absolute one is still preferred by some internal logic,
             // but based on sorting, relative should be first.
             myFixture.checkResult("from .sibling import foo\n\nfoo()");
        }
    }
}
```