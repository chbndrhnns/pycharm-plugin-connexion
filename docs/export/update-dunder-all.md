### Overview

In a PyCharm (IntelliJ platform) plugin, updating `__all__` (dunder-all) means:

1. Finding the `__all__` assignment PSI node in a `PyFile`.
2. Reading/modifying its value (usually a list or tuple of strings, sometimes via `+=`).
3. Performing PSI changes inside a write action and (usually) a command.

Below is a practical, API-oriented guide.

---

### 1. Locate the `__all__` assignment in a Python file

You normally start from a `PsiFile` that you know is a `PyFile`:

```java
PsiFile psiFile = ...;  // obtained from editor, intention, refactoring, etc.
if (!(psiFile instanceof PyFile)) return;
PyFile pyFile = (PyFile) psiFile;
```

There are two common ways to find `__all__`:

#### 1.1. Using top‑level attributes

```java
PyTargetExpression[] attrs = pyFile.getTopLevelAttributes();
PyTargetExpression dunderAll = null;

for (PyTargetExpression attr : attrs) {
  if ("__all__".equals(attr.getName())) {
    dunderAll = attr;
    break;
  }
}

if (dunderAll == null) {
  // No __all__ defined at module level
  return;
}
```

If you prefer, you can also scan all statements:

```java
for (PyStatement statement : pyFile.getStatements()) {
  if (statement instanceof PyAssignmentStatement assignment) {
    PyExpression[] targets = assignment.getTargets();
    if (targets.length == 1 && targets[0] instanceof PyTargetExpression target) {
      if ("__all__".equals(target.getName())) {
        // Found __all__ assignment
      }
    }
  }
}
```

---

### 2. Read current `__all__` entries

The assigned value is usually a list or tuple literal, like:

```python
__all__ = ["foo", "bar"]
```

On the PSI side this is typically a `PyListLiteralExpression` or `PyTupleExpression`.

```java
PyExpression value = dunderAll.findAssignedValue();
if (value == null) return;

List<String> currentNames = new ArrayList<>();

if (value instanceof PySequenceExpression seq) {
  for (PyExpression element : seq.getElements()) {
    if (element instanceof PyStringLiteralExpression stringLiteral) {
      String name = stringLiteral.getStringValue();
      if (name != null) {
        currentNames.add(name);
      }
    }
  }
}
```

If `value` is not a simple sequence (e.g. `__all__ += other`, or a name imported from somewhere), updating becomes more
heuristic. In many plugins it’s acceptable to handle only the simple literal forms and bail out otherwise.

---

### 3. Modify `__all__` safely (write action + command)

Any PSI modification must be done inside a write action. If this is user‑visible (e.g. intention/refactoring), you
should also wrap it in a command for undo/redo.

```java
Project project = pyFile.getProject();

WriteCommandAction.runWriteCommandAction(project, () -> {
  doUpdateDunderAll(pyFile, "symbol_to_add");
});
```

Where `doUpdateDunderAll` does the actual PSI changes.

---

### 4. Updating the sequence literal in place

#### 4.1. Adding a new name

Example: add `newName` to `__all__` if it’s not already present.

```java
private static void doUpdateDunderAll(PyFile pyFile, String newName) {
  PyTargetExpression dunderAll = findDunderAll(pyFile);
  if (dunderAll == null) return;

  PyExpression value = dunderAll.findAssignedValue();
  if (!(value instanceof PySequenceExpression seq)) return;

  for (PyExpression element : seq.getElements()) {
    if (element instanceof PyStringLiteralExpression str && newName.equals(str.getStringValue())) {
      // Already present
      return;
    }
  }

  Project project = pyFile.getProject();
  LanguageLevel level = LanguageLevel.forElement(pyFile);
  PyElementGenerator generator = PyElementGenerator.getInstance(project);

  // Create new string literal element
  PyExpression newElement = generator.createExpressionFromText(level, '"' + newName + '"');

  // Insert it into the sequence
  PsiElement last = seq.getLastChild();
  // You can be more precise and insert before closing bracket, preserving formatting.
  seq.addBefore(newElement, last);
}
```

