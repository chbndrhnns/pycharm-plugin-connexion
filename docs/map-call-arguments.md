### Goal

When the caret is on a dataclass instance creation’s keyword (the field name), obtain the Python type that was annotated
on the dataclass field.

Example (caret on `x`):
```python
from dataclasses import dataclass

@dataclass
class A:
    x: int

a = A(x=1)
```

### Short answer
- Find the surrounding `PyCallExpression`.
- Map call arguments to parameters with the Python plugin’s argument-mapping API.
- From the mapped parameter, ask `TypeEvalContext` for its `PyType`.

For dataclasses, the Python plugin provides synthetic `__init__` parameters whose types are derived from the field
annotations, so the parameter’s type you get this way is the field’s annotated type.

### Kotlin example (works in an IntelliJ/PyCharm plugin)
```kotlin
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.TypeEvalContext

fun getDataclassFieldTypeAtCaret(caretElement: PsiElement): com.jetbrains.python.psi.types.PyType? {
    val keyword = PsiTreeUtil.getParentOfType(caretElement, PyKeywordArgument::class.java, /* strict = */ false)
        ?: return null

    // Ensure we’re on the keyword name token, not inside the value
    val keywordName = keyword.keyword ?: return null
    // Surrounding call, e.g. `A(x=1)`
    val call = PsiTreeUtil.getParentOfType(keyword, PyCallExpression::class.java) ?: return null

    val project: Project = caretElement.project
    val context = TypeEvalContext.codeAnalysis(project, caretElement.containingFile)

    // Map actual arguments to parameters of the resolved callee (supports dataclass __init__ synth params)
    val mapping = call.getArgumentMapping(context)  // since 2021.3+, otherwise use helper to map
    val param: PyNamedParameter? = mapping.getMappedParameter(keyword)
    // If using older API:
    // val param: PyNamedParameter? = call.mapArguments(context).argumentsMappedToParameters[keyword] as? PyNamedParameter

    return param?.let { context.getType(it) }
}
```

Notes:

- `getArgumentMapping(TypeEvalContext)` exists in recent Python plugin builds. If your SDK has only the older API, use
  the `mapArguments(context)`/`PyCallExpression.PyArgumentsMapping` helper and fetch the parameter mapped to the
  `PyKeywordArgument`.
- The returned `PyType` will reflect the dataclass field’s annotation (including `typing` constructs like `Optional`,
  `Annotated`, `list[int]`, etc.).

### Alternative: resolve via the class field (when mapping is unavailable)
If you can’t or don’t want to rely on the argument mapping (e.g., caret is on a positional arg or older SDK), you can:
1. Resolve the callee to the constructed `PyClass`.
2. Find the dataclass field in the class body by name (`PyTargetExpression`).
3. Ask `TypeEvalContext` for the field’s type.

```kotlin
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression

fun getDataclassFieldTypeFallback(caretElement: PsiElement): com.jetbrains.python.psi.types.PyType? {
    val keyword = PsiTreeUtil.getParentOfType(caretElement, PyKeywordArgument::class.java, false) ?: return null
    val name = keyword.keyword ?: return null
    val call = PsiTreeUtil.getParentOfType(keyword, PyCallExpression::class.java) ?: return null
    val context = TypeEvalContext.codeAnalysis(caretElement.project, caretElement.containingFile)

    val calleeRef = call.callee as? PyReferenceExpression ?: return null
    val resolved = calleeRef.reference.resolve()
    val pyClass = when (resolved) {
        is PyClass -> resolved
        else -> null
    } ?: return null

    val field: PyTargetExpression = pyClass.findClassAttribute(name, true) ?: return null
    return context.getType(field)
}
```

### Handling positional arguments

For positional construction like `A(1)`, use the argument mapping’s positional part to obtain the parameter and then ask
`TypeEvalContext` for its type:
```kotlin
val mapping = call.getArgumentMapping(context)
val paramForFirstPositional: PyNamedParameter? = mapping.getParametersMappedByPosition().getOrNull(0)
val type = paramForFirstPositional?.let { context.getType(it) }
```

### Edge cases and tips

- Forward references / postponed evaluation: `TypeEvalContext.codeAnalysis` handles most cases; if you’re computing
  on-the-fly in an intention/inspection, it’s typical. For quick UI feedback,
  `TypeEvalContext.userInitiated(project, file)` is fine too.
- `InitVar` and `ClassVar`: The mapping will still give you a parameter for `InitVar`; `ClassVar` fields are usually
  excluded from `__init__` and won’t map from a call-site keyword.
- `kw_only` dataclass fields: Mapping works the same; a keyword-only field will be exposed as a named parameter.
- Stub vs. source: If a type comes from stubs or `from __future__ import annotations`, the provider still yields the
  correct `PyType`.
- If you need the textual annotation as written, read it from the field target’s `annotation` (`PyAnnotationOwner`) and
  convert/resolve as needed; for semantic type operations prefer `PyType` via `TypeEvalContext`.

### Minimal Java version
```java
PyKeywordArgument kw = PsiTreeUtil.getParentOfType(caretElement, PyKeywordArgument.class, false);
if (kw == null) return null;
String name = kw.getKeyword();
PyCallExpression call = PsiTreeUtil.getParentOfType(kw, PyCallExpression.class);
if (call == null) return null;
TypeEvalContext ctx = TypeEvalContext.codeAnalysis(caretElement.getProject(), caretElement.getContainingFile());
PyCallExpression.PyArgumentsMapping mapping = call.getArgumentMapping(ctx);
PyNamedParameter param = (PyNamedParameter) mapping.getMappedParameter(kw);
return param != null ? ctx.getType(param) : null;
```

### Summary

Use the call-site argument-to-parameter mapping and then ask `TypeEvalContext` for the mapped parameter’s `PyType`. For
dataclasses, that parameter is synthetic and carries the exact field annotation-derived type, which is what you want
when the caret is on the field name in `A(field=...)`. 