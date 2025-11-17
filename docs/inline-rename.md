### Updated instructions: use the rename handler instead of a template

Below is a revised, self‑contained set of instructions that **use the in‑place rename handler** (with purple indicator
and live reference updates) instead of a live template.

---

### 1. Create the class with a unique initial name

First, generate and insert the `PyClass` PSI element. Before doing so, choose a **non‑conflicting** initial name (append
`1`, `2`, … if needed).

```java
private static String suggestUniqueClassName(PyFile file, String baseName) {
    Set<String> usedNames = new HashSet<>();

    for (PyClass pyClass : file.getTopLevelClasses()) {
        String name = pyClass.getName();
        if (name != null) usedNames.add(name);
    }

    // You can extend this to also consider functions, variables, etc.

    if (!usedNames.contains(baseName)) return baseName;

    int index = 1;
    while (usedNames.contains(baseName + index)) {
        index++;
    }
    return baseName + index;
}
```

Use it when creating the class:

```java
Project project = ...;
PyFile pyFile = ...;  // your target file
String baseName = "Foo"; // e.g. from quick‑fix logic

String uniqueName = suggestUniqueClassName(pyFile, baseName);

PyElementGenerator generator = PyElementGenerator.getInstance(project);
LanguageLevel langLevel = LanguageLevel.forElement(pyFile);

PyClass pyClass = generator.createFromText(
        langLevel,
        PyClass.class,
        "class " + uniqueName + ":\n    pass\n"
);

PyClass inserted = (PyClass) pyFile.add(pyClass);
```

Now the class exists in the PSI with a unique name, so starting rename will not immediately conflict with another
symbol.

---

### 2. Position the caret on the class name

To start in‑place rename, the caret must be on the element being renamed:

```java
PsiElement nameId = inserted.getNameIdentifier();
if(nameId ==null)return;

Editor editor = ...; // obtained from context (e.g. AnActionEvent)

// Ensure PSI and document are in sync
Document document = editor.getDocument();
PsiDocumentManager.

getInstance(project)
        .

doPostponedOperationsAndUnblockDocument(document);

// Move caret to the class name
editor.

getCaretModel().

moveToOffset(nameId.getTextOffset());
```

---

### 3. Invoke the Python in‑place rename handler

Instead of using `TemplateBuilder` / live templates, **invoke the rename handler** so the user gets:

- The **purple in‑place rename UI**
- All **references updated live** while typing

```java
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataManager;

// Build data context from the editor component
DataContext dataContext = DataManager.getInstance()
        .getDataContext(editor.getContentComponent());

        // Let the platform choose the right rename handler (e.g. PyMemberInplaceRenameHandler)
        RenameHandler handler = RenameHandlerRegistry.getInstance()
                .getRenameHandler(dataContext);

if(handler !=null&&handler.

        isAvailableOnDataContext(dataContext)){
        handler.

        invoke(project, editor, pyFile, dataContext);
}
```

Alternatively, if you want to directly use the Python handler you were inspecting:

```java
import com.jetbrains.python.refactoring.rename.PyMemberInplaceRenameHandler;

RenameHandler handler = new PyMemberInplaceRenameHandler();
if(handler.

isAvailableOnDataContext(dataContext)){
        handler.

invoke(project, editor, pyFile, dataContext);
}
```

Once `invoke` runs, the IDE:

- Enters **in‑place rename mode** on the new class name
- Shows the purple rename indicator
- Updates all found usages as the user types the new name

---

### 4. How this differs from the original template‑based instructions

Previous instructions:

1. Create a `PyClass` with a dummy name.
2. Wrap `getNameIdentifier()` in a `TemplateBuilder`.
3. Start a live template via `TemplateManager.startTemplate(...)`.

New instructions (rename‑handler based):

1. Compute a **unique initial name** (`suggestUniqueClassName`).
2. Create and insert the `PyClass` with that name.
3. Commit/flush PSI and move caret to the class’ `nameIdentifier`.
4. Use `RenameHandler` / `PyMemberInplaceRenameHandler` to start **in‑place rename**.

The new approach gives you exactly what you asked for:

- Inline rename with purple indicator
- All references in the current module (and beyond, per handler logic) updated live
- No immediate name clash because the initial name is made unique (`Foo`, `Foo1`, `Foo2`, …)

You can now replace the old template‑based snippet in your docs/scratch with the code in sections 1–3 above and a short
explanation that “we use the Python in‑place rename handler to let the user choose the final name while updating usages
automatically.”