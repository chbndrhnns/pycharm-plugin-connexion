### Goal
Write functional tests that start the in‑editor rename UI (In‑place Rename), simulate user typing, and assert both that the inline session actually started and that the resulting PSI/text is correct.

---

### Recommended test base
- Use `BasePlatformTestCase` (Kotlin) or `LightJavaCodeInsightFixtureTestCase` (Java). These give you `myFixture`/`fixture` with an `Editor`, PSI access, typing helpers, and utilities for pumping the EDT.
- Keep tests headless. Inline rename runs on the EDT and uses Live Templates under the hood. Ensure your test runs in EDT or uses helpers that do so.

---

### Minimal working example (Kotlin)
```kotlin
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.EdtTestUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.refactoring.RefactoringSettings

class InplaceRenameTest : BasePlatformTestCase() {
  fun `test inline rename starts and applies new name`() {
    // Given a file where in‑place rename is available at <caret>
    val file = myFixture.configureByText(
      "Foo.kt",
      """
        class Foo {
          fun bar() {
            val x<caret>yz = 1
            println(xyz)
          }
        }
      """.trimIndent()
    )

    val editor = myFixture.editor
    val project = project

    // Optional: set default options that the floating toolbar would toggle
    RefactoringSettings.getInstance().apply {
      RENAME_SEARCH_IN_COMMENTS = true
      RENAME_SEARCH_FOR_TEXT_OCCURRENCES = false
    }

    // When: trigger the same handler the Rename action uses
    startInlineRename(project, editor, file)

    // Then: assert that an inline rename (live template) started
    TemplateManagerImpl.getTemplateState(editor).let { state ->
      assertNotNull("Inline rename should start a live template", state)
    }

    // Simulate user typing the new name and committing with Enter
    myFixture.type("newName")
    myFixture.finishTemplate(editor)

    // Ensure documents are committed
    myFixture.checkResult(
      """
        class Foo {
          fun bar() {
            val newName = 1
            println(newName)
          }
        }
      """.trimIndent()
    )
  }

  private fun startInlineRename(project: Project, editor: Editor, file: PsiFile) {
    val base = myFixture.getViewComponent()
    val dataContext: DataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_FILE, file)
      .setParent(
        // fall back to the component's context if needed
        com.intellij.ide.DataManager.getInstance().getDataContext(base)
      )
      .build()

    EdtTestUtil.runInEdtAndWait {
      val handler: RenameHandler? = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)
      assertNotNull("No RenameHandler for context (caret must be on a renamable element)", handler)
      handler!!.invoke(project, editor, file, dataContext)
    }
  }
}
```

Notes:
- We assert that inline rename really started by checking the live‐template `TemplateState` in the editor. In‑place rename uses the Live Templates engine.
- `myFixture.finishTemplate(editor)` presses Enter for you; alternatively, you can `myFixture.type("\n")`.

---

### Java variant (trimmed)
```java
public class InplaceRenameTestJava extends LightJavaCodeInsightFixtureTestCase {
  public void testInlineRename() {
    PsiFile file = myFixture.configureByText("A.java", 
      "class A { void m(){ int ab<caret>c = 1; System.out.println(abc); } }");

    RefactoringSettings s = RefactoringSettings.getInstance();
    s.RENAME_SEARCH_IN_COMMENTS = true;
    s.RENAME_SEARCH_FOR_TEXT_OCCURRENCES = false;

    Editor editor = myFixture.getEditor();
    Project project = getProject();

    SimpleDataContext ctx = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, editor)
      .add(CommonDataKeys.PSI_FILE, file)
      .setParent(DataManager.getInstance().getDataContext(myFixture.getViewComponent()))
      .build();

    EdtTestUtil.runInEdtAndWait(() -> {
      RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(ctx);
      assertNotNull("RenameHandler not found", handler);
      handler.invoke(project, editor, file, ctx);
    });

    assertNotNull("Inline rename didn't start",
      TemplateManagerImpl.getTemplateState(editor));

    myFixture.type("newName");
    myFixture.finishTemplate(editor);

    myFixture.checkResult("class A { void m(){ int newName = 1; System.out.println(newName); } }");
  }
}
```

---

### Verifying that the floating toolbar options are respected
You generally don’t assert the visual popup in unit tests; instead, assert the behavior the options control.
- Pre‑set `RefactoringSettings` flags before invoking the handler:
  - `RENAME_SEARCH_IN_COMMENTS`
  - `RENAME_SEARCH_FOR_TEXT_OCCURRENCES`
- Put occurrences in comments/strings in your test file(s) and verify they change (or not) after finishing the template.

Example snippet inside a multi‑file test:
```kotlin
RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS = true
// file text contains // xyz in comment
// After inline rename, assert the comment updated if your element type supports text occurrences
```

If you must assert popup/menu interactions, wrap with `UiInterceptors` to intercept `JBPopup`/dialogs, but for inline rename this is rarely necessary—prefer asserting the resulting code changes.

---

### Useful tips and pitfalls
- Inline vs dialog: Inline starts only if a `RefactoringSupportProvider`/`RenamePsiElementProcessor` for the element says it’s available. Place the caret on a symbol that supports in‑place rename (local variable, parameter, etc.). Otherwise the classic dialog appears.
- EDT: Start the handler on the EDT (`EdtTestUtil.runInEdtAndWait`) and occasionally pump events (`myFixture.doHighlighting()` or `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()`) if your test waits for UI state.
- Document commits: After finishing the template, ensure PSI is committed (fixture helpers already do this). If you manipulate documents directly, call `PsiDocumentManager.getInstance(project).commitAllDocuments()`.
- Cross‑file checks: Use `myFixture.configureByFiles(...)` and `myFixture.checkResultByFile(...)` for multi‑file rename propagation.
- Focusing the editor: `myFixture.openFileInEditor(file.virtualFile)` ensures the editor is active before invoking the handler.
- Guard for missing handler: If `RenameHandlerRegistry` returns `null`, confirm your test’s `DataContext` contains `CommonDataKeys.EDITOR` and `PSI_FILE`, and the caret is on a renamable PSI.

---

### When a direct renamer is preferable
If you specifically test a variable-like element and don’t need the registry routing, you can drive the in‑place renamer directly:
```kotlin
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer

VariableInplaceRenamer(elementAtCaret, myFixture.editor).performInplaceRename()
// then type + finish template as above
```
Prefer the registry+handler path to match the product action’s behavior, but the direct renamer is useful for tight unit tests of your custom element type.

---

### Checklist
- Configure file(s) with `<caret>` on a renamable symbol.
- Build a `DataContext` with `EDITOR` and `PSI_FILE` (and optionally `PSI_ELEMENT`).
- Invoke via `RenameHandlerRegistry` on EDT.
- Assert an inline live template started (`TemplateManagerImpl.getTemplateState(editor) != null`).
- Type the new name and finish the template.
- Assert resulting PSI/text (and any options behavior you care about).