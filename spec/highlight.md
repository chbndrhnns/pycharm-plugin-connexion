### How PyCharm Highlights Affected Code During Refactoring

The visual indication of affected code in PyCharm (e.g., when renaming a variable or introducing a parameter) is
primarily achieved through the **Inplace Refactoring** framework, which leverages **Live Templates** to highlight usages
and allow simultaneous editing.

#### 1. The Mechanism

The core functionality relies on the following platform components:

* **`InplaceRefactoring`** (and its subclasses like `InplaceVariableIntroducer`): This is the logic controller. It
  identifies the "primary variable" (the one you are editing) and its occurrences.
* **`TemplateManager`**: It creates a temporary "Live Template" session. The primary element and its occurrences are
  treated as variables in this template, which is why they update synchronously as you type.
* **`HighlightManager`**: It is responsible for painting the colored boxes (background attributes) around the affected
  code regions.
    * **Primary Element**: Highlighted with `EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES`.
    * **Occurrences**: Highlighted with `EditorColors.SEARCH_RESULT_ATTRIBUTES`.

#### 2. How to Use It in a Plugin

To implement a similar feature in your plugin, you should use the `com.intellij.refactoring.introduce.inplace` package.

**Scenario: "Introduce" style refactoring (like Extract Variable)**
You should subclass `InplaceVariableIntroducer<PsiElement>`.

**Steps:**

1. **Collect Occurrences**: Find all `PsiElement` instances that should be highlighted/renamed.
2. **Create Handler**: Instantiate your `InplaceVariableIntroducer` subclass.
3. **Execute**: Call `performInplaceRefactoring()`.

**Code Example:**

```java
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

public class MyInplaceIntroducer extends InplaceVariableIntroducer<PsiElement> {

    public MyInplaceIntroducer(PsiNamedElement elementToRename,
                               Editor editor,
                               Project project,
                               String title,
                               List<PsiElement> occurrences) {
        super(elementToRename, editor, project, title,
                occurrences.toArray(PsiElement.EMPTY_ARRAY), null);
    }

    @Override
    protected PsiElement checkLocalScope() {
        // Return the scope (e.g., current file) where the refactoring is valid
        return myElementToRename.getContainingFile();
    }

    // Usage method
    public static void start(PsiNamedElement target, Editor editor, List<PsiElement> occurrences) {
        MyInplaceIntroducer introducer = new MyInplaceIntroducer(
                target,
                editor,
                target.getProject(),
                "My Refactoring",
                occurrences
        );

        // passing suggested names for the variable
        introducer.performInplaceRefactoring(new LinkedHashSet<>());
    }
}
```

**Scenario: Just Highlighting (No Renaming)**
If you only want to show the "affected code" visual effect without the interactive renaming, you can use
`HighlightManager` directly:

```java
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.TextAttributes;

// ... inside your action
HighlightManager highlightManager = HighlightManager.getInstance(project);
        TextAttributes attributes = EditorColors.SEARCH_RESULT_ATTRIBUTES.getDefaultAttributes();

// Add highlights
highlightManager.

        addOccurrenceHighlight(
                editor,
                startOffset,
                endOffset,
                attributes, 
    0,
                outHighlighters, 
    null
        );
```

#### Relevant Files in IntelliJ Community

For reference, you can explore these files in the IntelliJ Community repository:

* `platform/lang-impl/src/com/intellij/refactoring/introduce/inplace/InplaceVariableIntroducer.java`: The base class for
  introduce refactorings.
* `platform/lang-impl/src/com/intellij/refactoring/rename/inplace/InplaceRefactoring.java`: The core logic handling
  templates and highlighting.
* `python/src/com/jetbrains/python/refactoring/introduce/PyIntroduceHandlerUi.java`: A Python-specific implementation
  example showing how `InplaceVariableIntroducer` is extended and used.