### Short answer

Yes. In the Python plugin you can do this with PSI types rather than strings: obtain a `PyType` via `TypeEvalContext`,
peel unions/optionals, then extract the underlying class (or builtin) name from a `PyClassLikeType`/`PyBuiltinType`.
That lets you support both `typing` (e.g., `Optional`, `Union`) and PEP 604 (`A | B | None`) without string parsing.

### Sketch of a PSI-based implementation

```kotlin
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyClassLikeType
import com.jetbrains.python.psi.types.PyBuiltinType
import com.jetbrains.python.psi.types.PyNoneType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.intellij.psi.util.QualifiedName

/**
 * Given any `PyElement` you want to type (annotation, expression, parameter, etc.)
 * use the type system + PSI to compute a canonical constructor name (like `list`, `dict`, `MyClass`).
 */
fun canonicalCtorName(element: PyElement, ctx: TypeEvalContext): String? {
    val t = ctx.getType(element) ?: return null
    val base = firstNonNoneMember(t) ?: return null

    // If it’s a class-like type, prefer the PSI class name.
    if (base is PyClassLikeType) {
        // `name` is the short name; fall back to last component of qualified name.
        base.pyClass?.name?.let { return it }
        base.classQName?.let { return QualifiedName.fromDottedString(it).lastComponent }
    }

    // Handle builtins represented as `PyBuiltinType` (str, int, list, dict, set, tuple, bool, float, ...)
    if (base is PyBuiltinType) {
        // `typeName` or `name` usually returns canonical builtin name
        return base.name
    }

    // Some literal/specialized types still expose an underlying class
    if (base is PyLiteralType) {
        (base.literalClassType as? PyClassLikeType)?.pyClass?.name?.let { return it }
    }

    // Fallback: try to render to text minimally; better than guessing.
    return (base as? PyPresentableType)?.presentableText ?: base.name
}

/**
 * Unwrap `Union[...]`, `Optional[...]` and PEP 604 unions and pick the first non-None member.
 */
private fun firstNonNoneMember(t: PyType): PyType? {
    // PEP 604 and typing.Union both come through as `PyUnionType` in modern plugin versions
    if (t is PyUnionType) {
        return t.members.firstOrNull { it != null && it !is PyNoneType }
    }

    // Some old code paths may model Optional as a dedicated wrapper
    if (t is PyOptionalType) {
        return t.elementType
    }

    // Not a union/optional → return as-is
    return t
}
```

### How to use it

- From an annotation: `canonicalCtorName(pyFunction.getParameterList().getParameters()[i], ctx)` (on the parameter PSI),
  or directly on the `PyAnnotationOwner`’s annotation expression.
- From an expression: `canonicalCtorName(pyCallArgument.getValueExpression(), ctx)`.
- From a resolved target (e.g., a variable): pass the corresponding `PyTargetExpression`.

`TypeEvalContext` choices:

- In inspections or intentions: `TypeEvalContext.codeAnalysis(project, file)`.
- In on-the-fly features where performance matters, reuse a context provided by the framework when possible.

### Notes and rationale

- No string parsing is needed: `PyUnionType` abstracts both `Union[...]` and `A | B` (PEP 604). Filtering out `None` is
  just member selection.
- `PyClassLikeType` gives you access to the resolved `PyClass`, so you get the real short name and can still fall back
  to the qualified name.
- `PyBuiltinType` covers builtins, so you’ll naturally return `list`, `dict`, `set`, `tuple`, `str`, `int`, `float`,
  `bool`, etc.
- The fallback to `presentableText` keeps you robust in the face of provider-specific types (e.g., `TypedDict`,
  `Literal`, `NewType`), while still avoiding brittle string surgery.

### Optional: prefer stable canonicalization policy

Depending on your usages, you may want a slightly stricter policy:

- Always return the simple class name (drop qualifiers), even if we only have a qualified name.
- Optionally normalize aliases (e.g., return `dict` for `typing.Dict`). This is usually already handled by providers
  mapping to `PyBuiltinType`/`PyClassLikeType` of `dict`.

```kotlin
private fun simpleNameFromClassLike(t: PyClassLikeType): String? {
    t.pyClass?.name?.let { return it }
    return t.classQName?.substringAfterLast('.')
}
```

### Edge cases you get “for free” by using PSI

- `Union`/`Optional` composed with generics: you still land on the outer class, not the parameter list.
- `A | None | B` vs `Optional[A]`: both become `PyUnionType` and the code path is identical.
- Qualified names from imported symbols: resolution goes through the type providers, not text.

If you share the exact PSI you start from (annotation vs expression vs target), I can tailor the call sites and any
extra guards needed for that context.