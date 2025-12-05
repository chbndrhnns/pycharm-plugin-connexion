To add an action to the **Refactor This** context menu (and the main **Refactor** menu) in a PyCharm plugin, you must follow a specific implementation pattern. The platform filters the "Refactor This" popup to only include actions that extend specific classes.

### Steps to Implement

1.  **Create an Action Class**
    Your action class **must** extend `com.intellij.refactoring.actions.BaseRefactoringAction`. This is a requirement for the action to appear in the "Refactor This" popup.

    ```java
    package com.example.plugin;

    import com.intellij.lang.Language;
    import com.intellij.openapi.actionSystem.DataContext;
    import com.intellij.openapi.editor.Editor;
    import com.intellij.psi.PsiElement;
    import com.intellij.psi.PsiFile;
    import com.intellij.refactoring.RefactoringActionHandler;
    import com.intellij.refactoring.actions.BaseRefactoringAction;
    import org.jetbrains.annotations.NotNull;
    import org.jetbrains.annotations.Nullable;

    public class MyRefactoringAction extends BaseRefactoringAction {

        @Override
        protected boolean isAvailableInEditorOnly() {
            // Return true if the refactoring is only available in the editor
            return false;
        }

        @Override
        protected boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
            // Check if the refactoring applies to the selected elements (e.g. in Project View)
            return elements.length > 0;
        }
        
        @Override
        protected boolean isAvailableForLanguage(Language language) {
            // Optional: Filter by language (e.g., Python)
            return "Python".equals(language.getID());
        }

        @Override
        protected @Nullable RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
            // Return your handler implementation
            return new MyRefactoringHandler();
        }
    }
    ```

2.  **Implement the Handler**
    Create a class implementing `com.intellij.refactoring.RefactoringActionHandler` to perform the actual refactoring logic.

    ```java
    package com.example.plugin;

    import com.intellij.openapi.actionSystem.DataContext;
    import com.intellij.openapi.editor.Editor;
    import com.intellij.openapi.project.Project;
    import com.intellij.psi.PsiElement;
    import com.intellij.psi.PsiFile;
    import com.intellij.refactoring.RefactoringActionHandler;
    import org.jetbrains.annotations.NotNull;
    import org.jetbrains.annotations.Nullable;

    public class MyRefactoringHandler implements RefactoringActionHandler {
        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
            // Handle refactoring invoked from editor
        }

        @Override
        public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
            // Handle refactoring invoked from other views (e.g. Project View)
        }
    }
    ```

3.  **Register the Action**
    In your `plugin.xml`, register the action and add it to the `RefactoringMenu` group. This group is used by both the main menu and the "Refactor This" popup.

    ```xml
    <actions>
        <action id="MyRefactoringAction" 
                class="com.example.plugin.MyRefactoringAction" 
                text="My Refactoring..." 
                description="Performs my custom refactoring">
            <!-- Add to the Refactor menu -->
            <add-to-group group-id="RefactoringMenu" anchor="last"/>
        </action>
    </actions>
    ```

### Why `BaseRefactoringAction`?
The "Refactor This" popup (`Refactorings.QuickListPopupAction`) specifically filters actions. It iterates over the `RefactoringMenu` group and **only** displays actions that are instances of:
*   `BaseRefactoringAction`
*   `RenameElementAction`
*   `CopyElementAction`

If you use a standard `AnAction`, it will appear in the main "Refactor" menu but **not** in the "Refactor This" popup (Ctrl+T / Ctrl+Alt+Shift+T).

### Helper for Python
If your plugin depends on the Python plugin (`com.intellij.modules.python`), you can alternatively extend `com.jetbrains.python.refactoring.PyBaseRefactoringAction`. This class provides Python-specific helper methods (like `isAvailableForLanguage` pre-configured for Python) but essentially works the same way as `BaseRefactoringAction`.