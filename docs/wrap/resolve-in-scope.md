### Short answer

Use `PyResolveUtil.resolveQualifiedNameInScope(...)` with the nearest `ScopeOwner` and a `TypeEvalContext`. If it
returns at least one PSI element, the symbol is available in that scope (taking into account local names, imports,
qualified access, and builtins).

### Why this works

`resolveQualifiedNameInScope` (in `com.jetbrains.python.psi.resolve.PyResolveUtil`) walks outward through Python scopes,
tries to resolve the first identifier as an unqualified name, and, if needed, continues resolving the rest of the
qualified name through types (modules, classes, instances). If no lexical candidate is found, it also checks Python
builtins via `PyBuiltinCache`.

### Minimal Java example

```java
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;

// `contextElement` is any PSI element inside the place you care about (caret, reference, etc.)

boolean isSymbolInScope(PsiElement contextElement, String dottedName) {
    PyFile file = (PyFile) contextElement.getContainingFile();
    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(contextElement);
    if (scopeOwner == null) scopeOwner = file; // fallback to module scope

    TypeEvalContext tec = TypeEvalContext.codeAnalysis(contextElement.getProject(), file);
    return !PyResolveUtil
            .resolveQualifiedNameInScope(QualifiedName.fromDottedString(dottedName), scopeOwner, tec)
            .isEmpty();
}
```

- Pass a single identifier (e.g., `"foo"`) to test an unqualified name in the current scope.
- Pass a dotted name (e.g., `"pkg.mod.attr"`) to check if the qualified chain is resolvable starting from something
  visible in this scope.

### Kotlin variant

```kotlin
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

fun isSymbolInScope(contextElement: PsiElement, dottedName: String): Boolean {
    val file = contextElement.containingFile as PyFile
    val owner: ScopeOwner = ScopeUtil.getScopeOwner(contextElement) ?: file
    val tec = TypeEvalContext.codeAnalysis(contextElement.project, file)
    val results = PyResolveUtil.resolveQualifiedNameInScope(
        QualifiedName.fromDottedString(dottedName), owner, tec
    )
    return results.isNotEmpty()
}
```

### Notes and caveats

- Builtins: If no local/imported candidate is found for the first identifier, the method will also consider Python
  builtins (`PyBuiltinCache`). This mirrors Python name resolution (LEGB with builtins fallback) and may return true for
  names like `len` even if not explicitly imported.
- Flow insensitivity: The resolution is not strictly flow-sensitive; it approximates availability rather than executing
  control flow. This is generally what you want for editor-time checks and inspections.
- Scope choice: If you want to test only the module level (ignoring inner scopes), pass the `PyFile` (which is a
  `ScopeOwner`) as `scopeOwner` instead of the nearest owner.
- Performance: Build `TypeEvalContext` appropriate to your use case (`codeAnalysis` for inspections, `userInitiated`
  when running on explicit actions). Consider caching results with a `CachedValue` if you call this frequently.
- “Declared here only” vs. “available”: If you need only names declared directly in the exact scope (excluding imported
  ones), use the control-flow `Scope` API from `ControlFlowCache` to inspect declared elements, but be aware that this
  won’t reflect full Python import/name resolution. For what most plugins mean by “available in scope,”
  `resolveQualifiedNameInScope` is the right utility.

### Quick usage patterns

- Check local/outer/module/builtins: pass `"name"`.
- Check attribute on something visible (module/class/instance): pass `"name.attr"` or longer chains.
- Retrieve the actual targets: use the returned `List<PsiElement>` to see exactly what was resolved (functions, classes,
  variables, imported names, etc.).

### Troubleshooting

- If you get an empty list but expect a hit, verify you computed the correct `ScopeOwner` (e.g., inside a function vs.
  at module level), and that your `TypeEvalContext` matches the phase (inspections vs. completion).