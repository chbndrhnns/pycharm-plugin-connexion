### Goal

When a reference/annotation has a union type (e.g., `A | B | C`), show a popup chooser listing the union’s member types.
When the user selects one, insert the chosen type’s short name at the caret (or replace the current union in a type
annotation) and add the proper import to the file.

Below is a minimal, production-ready way to do it in a PyCharm plugin using the IntelliJ Platform + PyCharm OpenAPI.

---

### Key APIs you’ll use

- `PyUnionType` (Python plugin): inspect union members.
- `TypeEvalContext` (Python plugin): compute/format types.
- `JBPopupFactory#createPopupChooserBuilder` (Platform): show a list popup.
- `WriteCommandAction` and `PsiDocumentManager` (Platform): commit/write PSI safely.
- `PyElementGenerator` (Python plugin): create PSI for the chosen type annotation.
- `AddImportHelper` (Python plugin): add an import (from module or fully qualified name) if needed.

---

### Kotlin example: show chooser and insert selected type

This example assumes you invoke it from an editor action when the caret is on or near a union annotation. It handles two
common places:

- Replacing the whole annotation expression (e.g., `x: A | B | C` → `x: A`).
- Inserting a type at caret (fallback).

```kotlin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyElementGenerator
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.PyRefactoringUtil
import com.intellij.openapi.util.TextRange
import com.intellij.util.ThrowableRunnable

class ChooseUnionMemberTypeAction : AnAction("Choose Type from Union") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR)
        val file = e.getRequiredData(com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE) as? PyFile ?: return

        val context = TypeEvalContext.codeAnalysis(project, file)

        // Find the closest type expression PSI around the caret
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        val typeExpr = PsiTreeUtil.getParentOfType(
            elementAtCaret,
            PyExpression::class.java,
            /* strict = */ false
        ) ?: return

        // Ask PyCharm to infer the type at this element
        val inferred = com.jetbrains.python.psi.types.PyTypeChecker.getType(typeExpr, context)
        val union = (inferred as? PyUnionType) ?: return // nothing to do if not a union

        val options = union.members.toList().ifEmpty { return }

        // Map each option to a presentable label + needed import info
        data class Item(
            val type: PyType,
            val label: String,
            val qName: String?, // qualified name to import, if known
            val shortName: String // what we’ll insert in code
        )

        val items: List<Item> = options.mapNotNull { t ->
            when (t) {
                is PyClassType -> {
                    val pyClass = t.pyClass
                    val qName = pyClass.qualifiedName // e.g., "pkg.mod.Class"
                    val short = pyClass.name ?: return@mapNotNull null
                    Item(t, short, qName, short)
                }
                else -> {
                    // Fallback: try to get a readable name; you can expand this for typing types
                    val name = t.name ?: return@mapNotNull null
                    // For typing constructs, you might want to prefer importing from "typing" or module of the alias
                    Item(t, name, null, name)
                }
            }
        }

        if (items.isEmpty()) return

        // Show the chooser
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("Select type from union")
            .setRenderer { value ->
                com.intellij.ui.SimpleListCellRenderer.create<Item>("") { label, item, _ ->
                    label.text = item.label
                }
            }
            .setItemChosenCallback { chosen ->
                applySelection(project, editor, file, typeExpr, chosen)
            }
            .createPopup()

        popup.showInBestPositionFor(editor)
    }

    private fun applySelection(
        project: Project,
        editor: Editor,
        file: PyFile,
        typeExpr: PyExpression,
        item: Item
    ) {
        WriteCommandAction.runWriteCommandAction(project, ThrowableRunnable {
            val doc = editor.document
            val generator = PyElementGenerator.getInstance(project)

            // 1) Replace the union expression with the chosen type PSI if possible
            val replacementText = item.shortName
            val replacement = generator.createExpressionFromText(LanguageLevel.forElement(file), replacementText)

            val replaced = try {
                typeExpr.replace(replacement)
                true
            } catch (_: Throwable) {
                false
            }

            if (!replaced) {
                // 2) Fallback: insert at caret
                doc.insertString(editor.caretModel.offset, replacementText)
            }

            PsiDocumentManager.getInstance(project).commitDocument(doc)

            // Add import for the chosen type when we know its qualified name
            // (AddImportHelper is safe: it checks duplicates and places imports in correct section.)
            item.qName?.let { qName ->
                AddImportHelper.addImportFrom(
                    file,
                    qName, /* alias = */
                    null, /* element = */
                    null,
                    ImportPriority.THIRD_PARTY
                )
            }
        })
    }
}
```

---

### Where to run this from

- Bind `ChooseUnionMemberTypeAction` to a keyboard shortcut or add it to the editor’s context menu.
- If you want this behavior automatically on quick-fix, expose it as an `IntentionAction` (e.g., “Select type from
  union…”) registered via `com.intellij.codeInsight.intentionAction` and compute the union at the caret similarly.

---

### Extracting union members reliably

`PyUnionType` covers both `typing.Union[A, B]` and PEP 604 `A | B` forms. Depending on your Python plugin version:

