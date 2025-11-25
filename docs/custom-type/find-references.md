### Overview

In a PyCharm plugin you don’t need any special “dataclass-only” API to find uses of a dataclass field. Dataclass fields
are represented in PSI as normal Python target expressions, and all regular IntelliJ reference‑search infrastructure
works on them.

The basic steps are:

1. Get the `PsiElement` that represents the dataclass field.
2. Run `ReferencesSearch.search(fieldElement, scope)`.
3. Iterate over the returned `PsiReference` instances.

Below is how to do this and what to keep in mind specifically for Python dataclasses.

---

### 1. What is the PSI element for a dataclass field?

For a Python dataclass like:

```python
from dataclasses import dataclass


@dataclass
class User:
    name: str
    age: int
```

Each field (`name`, `age`) is a `PyTargetExpression` in PSI, located in the class body. In Java/Kotlin:

- The containing class is `PyClass`.
- Its body contains `PyTargetExpression` children.

So if you already have the class PSI, you can get the field element as:

```java
PyClass pyClass = ...; // obtained from context
PyTargetExpression[] classAttributes = pyClass.getClassAttributes();
for(
PyTargetExpression attr :classAttributes){
        if("name".

equals(attr.getName())){
PyTargetExpression nameField = attr; // this is the dataclass field element
// Use nameField for reference search
    }
            }
```

If you are starting from an editor caret,

```java
PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
PyTargetExpression field = PsiTreeUtil.getParentOfType(elementAtCaret, PyTargetExpression.class);
```

Now `field` is the element you need to search references for.

---

### 2. Running a reference search

Once you have the `PsiElement` for the dataclass field, use the platform’s `ReferencesSearch`:

```java
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;

PsiElement field = nameField; // the PyTargetExpression from above
GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

Query<PsiReference> query = ReferencesSearch.search(field, scope);
for(
PsiReference ref :query){
PsiElement refElement = ref.getElement();
// process the reference (e.g., collect, navigate, highlight, etc.)
}
```

This will return all usages of that PSI element according to PyCharm’s Python resolver, including:

- Attribute accesses like `user.name`.
- Direct references inside the class.
- References from other modules where the class is imported.

Dataclasses in PyCharm are modeled so that `user.name` resolves back to the `PyTargetExpression` for `name` in the
dataclass definition, which makes `ReferencesSearch` work out of the box.

---

### 3. Integrating into a plugin action / intention / inspection

In most plugin use cases, you:

1. Determine the "target" field from context (caret, quick‑fix context element, etc.).
2. Run the search.
3. Act on the references.

Example inside an intention or quick‑fix (Java‑style pseudo‑code):

```java

@Override
public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
    PyTargetExpression field = PsiTreeUtil.getParentOfType(elementAtCaret, PyTargetExpression.class);
    if (field == null) return;

    Query<PsiReference> query = ReferencesSearch.search(field, GlobalSearchScope.projectScope(project));

    for (PsiReference ref : query) {
        PsiElement refElement = ref.getElement();
        // Your processing here — e.g. rename, add annotation, offer a quick fix, etc.
    }
}
```

---

### 4. Handling special dataclass-related cases (optional)

Depending on how thorough you need to be, you might want to treat a few extra PSI patterns as "references" to the field,
even if they’re not standard `PsiReference`s or you want to categorize them:

1. **Dataclass constructor keyword arguments**
   ```python
   user = User(name="John", age=42)
   ```
   Here, `name=` and `age=` are `PyKeywordArgument` elements. In recent Python plugin versions, these keyword names
   typically resolve back to the corresponding parameters/fields, and will show up as references via `ReferencesSearch`.
   If something doesn’t show up, you can add custom logic that:
    - Finds `PyCallExpression` for the dataclass.
    - Iterates its `PyArgumentList` / `PyKeywordArgument`s.
    - Matches keyword names with field names.

2. **Type hints and annotations**
   If you care about type references (for example, in `TypedDict` or other helper structures pointing to your dataclass
   fields), you may need to:
    - Inspect `PyAnnotation` / `PyStringLiteralExpression` etc.
    - Use `PsiTreeUtil` and custom scanning code over a scope (file, module, project) in addition to `ReferencesSearch`.

Often, though, simply trusting `ReferencesSearch` is enough, because the Python plugin already wires its resolution
system to dataclasses.

---

### 5. Reusing the built-in Find Usages infrastructure (if you need full UI)

If instead of just getting references you want full *Find Usages* behavior (with the usual tool window, grouping, etc.),
you can:

1. Implement a `FindUsagesHandlerFactory` for your element type, or
2. Reuse the existing Python `FindUsagesProvider`/handler when your plugin initiates a find‑usages operation.

But this is only needed if you want the full UI. If you only need the references programmatically,
`ReferencesSearch.search(...)` is the correct and simplest API.

---

### Summary

- Dataclass fields are just `PyTargetExpression`s in PSI.
- To find all references programmatically in a PyCharm plugin:
    1. Obtain the `PyTargetExpression` for the field (from the class body or caret context).
    2. Run `ReferencesSearch.search(field, desiredScope)`.
    3. Iterate over the resulting `PsiReference`s and process them.
- PyCharm’s Python support already resolves dataclass attribute accesses (`instance.field`) back to the field element,
  so no special dataclass API is usually required.

If you describe how you obtain the dataclass field (e.g., from a quick‑fix, inspection, or gutter action), I can sketch
a more concrete code sample tailored to that entry point.