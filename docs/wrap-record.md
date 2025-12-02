### Goal

Create a PyCharm quick fix/intention that converts a simple, annotated variable into a value-object dataclass. Example:

```python
username: str = "username"
```

becomes

```python
import dataclasses

@dataclasses.dataclass
class Username:
    value: str
```

Optionally, replace original usages with `Username(...)` where appropriate.

---

### User stories

- As a user, when my caret is on an annotated assignment like `username: str = "john"`, I can invoke an intention
  “Convert variable to dataclass value object” to generate a `Username` dataclass with a single `value` field of the
  same type.
- If I already have `from dataclasses import dataclass`, the action should use `@dataclass` (unqualified). Otherwise, it
  should add `import dataclasses` and use `@dataclasses.dataclass`.
- If `class Username` conflicts with an existing symbol, I’m prompted to choose a new class name (e.g., `Username1` or
  `UserName`), or the action auto-resolves by appending a suffix.
- A preview shows the exact code transformation before applying.

---

### Detection (when to offer the intention)

Offer the intention on a `PyTargetExpression` that meets all:

- Is a simple assignment in module/class scope: `name: <type> = <expr>` or `name: <type>` with or without initializer.
- `name` is a valid Python identifier and not dunder.
- The annotation resolves to a known/statically printable type name (at least a PSI string such as `str`, `int`, `UUID`,
  `Path`, `typing.NewType`, etc.). Start minimal with built‑ins and simple names; gracefully fall back to `Any` if
  needed.
- Not a multi-target or tuple destructuring assignment.

Optionally support:

- `# type:` comments as a fallback for annotation.
- Without initializer (e.g., `username: str`).

---

### Transformation rules

1. Determine class name from variable name via UpperCamelCase:
    - `username` → `Username`, `user_name` → `UserName`, `_id` → `Id` (or `ID` if acronym detection is implemented).
    - Handle leading underscores by stripping them for the class name.
2. Determine field type string `T` from annotation text; if absent, use `Any` and add `from typing import Any` if
   missing.
3. Generate dataclass:
    - Prefer placing the new class near the variable (module-level):
        - If variable is top-level: place class above the variable and remove the variable declaration; add a TODO
          comment or offer a follow-up action to instantiate where needed.
        - If variable is inside a class or function: insert class at module level (top of file or after imports) to
          avoid nested classes, or allow a setting to nest.
    - Decorator: use `@dataclass` if the `dataclass` name is available; otherwise `@dataclasses.dataclass` and add the
      necessary import.
    - Field: `value: T` (no default). If the original assignment had a default `= expr`, consider two strategies:
        - Minimal: ignore default in the class and leave usage changes to follow-up actions.
        - Optional advanced: set a default in the dataclass: `value: T = expr` only if `expr` is safely serializable
          literal.
4. Imports:
    - If `from dataclasses import dataclass` exists → use it.
    - Else if `import dataclasses` exists → use `@dataclasses.dataclass`.
    - Else add `import dataclasses` at top, respecting existing import blocks and formatting.
5. Optional usage rewrite (follow-up quick fix): replace the original variable with an instance creation near the
   original site:
   ```python
   Username(value="username")
   ```
   This is safer as a separate action because global semantic changes may be non-local.

---

### Edge cases and decisions

- Name conflicts:
    - Existing `class Username` or `Username` variable: auto-rename to `UsernameValue` or prompt via dialog.
- Scope:
    - If source assignment is inside a function, generating a top-level class changes scope. Provide a preview and note
      in the description. For first iteration, restrict to module level only.
- Complex annotations:
    - `typing.Optional[str]`, `list[str]`, aliases, or imported names → keep the original text representation. Ensure
      required imports are present; don’t add new typing imports automatically unless we changed the type to `Any`.
- Defaults and mutability:
    - If default is a mutable literal (`[]`, `{}`), do NOT carry it into a dataclass default; leave field without
      default.
- Dataclass availability:
    - Python 3.6 with backport is rare; assume 3.7+. If project interpreter < 3.7, either hide the intention or require
      `dataclasses` backport (out of scope initially).
- Comments and decorators around the original assignment:
    - Preserve leading comments as a docstring for the class or attach as a preceding comment.
