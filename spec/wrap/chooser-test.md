### What you’ll test

You want to verify two things for your chooser that lists union member types:

- It shows the expected items (labels) in the popup.
- Selecting one item applies the edit (replaces the union with the selected type and adds/imports the type).

Below is a practical, robust approach using the IntelliJ Platform test framework (`BasePlatformTestCase`) and a tiny
seam in your action to make the chooser testable without real UI.

---

### 1) Make the chooser injectable (one small seam in production code)

Add a `PopupHost` collaborator that your action uses to show the chooser. In production, it delegates to
`JBPopupFactory`; in tests, you replace it with a fake that captures items and simulates a user choice.

```kotlin
// src/main/kotlin/.../ChooseUnionMemberTypeAction.kt

interface PopupHost {
    fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        renderer: (T) -> String,
        onChosen: (T) -> Unit
    )
}

class JbPopupHost : PopupHost {
    override fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        renderer: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle(title)
            .setRenderer(
                com.intellij.ui.SimpleListCellRenderer.create<T>("") { label, value, _ ->
                    label.text = renderer(value)
                }
            )
            .setItemChosenCallback { chosen -> onChosen(chosen) }
            .createPopup()
        popup.showInBestPositionFor(editor)
    }
}

class ChooseUnionMemberTypeAction(
    private val popupHost: PopupHost = JbPopupHost()
) : AnAction("Choose Type from Union") {
    // ... existing code ...
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val file = e.getRequiredData(CommonDataKeys.PSI_FILE) as? PyFile ?: return

        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        val typeExpr = PsiTreeUtil.getParentOfType(elementAtCaret, PyExpression::class.java, false) ?: return

        val inferred = com.jetbrains.python.psi.types.PyTypeChecker.getType(typeExpr, context)
        val union = inferred as? PyUnionType ?: return

        data class Item(val type: PyType, val label: String, val qName: String?, val shortName: String)

        val items: List<Item> = union.members.mapNotNull { t ->
            when (t) {
                is PyClassType -> {
                    val cls = t.pyClass
                    val qn = cls.qualifiedName
                    val short = cls.name ?: return@mapNotNull null
                    Item(t, short, qn, short)
                }
                else -> {
                    val name = t.name ?: return@mapNotNull null
                    Item(t, name, null, name)
                }
            }
        }
        if (items.isEmpty()) return

        popupHost.showChooser(
            editor,
            title = "Select type from union",
            items = items,
            renderer = { it.label },
            onChosen = { chosen -> applySelection(project, editor, file, typeExpr, chosen) }
        )
    }

    private fun applySelection(
        project: Project,
        editor: Editor,
        file: PyFile,
        typeExpr: PyExpression,
        item: Item
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = editor.document
            val generator = PyElementGenerator.getInstance(project)
            val replacement = generator.createExpressionFromText(LanguageLevel.forElement(file), item.shortName)
            val replaced = try {
                typeExpr.replace(replacement); true
            } catch (_: Throwable) {
                false
            }
            if (!replaced) {
                doc.insertString(editor.caretModel.offset, item.shortName)
            }
            PsiDocumentManager.getInstance(project).commitDocument(doc)
            item.qName?.let { qn ->
                AddImportHelper.addImportFrom(file, qn, null, null, AddImportHelper.ImportPriority.THIRD_PARTY)
            }
        }
    }
}
```

This keeps production behavior unchanged while giving tests an easy hook.

---

### 2) A simple fake `PopupHost` for tests

The fake captures provided items so you can assert the labels, and then it triggers `onChosen` with the index you decide
in each test.

```kotlin
// src/test/kotlin/.../FakePopupHost.kt

class FakePopupHost : PopupHost {
    var lastTitle: String? = null
    var lastLabels: List<String> = emptyList()
    var selectedIndex: Int = 0 // default selection for tests

    override fun <T> showChooser(
        editor: Editor,
        title: String,
        items: List<T>,
        renderer: (T) -> String,
        onChosen: (T) -> Unit
    ) {
        lastTitle = title
        lastLabels = items.map(renderer)
        @Suppress("UNCHECKED_CAST")
        val chosen = items[selectedIndex] as T
        onChosen(chosen)
    }
}
```

---

### 3) Test: verifies chooser items and selection behavior

This uses `BasePlatformTestCase`. It builds a file with a union annotation, places the caret, runs the action, and
inspects both the captured chooser items and the final file content.

