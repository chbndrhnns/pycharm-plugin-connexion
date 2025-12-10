### Identifying Python docstrings via PSI in a PyCharm plugin

In the Python plugin, docstrings are exposed via the `PyDocStringOwner` interface. Any PSI element that can have a docstring (module, class, function/method) implements this interface.

Key points:
- **Module docstring** → `PyFile` (which is a `PyDocStringOwner`)
- **Class docstring** → `PyClass` (also a `PyDocStringOwner`)
- **Function / method docstring** → `PyFunction` (also a `PyDocStringOwner`)

All of these share common APIs from `PyDocStringOwner`.

### 1. From an arbitrary PSI element
If you have some `PsiElement` and want to know whether it is a docstring owner and get its docstring:

```java
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;

PsiElement element = ...; // obtained from PSI

if (element instanceof PyDocStringOwner owner) {
    // The *string literal expression* that is the docstring, or null
    PyStringLiteralExpression docStringExpr = owner.getDocStringExpression();

    if (docStringExpr != null) {
        String text = owner.getDocStringValue(); // unescaped docstring text
        // or: String rawText = docStringExpr.getText();  // literal token text including quotes
    }
}
```

This works for:
- `PyFile` (module level)
- `PyClass` (class level)
- `PyFunction` (function or method level)

### 2. Starting specifically from file / class / function

**Module docstring:**
```java
import com.jetbrains.python.psi.PyFile;

PyFile pyFile = (PyFile) psiFile; // assuming it's Python
PyStringLiteralExpression moduleDocExpr = pyFile.getDocStringExpression();
String moduleDoc = pyFile.getDocStringValue();
```

**Class docstring:**
```java
import com.jetbrains.python.psi.PyClass;

PyClass pyClass = ...;
PyStringLiteralExpression classDocExpr = pyClass.getDocStringExpression();
String classDoc = pyClass.getDocStringValue();
```

**Function / method docstring:**
```java
import com.jetbrains.python.psi.PyFunction;

PyFunction pyFunction = ...;
PyStringLiteralExpression funcDocExpr = pyFunction.getDocStringExpression();
String funcDoc = pyFunction.getDocStringValue();
```

### 3. Checking whether a given PSI element *is* a docstring
Sometimes you want to know if a specific `PsiElement` (e.g., the caret position) is inside the docstring literal.

```java
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyDocStringOwner;

PsiElement elementAtCaret = file.findElementAt(offset);
PyStringLiteralExpression stringLit = 
        PyUtil.as(elementAtCaret.getParent(), PyStringLiteralExpression.class);

if (stringLit != null && stringLit.getParent() instanceof PyDocStringOwner) {
    // `stringLit` is the docstring of a module/class/function
}
```
(If you don’t use `PyUtil.as`, just do regular `instanceof` checks.)

### 4. Traversing PSI to find nearest docstring owner
If you’re at any element and want the enclosing element that owns a docstring (e.g., the current function or class):

```java
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyDocStringOwner;

PsiElement element = ...;
PyDocStringOwner owner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class, /* strict = */ false);

if (owner != null) {
    String doc = owner.getDocStringValue();
}
```

### 5. Summary
- Use `PyDocStringOwner` to *identify* PSI elements that can have docstrings.
- Call `getDocStringExpression()` to get the `PyStringLiteralExpression` representing the docstring.
- Call `getDocStringValue()` to get the processed text.
- You can reach the relevant owner via `PsiTreeUtil.getParentOfType` or direct casting if you already know the type (`PyFile`, `PyClass`, `PyFunction`).

This is the canonical way to work with docstrings in a PyCharm / IntelliJ-based plugin using PSI.