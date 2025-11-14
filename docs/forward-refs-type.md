### Answer

You don’t need (and shouldn’t write) your own text‑level parsing for quoted unions – but there is **no dedicated PSI
node that “becomes a union” for a quoted annotation**. The string literal stays a `PyStringLiteralExpression`. The union
and the forward refs are handled **only at the type level**.

So the right approach is:

1. **Stop parsing the annotation string yourself.**
2. **Always go through the type system (`TypeEvalContext` + `PyTypingTypeProvider`).**

### How to handle quoted unions via helpers

If you have something like:

```python
from __future__ import annotations


class A: ...


class B: ...


x: "A | B"
```

PSI for the annotation is just a string literal, but `PyTypingTypeProvider` knows how to interpret it and build the
union (including resolving forward refs when allowed).

Typical usage in a plugin:

```java
TypeEvalContext ctx = TypeEvalContext.userInitiated(project, file);

// For a variable, parameter, or function
PyTargetExpression target = ...;      // or PyNamedParameter, or PyFunction for return type
PyType type = ctx.getType(target);    // <- this is where quoted unions are interpreted

if(type instanceof
PyUnionType unionType){
        for(
PyType member :unionType.

getMembers()){
        // member is the resolved class type for A or B (forward ref resolved when allowed)
        }
        }
```

Key points:

- **Quoted union stays a string at PSI level.** There is no PSI “union node” you can query for quoted annotations.
- **`PyTypingTypeProvider`** is responsible for:
    - Parsing `"A | B"`, `"typing.Union[A, B]"`, etc., including forward references.
    - Handling `from __future__ import annotations` and the various typing/PEP‑604 forms.
- You access that logic exclusively through **`TypeEvalContext.getType(...)`** on the *owner* (parameter, target,
  function), not on the string literal itself.

### Forward refs and unions together

To combine with forward‑ref support:

- Forward refs in annotations (including inside unions) are gated by
  `PyResolveUtil.allowForwardReferences(PyQualifiedExpression)` and resolved by
  `PyForwardReferenceResolveProvider`.
- When you use `TypeEvalContext.getType(owner)`, the typing provider internally:
    - Checks whether forward references are allowed at that location.
    - Resolves the referenced names (including those coming from a quoted annotation string).
    - Produces a `PyUnionType` whose members are already the resolved `PyType`s.

You generally **don’t need to call `PyForwardReferenceResolveProvider` directly**; normal reference resolution and the
type providers will use it when appropriate.

### So, to your question

> The agent in my plugin still falls back to text-level parsing for quoted unions. Is there no helper/PSI approach
> available?

- There is **no PSI‑only solution** that turns a quoted union into a union node you can walk – it will remain a string
  literal in PSI.
- The **supported helper‑based approach** is to:
    - Work with `TypeEvalContext` and `PyTypingTypeProvider` via `ctx.getType(owner)`.
    - Inspect the resulting `PyUnionType` and its members instead of manually parsing the annotation text.

If you show me where you currently grab the annotation (which PSI element you start from), I can sketch the exact
replacement code using `TypeEvalContext` so your agent never needs to parse the quoted union text again.