- Prefer `PyUnionType.members` (Kotlin property) or `getMembers()` (Java) for the list.
- Older APIs may have `getOptions()`; keep a small compatibility shim:

```kotlin
val union = inferred as? PyUnionType
val members: Collection<PyType> = when {
    union == null -> emptyList()
    try {
        union.members; true
    } catch (_: Throwable) {
        false
    } -> union.members
    try {
        union.javaClass.getMethod("getMembers"); true
    } catch (_: Throwable) {
        false
    } -> union.getMembers()
    else -> (union.javaClass.methods.find { it.name == "getOptions" }?.invoke(union) as? Collection<*>)
        ?.filterIsInstance<PyType>() ?: emptyList()
}
```

---

### Getting a presentable name for each option

- For classes: `PyClassType` → `pyClass.name` and `pyClass.qualifiedName`.
- For other `PyType` variants (e.g., `typing.Literal`, `typing.Dict[str, int]`), consider using `PyType#getName()` or,
  if available, `PyTypePresentationService`/`PyTypeUtil` in your platform version to generate a short, user-friendly
  label.

```kotlin
val label = type.name ?: type.toString() // last-resort fallback
```

---

### Adding import for the chosen type

- Prefer `AddImportHelper.addImportFrom(PyFile, qualifiedName, alias, anchor, priority)`.
- If you only know the module and the short name separately, you can build a `QualifiedName` from dotted text and pass
  it to `AddImportHelper`.
- `AddImportHelper` avoids duplicates and places the import according to the code style.

Common patterns:

```kotlin
// from pkg.mod import Class
AddImportHelper.addImportFrom(pyFile, "pkg.mod.Class", null, null, ImportPriority.THIRD_PARTY)

// import pkg.mod as m  (if you prefer a module import then qualify in code)
// AddImportHelper.addImportStatement(pyFile, "pkg.mod", null, ImportPriority.THIRD_PARTY)
```

If the symbol is already available in the local scope or imported, `AddImportHelper` will typically do nothing, which is
what you want.

---

### Replacing exactly the union portion inside an annotation (optional refinement)

If you want to replace only the part inside an annotation (e.g., `A | B | C` under `x: ...`), locate the annotation node
and its value expression precisely:

```kotlin
val annotation = PsiTreeUtil.getParentOfType(elementAtCaret, PyAnnotation::class.java)
val valueExpr = annotation?.value // the expression after ':' or '->'
if (valueExpr is PyBinaryExpression || valueExpr is PySubscriptionExpression) {
    valueExpr.replace(generator.createExpressionFromText(langLevel, item.shortName))
}
```

This keeps other surrounding annotation syntax intact.

---

### Handling typing aliases and builtins

- For `typing` types (e.g., `list[str]`), you usually don’t need an import for builtins like `list`, `dict` (PEP 585).
  For `typing.Optional`, `typing.Literal`, etc., prefer importing from `typing` (or `typing_extensions` if your plugin
  context says so). You can encode a small rules table mapping known types to their preferred provider module.
- For project-local classes, derive the `qualifiedName` from `PyClass.qualifiedName`.

---

### Tests sketch (Platform test framework)

Use `BasePlatformTestCase` to drive an in-memory file and simulate the chooser callback.

```kotlin
class ChooseUnionMemberTypeActionTest : BasePlatformTestCase() {
    fun `test replace union and add import`() {
        myFixture.configureByText(
            "a.py", """
        from pkg import A
        from pkg2 import C
        
        def f(x: A | B | C):
            pass
    """.trimIndent()
        )

        val file = myFixture.file as PyFile
        val project = project
        val editor = myFixture.editor

        // Pretend user chose B (pkg.B)
        val item = Item(/* type */ /* mocked */, label = "B", qName = "pkg.B", shortName = "B")

        ChooseUnionMemberTypeAction().apply {
            // call private via reflection or extract into a helper
            // applySelection(project, editor, file, typeExpr, item)
        }

        myFixture.checkResult(
            """
        from pkg import A
        from pkg import B
        from pkg2 import C
        
        def f(x: B):
            pass
      """.trimIndent()
        )
    }
}
```

---

### Practical tips

- Always wrap PSI edits in `WriteCommandAction` and commit with `PsiDocumentManager`.
- If your union appears in a stubbed context (e.g., library code), favor inserting at caret rather than replacing PSI
  that belongs to another file.
- Consider de-duplicating identical labels if two options share the same short name but come from different modules;
  display `shortName (module)` in the popup.
- If you want this as a quick-fix on unresolved reference inside a union, implement an `IntentionAction` that becomes
  available when `PyUnionType` is detected at the caret.

---

### Summary

1) Detect `PyUnionType` at caret with `TypeEvalContext`.
2) Enumerate members, build user-friendly labels and qualified names.
3) Show a `JBPopupFactory` list popup.
4) On selection: replace/insert the chosen short name and call `AddImportHelper` with the qualified name.

This gives users a smooth, discoverable way to “pick one” from a union and ensures imports stay correct and code-style
compliant.