```kotlin
// src/test/kotlin/.../ChooseUnionMemberTypeActionTest.kt

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.TestActionEvent
import com.jetbrains.python.PythonFileType

class ChooseUnionMemberTypeActionTest : BasePlatformTestCase() {

    private fun runAction(action: AnAction) {
        val event: AnActionEvent = TestActionEvent.createTestEvent(action)
        action.actionPerformed(event)
    }

    fun `test chooser lists union members and applying selection replaces annotation and imports`() {
        // Arrange: file with union types A | B | C; A and C already imported; B not yet.
        val text = """
      from pkg import A
      from pkg2 import C

      class D: pass

      def f(x: A | B | C):
          pass
    """.trimIndent()

        myFixture.configureByText("a.py", text)
        val fakePopup = FakePopupHost().apply {
            // We will assert items first, then select index 1 -> "B"
            selectedIndex = 1
        }

        // Place caret on the union
        val offset = myFixture.file.text.indexOf(": A | B | C") + 2
        myFixture.editor.caretModel.moveToOffset(offset)

        // Act: run the action with the fake popup
        val action = ChooseUnionMemberTypeAction(fakePopup)
        runAction(action)

        // Assert: chooser labels are exactly [A, B, C] in some order that matches union members
        assertEquals(listOf("A", "B", "C"), fakePopup.lastLabels)

        // After selecting "B", annotation should be replaced and import added
        myFixture.checkResult(
            """
        from pkg import A
        from pkg import B
        from pkg2 import C

        class D: pass

        def f(x: B):
            pass
      """.trimIndent()
        )
    }
}
```

Notes:

- `TestActionEvent` is a convenient way to invoke actions in tests without needing actual UI.
- We manually set the caret; the action infers the union at/near the caret.
- We verified both the popup content (via the fake) and the file edits/imports.

---

### 4) Optional: a second test that picks a built-in/typing type

If your union may include typing constructs (e.g., `int | Literal["x"]`), add a test ensuring labels are readable and
imports are added only when necessary.

```kotlin
fun `test chooser includes typing literal and selecting it replaces union`() {
    val text = """
    from typing import Literal
    def f(x: int | Literal["x"]):
        pass
  """.trimIndent()
    myFixture.configureByText("b.py", text)

    val fakePopup = FakePopupHost().apply { selectedIndex = 1 } // pick the Literal option
    val offset = myFixture.file.text.indexOf(": int | Literal") + 2
    myFixture.editor.caretModel.moveToOffset(offset)

    val action = ChooseUnionMemberTypeAction(fakePopup)
    TestActionEvent.createTestEvent(action).let(action::actionPerformed)

    // Labels should be something like ["int", "Literal['x']"] depending on your label logic
    assertTrue(fakePopup.lastLabels.any { it.startsWith("Literal") })

    // The annotation should now be `Literal["x"]`, imports unchanged (already present)
    myFixture.checkResult(
        """
      from typing import Literal
      def f(x: Literal["x"]):
          pass
    """.trimIndent()
    )
}
```

---

### 5) Alternative: replacing the application service (no seam)

If you can’t or don’t want to introduce `PopupHost`, you can replace the application service `JBPopupFactory` with a
test double within the test using `ServiceContainerUtil.replaceService`:

```kotlin
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.ServiceContainerUtil
import com.intellij.openapi.Disposable

class RecordingPopupFactory :
    JBPopupFactory() { /* implement createPopupChooserBuilder to capture items and immediately call callback */ }

fun `test via service replacement`() {
    val app = ApplicationManager.getApplication()
    val disposable: Disposable = testRootDisposable
    val recording = RecordingPopupFactory()
    ServiceContainerUtil.replaceService(app, JBPopupFactory::class.java, recording, disposable)
    // run action, then assert `recording.items` and file content
}
```

This is more brittle because you need to mimic the factory API sufficiently for your platform version, but it keeps
production code unchanged.

---

### 6) Test dependencies and tips

- Base test class: `com.intellij.testFramework.fixtures.BasePlatformTestCase`.
- Ensure your test module depends on the Python plugin test fixtures (since you’re using `Py*` PSI and types). In
  Gradle, use the IntelliJ Gradle plugin with `plugins = ['python']` and enable`intellij { plugins = ['PythonCore'] }` (
  exact id may vary by version).
- Always perform PSI edits on EDT through the framework helpers; `BasePlatformTestCase` and `WriteCommandAction` already
  handle threading for you in these snippets.
- Keep labels stable by sorting union members if your underlying API yields nondeterministic order; otherwise, assert as
  a set or reorder in tests.

Example of stabilizing order in production before showing popup:

```kotlin
val items = items.unsorted().sortedBy { it.label }
```

---

### Summary

- Introduce a tiny, test-only seam (`PopupHost`) so your action doesn’t depend on real UI.
- In tests, use `FakePopupHost` to capture chooser items and simulate selection.
- Use `BasePlatformTestCase` + `TestActionEvent` + fixtures to assert both the chooser labels and the file changes,
  including imports.

This approach yields fast, reliable tests that validate both the UX contract (what the user sees in the chooser) and the
resulting code transformation.