In practice you also handle commas and spacing by inserting a comma token and/or using `CodeStyleManager` to reformat
the assignment afterwards:

```java
CodeStyleManager.getInstance(project).reformat(dunderAll.getParent());
```

#### 4.2. Replacing the entire `__all__` value

If you want full control (e.g. regenerate `__all__` from current exports), the simplest and most robust path is to
replace the value expression with a freshly generated list literal.

```java
private static void setDunderAll(PyFile pyFile, List<String> names) {
  PyTargetExpression dunderAll = findDunderAll(pyFile);
  if (dunderAll == null) return;

  Project project = pyFile.getProject();
  LanguageLevel level = LanguageLevel.forElement(pyFile);
  PyElementGenerator generator = PyElementGenerator.getInstance(project);

  // Build a Python list literal text, e.g. ["a", "b", "c"]
  String joined = names.stream()
      .map(n -> '"' + n + '"')
      .collect(Collectors.joining(", "));
  String listText = "[" + joined + "]";

  PyExpression newValue = generator.createExpressionFromText(level, listText);
  PyExpression oldValue = dunderAll.findAssignedValue();
  if (oldValue != null) {
    oldValue.replace(newValue);
  }
}
```

This approach avoids fiddling with commas and brackets manually.

---

### 5. Creating a new `__all__` assignment if missing

If the file has no `__all__` and your plugin wants to introduce one, you can insert a new assignment at the top‑level:

```java
private static void createDunderAll(PyFile pyFile, List<String> names) {
  Project project = pyFile.getProject();
  LanguageLevel level = LanguageLevel.forElement(pyFile);
  PyElementGenerator generator = PyElementGenerator.getInstance(project);

  String joined = names.stream()
      .map(n -> '"' + n + '"')
      .collect(Collectors.joining(", "));

  String stmtText = "__all__ = [" + joined + "]";

  PyStatement statement = generator.createFromText(level, PyStatement.class, stmtText);

  // Insert near the top of the module; you may want to skip encoding comment, shebang, etc.
  pyFile.addBefore(statement, pyFile.getFirstChild());
}
```

Again, wrap this in `WriteCommandAction.runWriteCommandAction`.

---

### 6. Handling more complex patterns

In real‑world Python projects, you might see patterns like:

```python
__all__ = [
    "a",
    "b",
]
__all__ += ["c"]
```

or

```python
NAMES = ["a", "b"]
__all__ = NAMES
```

The “fully correct” handling of such patterns is non‑trivial. Common, pragmatic strategies in plugins are:

* Only support a direct assignment to a literal list/tuple (`__all__ = [..]` or `(__all__ = (..))`).
* Ignore subsequent `__all__ += ...` updates or non‑literal values, or show a message that the pattern is not supported.
* When multiple `__all__` assignments exist, either:
    * Choose the first, or
    * Choose the last assignment in file order.

Be explicit in your plugin’s behavior and, if needed, document it for users.

---

### 7. Typical usage scenario in a feature

**Example: refactoring that moves a symbol and wants to update `__all__`:**

1. During refactoring, after you move or rename a function/class/method, gather the export names that should be exposed
   from the module.
2. Compute the new list of names for that module’s `__all__`.
3. Run a write command updating or creating `__all__` as shown above.

This integrates cleanly with the rest of PyCharm’s refactoring infrastructure because PSI updates are atomic, undoable,
and respect code style.

---

### 8. Summary

To update `__all__` in a PyCharm plugin:

1. Work with `PyFile` and `PyTargetExpression` named `"__all__"`.
2. Read the assigned value via `findAssignedValue()` and treat it as a `PySequenceExpression` where possible.
3. Perform additions or replacements using `PyElementGenerator` in a `WriteCommandAction`.
4. Optionally reformat the affected statement using `CodeStyleManager`.
5. Decide and document how you handle complex / non‑literal patterns.

If you share a bit of your existing plugin code (intention, inspection, or refactoring), I can adapt the snippets above
directly to your class/method structure and show a minimal, complete implementation.