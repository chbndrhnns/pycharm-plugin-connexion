### Short answer
You can’t get the floating inline toolbar if you run `RenameProcessor.run()` head‑lessly. That processor bypasses the UI completely. To show the inline (in‑editor) rename UI with the options “gear” popup, you must trigger the same handler the Rename action uses and provide an `Editor` + `PSI_ELEMENT` in the `DataContext` so the platform starts in‑place rename.

### What to call instead of `RenameProcessor`
Use the registered `RenameHandler` (via `RenameHandlerRegistry`) or the `InplaceRenameHandler` if you know the target supports in‑place rename.

```java
Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
if (editor == null) return;

// Make sure caret points at the element to be renamed
CaretModel caret = editor.getCaretModel();
caret.moveToOffset(targetElement.getTextOffset());

DataContext base = DataManager.getInstance().getDataContext(editor.getContentComponent());
DataContext ctx = SimpleDataContext.builder()
    .add(CommonDataKeys.EDITOR, editor)
    .add(CommonDataKeys.PSI_ELEMENT, targetElement)
    .add(CommonDataKeys.PSI_FILE, targetElement.getContainingFile())
    .setParent(base)
    .build();

RenameHandler handler = RenameHandlerRegistry.getInstance().getRenameHandler(ctx);
if (handler != null) {
  handler.invoke(project, editor, targetElement.getContainingFile(), ctx);
}
```

- If the element supports in‑place rename (controlled by `RefactoringSupportProvider#isInplaceRenameAvailable` and element‑specific processors), this starts inline rename and the floating toolbar appears so the user can toggle options.
- If in‑place isn’t available for that element, the platform shows the classic Rename dialog, which also exposes the same options (search in comments/strings, text occurrences, preview, etc.).

### Pre‑setting or customizing the options
- Defaults used by the inline toolbar/dialog come from `RefactoringSettings` (e.g., `RENAME_SEARCH_IN_COMMENTS`, `RENAME_SEARCH_FOR_TEXT_OCCURRENCES`). You can preconfigure these before invoking the handler:

```java
RefactoringSettings s = RefactoringSettings.getInstance();
s.RENAME_SEARCH_IN_COMMENTS = true;
s.RENAME_SEARCH_FOR_TEXT_OCCURRENCES = true;
```

- To influence whether in‑place rename is offered, provide/extend a `RefactoringSupportProvider` for your language or element, and/or a `RenamePsiElementProcessor` that returns `isInplaceRenameAvailable()` appropriately.

### Forcing inline vs. dialog behavior
- Inline + floating toolbar: call the handler with a valid `Editor` and caret on the element. You can also call `new VariableInplaceRenamer(element, editor).performInplaceRename()` (or another `InplaceRenamer`) when applicable, but prefer the handler so platform policies and extensions apply.
- Dialog (no floating toolbar): use `RenameDialogFactory.getInstance().createRenameDialog(project, element, null, editor).show();`

### Practical checklist
1. Place caret at the element and build a `DataContext` containing `CommonDataKeys.EDITOR`, `CommonDataKeys.PSI_ELEMENT`, and `CommonDataKeys.PSI_FILE`.
2. Get a `RenameHandler` from `RenameHandlerRegistry` and call `invoke(...)`.
3. Optionally set `RefactoringSettings` flags before invoking if you want different defaults.
4. Ensure your plugin’s `RefactoringSupportProvider`/`RenamePsiElementProcessor` allows in‑place rename for your element.

This way, a programmatically triggered rename behaves like a user‑invoked rename and shows the floating toolbar so the user can modify the options.