- `__all__` management and export style:
    - If file has `__all__`, consider adding the new class name. For MVP, skip.
- Formatting and code style:
    - Run reformat on the inserted class and optimize imports afterwards.

---

### UI/UX

- Intention text: `Convert variable to dataclass value object`
- Family name: `Refactorings`
- Preview: show added import (if any), new class, and the removal of the original assignment.
- If conflicts or invalid contexts: intention not available, or show a small balloon with the reason.

---

### Implementation sketch (Kotlin, IntelliJ Platform + PyCharm PSI)

```kotlin
class ConvertVariableToDataclassIntention : PyBaseIntentionAction() {
    override fun getText() = "Convert variable to dataclass value object"
    override fun getFamilyName() = "Refactorings"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file !is PyFile || editor == null) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val assignment = PsiTreeUtil.getParentOfType(element, PyAnnotationOwner::class.java, PyAssignmentStatement::class.java)
        val target = PsiTreeUtil.getParentOfType(element, PyTargetExpression::class.java) ?: return false
        val annOwner = target as? PyAnnotationOwner ?: return false
        if (PsiTreeUtil.getParentOfType(target, PyClass::class.java, true) != null) return false // restrict to module-level MVP
        if (target.isQualified) return false
        val annotation = annOwner.annotation?.value ?: return false
        return target.parent is PyAssignmentStatement || annotation != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file !is PyFile || editor == null) return
        val target = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PyTargetExpression::class.java) ?: return
        val annOwner = target as PyAnnotationOwner
        val typeText = annOwner.annotation?.value?.text ?: "Any"
        WriteCommandAction.runWriteCommandAction(project, text) {
            val className = toClassName(target.name ?: "Value")
            val generator = PyElementGenerator.getInstance(project)
            val langLevel = LanguageLevel.forElement(file)

            // Ensure imports
            val decoratorText = ensureDataclassImport(file)

            val classText = buildString {
                append("$decoratorText\nclass $className:\n    value: $typeText\n")
            }
            val pyClass = generator.createFromText(langLevel, PyClass::class.java, classText)

            // Insert above the assignment
            val parent = target.parent.parent  // likely PyAssignmentStatement
            val inserted = file.addBefore(pyClass, parent)
            file.addBefore(generator.createFromText(langLevel, PsiElement::class.java, "\n"), parent)

            // Remove original assignment statement
            parent.delete()

            // Optional: move caret to class name
            (inserted as? PyClass)?.nameIdentifier?.let { editor.caretModel.moveToOffset(it.textOffset) }

            CodeStyleManager.getInstance(project).reformat(inserted)
            PyImportOptimizer.onlyOptimizeImports(file)
        }
    }

    private fun ensureDataclassImport(file: PyFile): String {
        val hasFrom = PyPsiUtils.hasImportFrom(file, "dataclasses", "dataclass")
        val hasModule = PyPsiUtils.hasImportModule(file, "dataclasses")
        if (!hasFrom && !hasModule) {
            PyImportStatementUtil.addImportStatement(file, "dataclasses", null, ImportPriority.THIRD_PARTY)
            return "@dataclasses.dataclass"
        }
        return if (hasFrom) "@dataclass" else "@dataclasses.dataclass"
    }

    private fun toClassName(name: String): String = name
        .trim('_')
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString("") { it.replaceFirstChar { c -> c.titlecase() } }
}
```

Notes:

- `PyPsiUtils.hasImportFrom/hasImportModule` are placeholders; use available utilities from `PyImportStatement`,
  `PyFile.getImportBlock()`, or `PyPsiFacade` helpers. The IntelliJ Python plugin has `PyImportStatementHelper`/
  `PyImportStatementUtil`.
- Use `IntentionPreviewInfo` to supply a custom preview without mutating PSI.

---

### Tests (using PythonCodeInsight fixtures)

Create a test class extending the Python code insight test fixture. The repository indicates common fixtures at:

- `python-common-tests/com/jetbrains/python/fixture/PythonCommonCodeInsightTestFixture.kt`
- `python/testSrc/com/jetbrains/python/fixtures/PythonPlatformCodeInsightTestFixture.kt`

#### Test data layout

