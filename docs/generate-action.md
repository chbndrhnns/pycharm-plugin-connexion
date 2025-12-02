### Approach

To add a "Generate" action with options for Dataclass and Pydantic models, you should follow these steps:

1. **Define the Actions**: Create separate `AnAction` classes for each option (Dataclass, Frozen Dataclass, Pydantic,
   Frozen Pydantic) or a single action that prompts the user. A single action with a popup is often cleaner for the "
   Generate" menu (Cmd+N).
2. **Register in Plugin.xml**: Add your action to the `GenerateGroup` to make it appear in the standard "Generate"
   popup.
3. **Implement Code Generation**: Use `PyElementGenerator` to create the Python class structures and `AddImportHelper`
   to manage imports.
4. **Testing**: Use `LightPythonCodeInsightFixtureTestCase` to verify the generated code.

### Implementation Details

#### 1. Create the Action

You can create a single action that shows a selection popup.

```java
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import org.jetbrains.annotations.NotNull;

public class GeneratePythonTypeAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || !(file instanceof PyFile)) return;

        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(java.util.List.of(
                        "Dataclass", "Frozen Dataclass", "Pydantic BaseModel", "Frozen Pydantic BaseModel"))
                .setTitle("Generate Type")
                .setItemChosenCallback(selected -> generateCode(project, (PyFile) file, selected))
                .createPopup()
                .showInBestPositionFor(e.getDataContext());
    }

    private void generateCode(Project project, PyFile file, String type) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            String className = "MyType"; // You might want to prompt for this
            String classText = "";

            if (type.contains("Dataclass")) {
                AddImportHelper.addOrUpdateFromImportStatement(file, "dataclasses", "dataclass", null, AddImportHelper.ImportPriority.BUILTIN, null);
                String decorator = type.contains("Frozen") ? "@dataclass(frozen=True)" : "@dataclass";
                classText = decorator + "\nclass " + className + ":\n    pass";
            } else if (type.contains("Pydantic")) {
                AddImportHelper.addOrUpdateFromImportStatement(file, "pydantic", "BaseModel", null, AddImportHelper.ImportPriority.THIRD_PARTY, null);
                if (type.contains("Frozen")) {
                    // Pydantic v2 style
                    AddImportHelper.addOrUpdateFromImportStatement(file, "pydantic", "ConfigDict", null, AddImportHelper.ImportPriority.THIRD_PARTY, null);
                    classText = "class " + className + "(BaseModel):\n    model_config = ConfigDict(frozen=True)\n";
                } else {
                    classText = "class " + className + "(BaseModel):\n    pass";
                }
            }

            // Insert at end of file or at cursor
            file.add(generator.createFromText(LanguageLevel.getDefault(), PyClass.class, classText));
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(file instanceof PyFile);
    }
}
```

#### 2. Register the Action

In your `plugin.xml`, add the action to the `GenerateGroup`.

```xml

<actions>
    <action id="MyPlugin.GeneratePythonType" class="com.example.GeneratePythonTypeAction"
            text="Generate Python Type...">
        <add-to-group group-id="GenerateGroup" anchor="first"/>
    </action>
</actions>
```

### Adding Tests

Use `LightPythonCodeInsightFixtureTestCase` for testing. This allows you to simulate the editor and check the file
content after the action execution.

```java
import com.jetbrains.python.fixtures.PyTestCase; // Or LightPythonCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

public class GeneratePythonTypeActionTest extends PyTestCase {

    public void testGenerateDataclass() {
        myFixture.configureByText("test.py", "<caret>");

        // You might need to adjust the action logic to be testable without UI interaction
        // e.g. by creating separate actions for tests or checking TestMode flags.
        // For this example, assume we can trigger specific generation:

        myFixture.performEditorAction("MyPlugin.GeneratePythonType");
        // Note: If your action shows a UI popup, you'll need to mock it or use a Test structure
        // that calls the generation logic directly.

        // Verifying the output
        myFixture.checkResult("from dataclasses import dataclass\n\n@dataclass\nclass MyType:\n    pass");
    }
}
```

**Tip for Testing UI Actions**: Since `JBPopup` blocks tests, separate the generation logic into a `public` method or a
service that can be called directly in tests, or use `TestDialogManager` to mock UI responses if applicable. Ideally,
extract the generation logic into a helper class: `PythonTypeGenerator.generate(file, type)`.