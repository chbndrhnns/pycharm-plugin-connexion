### Testing the Usage Filtering Rule

To verify that your filtering logic works correctly, you should create a test case that:
1.  Sets up a Python file with usages inside and outside of type annotations.
2.  Finds all usages of a class programmatically.
3.  Applies your `UsageFilteringRule` with the filter enabled and disabled.
4.  Asserts that only the correct usages are visible.

Here is a complete, self-contained test class extending `PyTestCase`. It includes the mock implementations of your Action and Rule (as static inner classes) so you can run it immediately. In your actual project, you would import your real classes instead.

#### `PyTypeAnnotationFilteringRuleTest.java`

```java
package com.example.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageFilteringRule;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PyTypeAnnotationFilteringRuleTest extends PyTestCase {

    // --- Implementation of Action and Rule (Matches your solution) ---

    public static class PyShowTypeAnnotationsAction extends ToggleAction {
        private static final String PROPERTY_KEY = "PyShowOnlyTypeAnnotations";

        public static boolean isShowOnlyTypeAnnotations(@NotNull Project project) {
            return PropertiesComponent.getInstance(project).getBoolean(PROPERTY_KEY, false);
        }

        public static void setShowOnlyTypeAnnotations(@NotNull Project project, boolean state) {
            PropertiesComponent.getInstance(project).setValue(PROPERTY_KEY, state, false);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            return project != null && isShowOnlyTypeAnnotations(project);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            Project project = e.getProject();
            if (project != null) {
                setShowOnlyTypeAnnotations(project, state);
            }
        }
    }

    public static class PyTypeAnnotationFilteringRule implements UsageFilteringRule {
        private final Project myProject;

        public PyTypeAnnotationFilteringRule(@NotNull Project project) {
            myProject = project;
        }

        @Override
        public @NotNull String getActionId() {
            return "Python.ShowTypeAnnotations";
        }

        @Override
        public boolean isVisible(@NotNull Usage usage) {
            // If the filter is NOT active, show everything
            if (!PyShowTypeAnnotationsAction.isShowOnlyTypeAnnotations(myProject)) {
                return true;
            }

            // If filter IS active, only show usages inside type hints
            if (usage instanceof PsiElementUsage) {
                PsiElement element = ((PsiElementUsage) usage).getElement();
                if (element != null) {
                    TypeEvalContext context = TypeEvalContext.userInitiated(myProject, element.getContainingFile());
                    return PyTypingTypeProvider.isInsideTypeHint(element, context);
                }
            }
            return false;
        }
    }

    // --- Test Logic ---

    public void testFiltering() {
        // 1. Configure a file with mixed usages
        myFixture.configureByText("test.py",
                "class MyType: pass\n" +
                "def foo(x: MyType):\n" +      // Usage 1: Inside Type hint
                "    v = MyType()\n" +          // Usage 2: Constructor call (Normal usage)
                "    pass"
        );

        // 2. Find usages of 'MyType'
        PsiElement targetElement = myFixture.findElementByText("MyType", PyClass.class);
        Collection<UsageInfo> usageInfos = myFixture.findUsages(targetElement);
        
        // Convert UsageInfo to Usage objects (Rule expects Usage)
        List<Usage> usages = new ArrayList<>();
        for (UsageInfo info : usageInfos) {
            usages.add(new UsageInfo2UsageAdapter(info));
        }
        
        // Ensure we found at least the two usages we expect
        assertTrue("Should find at least 2 usages", usages.size() >= 2);

        PyTypeAnnotationFilteringRule rule = new PyTypeAnnotationFilteringRule(myFixture.getProject());

        // -----------------------------------------------------------------------
        // SCENARIO 1: Filter Enabled
        // -----------------------------------------------------------------------
        PyShowTypeAnnotationsAction.setShowOnlyTypeAnnotations(myFixture.getProject(), true);

        int visibleCount = 0;
        for (Usage usage : usages) {
            if (rule.isVisible(usage)) {
                visibleCount++;
                // Verify that the visible usage is indeed inside a type hint
                PsiElement usageElement = ((PsiElementUsage) usage).getElement();
                assertNotNull(usageElement);
                assertTrue("Visible usage should be inside type hint", 
                    PyTypingTypeProvider.isInsideTypeHint(usageElement, 
                        TypeEvalContext.codeAnalysis(myFixture.getProject(), usageElement.getContainingFile())));
            }
        }
        
        // Only the usage in "def foo(x: MyType):" should be visible
        assertEquals("Only usages in type hints should be visible when filter is ON", 1, visibleCount);

        // -----------------------------------------------------------------------
        // SCENARIO 2: Filter Disabled
        // -----------------------------------------------------------------------
        PyShowTypeAnnotationsAction.setShowOnlyTypeAnnotations(myFixture.getProject(), false);

        visibleCount = 0;
        for (Usage usage : usages) {
            if (rule.isVisible(usage)) {
                visibleCount++;
            }
        }

        // All usages should be visible now
        assertEquals("All usages should be visible when filter is OFF", usages.size(), visibleCount);
    }
}
```

### Key Test Components

*   **`PyTestCase`**: The base class for Python plugin tests, giving you access to `myFixture`.
*   **`UsageInfo2UsageAdapter`**: Used to convert the `UsageInfo` objects returned by `myFixture.findUsages()` into the `Usage` objects required by the filtering rule interface.
*   **`PropertiesComponent`**: We manually toggle the property to simulate the user clicking the action button.
*   **`PyTypingTypeProvider.isInsideTypeHint`**: This is the core logic we are testing, ensuring it correctly identifies the type hint context.