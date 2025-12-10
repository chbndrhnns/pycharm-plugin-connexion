### Approach

To parse `pytest` identifiers in "Search Everywhere", you should implement a custom `SearchEverywhereContributor`.

The standard "Symbols" tab uses `ChooseByNameContributor`, which relies on matching a prefix against a pre-indexed list of names. This architecture does not support parsing arbitrary patterns like `file::class::method`. Therefore, you cannot simply extend the existing "Symbols" tab logic.

Instead, you should register a new `SearchEverywhereContributor` that:
1.  Parses the input string to extract the file path and symbol chain.
2.  Resolves the file in the project.
3.  Traverses the PSI to find the target symbol (class or function).

While you can technically set the group name to "Symbols", the platform might still render it as a separate tab or section. A dedicated "Pytest" tab or merging into "All" is the recommended user experience.

### Entrypoints

*   **Extension Point**: `com.intellij.searchEverywhereContributor`
*   **Interface**: `com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor`
*   **Factory**: `com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory`

### Implementation

Here is a complete implementation plan.

#### 1. The Contributor

This class parses the `::` pattern and resolves the element.

```java
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PytestIdentifierContributor implements WeightedSearchEverywhereContributor<PsiElement> {

    private final Project myProject;

    public PytestIdentifierContributor(Project project) {
        myProject = project;
    }

    @NotNull
    @Override
    public String getSearchProviderId() {
        return "PytestIdentifierContributor";
    }

    @NotNull
    @Override
    public String getGroupName() {
        return "Pytest"; // Or "Symbols" if you want to try to merge
    }

    @Override
    public int getSortWeight() {
        return 500;
    }

    @Override
    public boolean showInFindResults() {
        return true;
    }

    @Override
    public void fetchWeightedElements(@NotNull String pattern,
                                      @NotNull ProgressIndicator progressIndicator,
                                      @NotNull Processor<? super FoundItemDescriptor<PsiElement>> consumer) {
        if (!pattern.contains("::")) {
            return;
        }

        ApplicationManager.getApplication().runReadAction(() -> {
            PsiElement element = resolvePytestIdentifier(pattern);
            if (element != null) {
                // High weight to appear at the top
                consumer.process(new FoundItemDescriptor<>(element, 1000));
            }
        });
    }

    private @Nullable PsiElement resolvePytestIdentifier(String pattern) {
        // Simple splitting by ::
        List<String> parts = StringUtil.split(pattern, "::");
        if (parts.isEmpty()) return null;

        String filePath = parts.get(0);
        
        // Find file (assuming relative path from project root as per pytest output)
        VirtualFile vFile = myProject.getBaseDir().findFileByRelativePath(filePath);
        if (vFile == null || !vFile.exists()) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
        if (!(psiFile instanceof PyFile)) {
            return null;
        }

        PsiElement currentElement = psiFile;

        // Iterate over symbols
        for (int i = 1; i < parts.size(); i++) {
            String symbolName = parts.get(i);
            // Handle parametrization suffix like test_foo[1-2]
            symbolName = stripParametrization(symbolName);
            
            if (currentElement instanceof PyFile) {
                currentElement = ((PyFile) currentElement).findTopLevelAttribute(symbolName);
                if (currentElement == null) {
                    // Try class or function
                    currentElement = ((PyFile) psiFile).findTopLevelClass(symbolName);
                }
                if (currentElement == null) {
                    currentElement = ((PyFile) psiFile).findTopLevelFunction(symbolName);
                }
            } else if (currentElement instanceof PyClass) {
                currentElement = ((PyClass) currentElement).findMethodByName(symbolName, false);
                // Could also look for nested classes if needed
            } else {
                return null;
            }

            if (currentElement == null) return null;
        }

        return currentElement;
    }

    private String stripParametrization(String name) {
        // Remove trailing [...]
        int bracketIndex = name.indexOf('[');
        if (bracketIndex > 0 && name.endsWith("]")) {
            return name.substring(0, bracketIndex);
        }
        return name;
    }

    @Override
    public boolean processSelectedItem(@NotNull PsiElement selected, int modifiers, @NotNull String searchText) {
        // Default navigation
        return ((com.intellij.navigation.NavigationItem) selected).navigate(true);
    }

    @NotNull
    @Override
    public ListCellRenderer<? super PsiElement> getElementsRenderer() {
        return new com.intellij.ide.util.PsiElementListCellRenderer<>() {
            @Override
            public String getElementText(PsiElement element) {
                if (element instanceof PyFunction) return ((PyFunction) element).getName();
                if (element instanceof PyClass) return ((PyClass) element).getName();
                return element.toString();
            }

            @Nullable
            @Override
            protected String getContainerText(PsiElement element, String name) {
                PsiFile file = element.getContainingFile();
                return file != null ? file.getName() : null;
            }
        };
    }
    
    // ... implement other abstract methods (getDataForItem, etc.) ...
    @Override
    public @Nullable Object getDataForItem(@NotNull PsiElement element, @NotNull String dataId) {
        return null; 
    }
    
    public static class Factory implements SearchEverywhereContributorFactory<PsiElement> {
        @NotNull
        @Override
        public SearchEverywhereContributor<PsiElement> createContributor(@NotNull AnActionEvent initEvent) {
            return new PytestIdentifierContributor(initEvent.getProject());
        }
    }
}
```

#### 2. Registration (plugin.xml)

```xml
<extensions defaultExtensionNs="com.intellij">
    <searchEverywhereContributor implementation="com.yourpackage.PytestIdentifierContributor$Factory"/>
</extensions>
```

#### 3. Testing

Use `BasePlatformTestCase` or `PyTestCase` (if available in your test classpath) to verify the logic.

```kotlin
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyFunction
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PytestContributorTest : BasePlatformTestCase() {

    fun `test resolve plain method`() {
        myFixture.addFileToProject("tests/test_foo.py", """
            class TestClass:
                def test_method(self):
                    pass
        """.trimIndent())

        val contributor = PytestIdentifierContributor(project)
        val results = search(contributor, "tests/test_foo.py::TestClass::test_method")

        assertSize(1, results)
        val element = results[0].item
        assertTrue(element is PyFunction)
        assertEquals("test_method", (element as PyFunction).name)
    }

    fun `test resolve parametrized`() {
        myFixture.addFileToProject("tests/test_bar.py", """
            def test_param():
                pass
        """.trimIndent())

        val contributor = PytestIdentifierContributor(project)
        // Simulate input with parametrization suffix
        val results = search(contributor, "tests/test_bar.py::test_param[1-2-3]")

        assertSize(1, results)
        val element = results[0].item
        assertTrue(element is PyFunction)
        assertEquals("test_param", (element as PyFunction).name)
    }

    private fun search(contributor: SearchEverywhereContributor<*>, pattern: String): List<FoundItemDescriptor<*>> {
        val results = ArrayList<FoundItemDescriptor<*>>()
        val latch = CountDownLatch(1)
        
        // Mock processor
        val processor = Processor<FoundItemDescriptor<*>> { 
            results.add(it)
            true 
        }

        contributor.fetchWeightedElements(pattern, EmptyProgressIndicator(), processor)
        
        return results
    }
}
```