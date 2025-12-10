### Goal
You are in **file A** (a `PyFile`) and you have a `PyClass` that is declared in **file B**. You want to find **the import statement in file A that brings this class into scope**.

Conceptually:
- You already know the target `PyClass` (from file B).
- In file A, you must find an `import` / `from ... import ...` whose imported element resolves to that exact `PyClass`.

Below is an idiomatic way to do that using PSI.

---

### High‑level approach

1. **Get the `PyFile` for file A** (the context file).
2. **Iterate all exported names in file A** using `iterateNames()`.
3. For each name, check whether it comes **from an import** (`PyImportElement` / `PyImportedNameDefiner`).
4. Resolve that imported element; if the resolved element is the same `PyClass` from file B, then:
   - Take the parent `PyImportStatementBase` — that is the import statement you’re looking for.

This leverages exactly what we discussed before about `moduleFile.iterateNames()` returning name definers.

---

### Example code (Java/Kotlin‑style, works from an element inside file A)

Assume:
- `targetClass` is the `PyClass` from file B.
- `contextElement` is any PSI element inside file A (e.g. at the caret, or the usage expression).

```java
PyClass targetClass = ...;       // class declared in file B
PsiElement contextElement = ...; // some element inside file A

PyFile fileA = PsiTreeUtil.getParentOfType(contextElement, PyFile.class);
if (fileA == null) return;

AtomicReference<PyImportStatementBase> foundImport = new AtomicReference<>();

fileA.iterateNames((name, definer) -> {
    PyImportElement importElement = null;

    if (definer instanceof PyImportElement) {
        importElement = (PyImportElement) definer;
    } else if (definer instanceof PyImportedNameDefiner) {
        importElement = ((PyImportedNameDefiner) definer).getImportElement();
    }

    if (importElement != null) {
        PsiElement imported = importElement.resolve();
        if (imported == targetClass) {
            // We’ve found the import that defines this class in file A
            PyImportStatementBase stmt = PsiTreeUtil.getParentOfType(
                importElement, PyImportStatementBase.class
            );
            foundImport.set(stmt);
            return false; // stop iterating
        }
    }

    return true; // continue
});

PyImportStatementBase importInA = foundImport.get();
// `importInA` is the `import`/`from ... import ...` statement in file A that imports `targetClass`.
```

This works for cases like:

```python
from b import MyClass
# or
from some.pkg.b import MyClass
```

In both cases, the `PyImportElement` for `MyClass` will resolve to that `PyClass` in file B, and you get the correct `PyImportStatementBase`.

---

### If you start from a usage instead of the class

Often you’ll start from **a reference in file A**, not from `PyClass` directly. In that case:

1. Take the element under caret (e.g. a `PyReferenceExpression`).
2. Resolve it to get `targetClass`.
3. Then run the search above.

```java
PyReferenceExpression ref = ...; // usage of the class in file A
PsiElement resolved = ref.getReference().resolve();
if (resolved instanceof PyClass) {
    PyClass targetClass = (PyClass) resolved;
    // now run the iterateNames-based search shown earlier
}
```

Using this pattern you can always, from inside file A, locate **the import statement that brings a given `PyClass` (from some other file B) into scope**.