```
/testData/intentions/convertToDataclass/
  basic_before.py
  basic_after.py
  uses_from_import_before.py
  uses_from_import_after.py
  no_annotation_before.py
  no_intention_available.py
  conflict_before.py
  conflict_after.py
  complex_type_before.py
  complex_type_after.py
```

#### Sample tests (Kotlin)

```kotlin
class ConvertVariableToDataclassIntentionTest : PyTestCase() {
    fun testBasic() = myFixture.runWithLanguageLevel(LanguageLevel.PYTHON38) {
        myFixture.configureByText("basic_before.py", """
            u<caret>sername: str = "username"
        """.trimIndent())
        myFixture.launchAction(myFixture.findIntention("Convert variable to dataclass value object"))
        myFixture.checkResult(
            """
            import dataclasses

            @dataclasses.dataclass
            class Username:
                value: str
            
            
            """.trimIndent()
        )
    }

    fun testRespectsFromImport() {
        myFixture.configureByText("a.py", """
            from dataclasses import dataclass
            u<caret>sername: str = "username"
        """.trimIndent())
        myFixture.launchAction(myFixture.findIntention("Convert variable to dataclass value object"))
        myFixture.checkResult(
            """
            from dataclasses import dataclass

            @dataclass
            class Username:
                value: str
            
            
            """.trimIndent()
        )
    }

    fun testUnavailableWithoutAnnotation() {
        myFixture.configureByText("b.py", """
            u<caret>sername = "username"
        """.trimIndent())
        assertNull(myFixture.findIntention("Convert variable to dataclass value object"))
    }

    fun testComplexTypePreserved() {
        myFixture.configureByText("c.py", """
            from typing import Optional
            u<caret>ser_id: Optional[int] = None
        """.trimIndent())
        myFixture.launchAction(myFixture.findIntention("Convert variable to dataclass value object"))
        myFixture.checkResult(
            """
            from typing import Optional
            import dataclasses

            @dataclasses.dataclass
            class UserId:
                value: Optional[int]
            
            
            """.trimIndent()
        )
    }

    fun testNameConflict() {
        myFixture.configureByText("d.py", """
            class Username: pass
            u<caret>sername: str = "abc"
        """.trimIndent())
        myFixture.launchAction(myFixture.findIntention("Convert variable to dataclass value object"))
        // For MVP, auto-suffix with Value
        myFixture.checkResult(
            """
            class Username: pass
            import dataclasses

            @dataclasses.dataclass
            class UsernameValue:
                value: str
            
            
            """.trimIndent()
        )
    }
}
```

#### Additional tests to add

- With initializer containing mutable default (`[]`, `{}`) → no default in class.
- With comments above assignment → preserved as class docstring/comment.
- Inside function scope → intention not available (until we support relocating class to module scope).
- File with existing `import dataclasses` → use qualified decorator.
- Ensure formatting blank lines: two lines between imports and classes (PEP 8) subject to formatter.

---

### Intention preview

Provide a lightweight preview implementation:

```kotlin
override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val (className, typeText, decorator) = computePreviewParts(file, editor) ?: return IntentionPreviewInfo.EMPTY
    val result = buildString {
        appendLine(if (decorator == "@dataclass") "from dataclasses import dataclass" else "import dataclasses")
        appendLine()
        appendLine("$decorator")
        appendLine("class $className:")
        appendLine("    value: $typeText")
        appendLine()
    }
    return IntentionPreviewInfo.CustomDiff("Python", file.text, result)
}
```

---

### Future enhancements

- Offer an option to inline a factory function `def Username(value: T) -> Username: ...`.
- Replace usages of the variable with `Username(value=<expr>)` under safe, local conditions.
- Allow generating `frozen=True` dataclasses via a checkbox in a small dialog.
- Support nested classes if invoked in class scope.

---

### Summary

- Provide a clear availability predicate (single annotated assignment, module scope).
- Generate a minimal `@dataclasses.dataclass` with `value: <annotation>`.
- Manage imports, naming, and formatting.
- Ship with robust code insight tests for import handling, complex types, conflicts, and unavailability cases.

If you share a minimal plugin module or test skeleton, I can adapt the snippets to your exact project APIs (e.g.,
`PyImportStatementUtil` usage and fixture base classes).