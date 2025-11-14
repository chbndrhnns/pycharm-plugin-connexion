### Short answer

Use these built‑in helpers from the PyCharm Python PSI/type system:

- `PyResolveUtil.allowForwardReferences(PyQualifiedExpression)` — gate that says whether forward refs are allowed at the
  PSI location (annotations, pyi, etc.).
- `PyForwardReferenceResolveProvider` — reference resolver that actually resolves names used as forward refs in
  annotations when `allowForwardReferences` is true.
- `PyUnionType` — the core union container with utilities:
    - `PyUnionType.union(type1, type2)` / `PyUnionType.union(Collection<PyType?>)`
    - `PyUnionType.unionOrNever(...)`, `PyUnionType.createWeakType(...)`, `PyUnionType.toNonWeakType(...)`,
      `unionType.getMembers()`
- `PyUnsafeUnionType.unsafeUnion(...)` — for “weak”/gradual unions when strict semantics is enabled.
- `PyTypeUtil` — stream/collect helpers for composing/decomposing unions:
    - `PyTypeUtil.toStream(PyType)` (flattens a union to a stream of members)
    - `PyTypeUtil.toUnion()` / `PyTypeUtil.toUnion(baseType)` (collectors to build a `PyUnionType` back)
- `PyTypingTypeProvider` — typing/PEP‑604 provider that parses `A | B` and `typing.Union[...]`, handles quoted
  annotations/`from __future__ import annotations`, and returns proper `PyType` trees (including unions & forward refs)
  via the platform type inference (`TypeEvalContext.getType(element)`).

### How to use them together (typical patterns)

- Resolving a forward reference inside an annotation:
  ```java
  if (PyResolveUtil.allowForwardReferences(qualifiedExpr)) {
    // The platform’s `PyForwardReferenceResolveProvider` kicks in automatically
    List<RatedResolveResult> results = qualifiedExpr.getReference().multiResolve(false);
  }
  ```

- Building/normalizing a union that may include forward‑resolved members:
  ```java
  PyType t1 = /* possibly resolved from a forward ref */;
  PyType t2 = /* another member */;
  PyType union = PyUnionType.union(t1, t2);           // null means Any when empty
  PyType safe  = PyUnionType.unionOrNever(List.of(t1, t2)); // falls back to Never
  ```

- Mapping/flattening union members when you need to post‑process types:
  ```java
  PyType mapped = PyTypeUtil.toStream(union)
      .map(member -> transform(member))
      .collect(PyTypeUtil.toUnion());
  ```

- Letting the typing provider parse PEP 604 unions and quoted forward annotations for you:
  ```java
  TypeEvalContext ctx = TypeEvalContext.userInitiated(project, file);
  PyType type = ctx.getType(annotationOwner /* e.g., PyNamedParameter, PyTargetExpression */);
  // If the source had `"A" | B` or `Union["A", B]`, `type` will be a `PyUnionType`
  // whose members include the resolved class types (forward refs resolved when allowed).
  ```

### Notes and tips

- Forward refs are only allowed in certain contexts (annotations, pyi, etc.); always check
  `PyResolveUtil.allowForwardReferences(...)` if you’re resolving manually.
- When strict union semantics is enabled (registry key `python.typing.strict.unions`), use `PyUnsafeUnionType` for the
  gradual “type | Any” cases; otherwise `PyUnionType.createWeakType(...)` is the legacy helper.
- Prefer going through `TypeEvalContext`/type providers (`PyTypingTypeProvider`) instead of hand‑parsing union syntax;
  you’ll get correct handling of `typing` aliases, `from __future__ import annotations`, and nested/quoted unions for
  free.

### TL;DR

- Resolve forward‑ref names: `PyResolveUtil.allowForwardReferences`, `PyForwardReferenceResolveProvider` (via normal
  reference resolution).
- Build/inspect unions: `PyUnionType`, `PyUnsafeUnionType`, `PyTypeUtil` collectors.
- Parse union syntax and quoted annotations: rely on `TypeEvalContext` and `PyTypingTypeProvider`.