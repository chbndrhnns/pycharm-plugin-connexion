### Enabling File-Level Suppression for a Custom Plugin Inspection

To enable "Suppress for file" for your custom Python inspection (or any inspection in a plugin), you need to handle both the **logic** to check for the suppression comment and the **UI action** to add that comment.

Since the standard `PyInspectionsSuppressor` might not yet support file-level suppression (or you want to ensure it works for your specific tool), you should register your own `InspectionSuppressor` extension. IntelliJ IDEA allows multiple suppressors for the same language, and they function additively.

Here is the complete solution:

#### 1. Register the Extension
In your `plugin.xml`, register a custom inspection suppressor for Python.

```xml
<extensions defaultExtensionNs="com.intellij">
    <lang.inspectionSuppressor language="Python"
                               implementationClass="com.example.plugin.MyPythonInspectionSuppressor"/>
</extensions>
```

#### 2. Implement the Inspection Suppressor
Create `MyPythonInspectionSuppressor`. This class will:
1.  **Check** if the file header contains a suppression comment for the given tool ID (`isSuppressedFor`).
2.  **Provide** a QuickFix to add that comment (`getSuppressActions`).

```java
package com.example.plugin;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyPythonInspectionSuppressor implements InspectionSuppressor {
    private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        // 1. Check if the element is within a Python file
        PsiFile file = element.getContainingFile();
        if (file instanceof PyFile) {
            // 2. Check if the file itself is suppressed via a header comment
            return isSuppressedInFile((PyFile) file, toolId);
        }
        return false;
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        // Only offer this fix if we are in a Python file
        if (element != null && element.getContainingFile() instanceof PyFile) {
            return new SuppressQuickFix[]{
                new SuppressForFileFix(toolId)
            };
        }
        return SuppressQuickFix.EMPTY_ARRAY;
    }

    private static boolean isSuppressedInFile(@NotNull PyFile file, @NotNull String toolId) {
        PsiElement child = file.getFirstChild();
        // Scan comments at the beginning of the file
        while (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
            if (child instanceof PsiComment) {
                String text = child.getText();
                // Remove the leading '#'
                String commentContent = text.startsWith("#") ? text.substring(1).trim() : text;
                if (isSuppressedInComment(commentContent, toolId)) {
                    return true;
                }
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private static boolean isSuppressedInComment(@NotNull String commentText, @NotNull String suppressId) {
        Matcher m = SUPPRESS_PATTERN.matcher(commentText);
        return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), suppressId);
    }
}
```

#### 3. Implement the QuickFix
Create the `SuppressForFileFix` class to handle the insertion of the comment.

```java
package com.example.plugin;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiParserFacade;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

public class SuppressForFileFix implements SuppressQuickFix {
    private final String myToolId;

    public SuppressForFileFix(String toolId) {
        myToolId = toolId;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Suppress for a file";
    }

    @Override
    public @NotNull String getName() {
        return "Suppress for file";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
        return context.getContainingFile() instanceof PyFile;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element == null) return;
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PyFile)) return;

        String commentText = "# noinspection " + myToolId;
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PsiComment newComment = generator.createFromText(Project.DIRECTORY_STORE_FOLDER, PsiComment.class, commentText);
        PsiElement newLine = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n");

        PsiElement firstChild = file.getFirstChild();
        if (firstChild != null) {
            file.addBefore(newComment, firstChild);
            file.addBefore(newLine, firstChild);
        } else {
            file.add(newComment);
            file.add(newLine);
        }
    }
}
```

### How it works
1.  **Additive Behavior:** IntelliJ will query both the standard `PyInspectionsSuppressor` and your `MyPythonInspectionSuppressor`.
2.  **Logic:** When checking if an inspection should be shown, your suppressor checks the file header. If it finds the `# noinspection <ID>`, it returns `true`, preventing the warning.
3.  **UI:** Your `getSuppressActions` adds the "Suppress for file" option to the menu alongside the standard statement-level options provided by the platform.