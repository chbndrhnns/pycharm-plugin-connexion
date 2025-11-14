### Goal

Add an import for a resolved Python class (or module) using PSI utilities, while

- reusing existing imports where possible (e.g., add a name to an existing `from x import ...`), and
- avoiding imports for builtins or from stub-only locations.

Below are minimal, working idioms that use the platform’s existing machinery.

### The one-call happy path

If you already have the resolved symbol and the PSI of the usage location, prefer the single entry point
`AddImportHelper.addImport`.

```java
// Java

import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

void addImportForResolved(PsiNamedElement resolvedSymbol, PsiFile file, PyElement usageAnchor) {
    // This will:
    // - decide module vs from-import (according to PyCodeInsightSettings)
    // - reuse existing "from" imports by appending a new name
    // - insert at the correct place (top of file or nearest block using the anchor)
    AddImportHelper.addImport(resolvedSymbol, file, usageAnchor);
}
```

```kotlin
// Kotlin
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.PyElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement

fun addImportForResolved(resolvedSymbol: PsiNamedElement, file: PsiFile, usageAnchor: PyElement) {
    AddImportHelper.addImport(resolvedSymbol, file, usageAnchor)
}
```

This one call handles the common cases, including reusing an existing `from pkg import …` when appropriate. If the user
preference `PyCodeInsightSettings.PREFER_FROM_IMPORT` is enabled, it will try to add a `from` import; otherwise it adds
a module import and qualifies the usage (e.g., replaces `ClassName` with `pkg.ClassName`).

### Ensuring you don’t import builtins

Before adding anything, skip imports for builtins. Use `PyBuiltinCache` to check:

```kotlin
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.intellij.psi.PsiElement

fun isBuiltinSymbol(symbol: PsiElement, context: PsiElement): Boolean =
    PyBuiltinCache.getInstance(context).isBuiltin(symbol)
```

Guard the import like this:

```kotlin
fun addImportIfNeeded(symbol: PsiNamedElement, file: PsiFile, anchor: PyElement) {
    if (PyBuiltinCache.getInstance(anchor).isBuiltin(symbol)) return
    AddImportHelper.addImport(symbol, file, anchor)
}
```

This prevents importing names that live in `builtins` (or their typeshed stubs) — exactly what you want when you say
“builtins should not be imported from stubs.”

### If you need explicit control (module vs from-import)

Sometimes you want to be explicit: reuse an existing module import, or add a name to a specific `from` import. Use these
helpers that power `addImport` under the hood.

- Add or update a `from` import (reuses an existing matching `from` when possible):

```java
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;

boolean ensureFromImport(PsiFile file, String from, String name, PsiElement anchor) {
    // Priority helps sorting within import groups; compute it from the resolved file if you have it.
    ImportPriority prio = null; // optional, you can pass null
    return AddImportHelper.addOrUpdateFromImportStatement(file, from, name, /*asName*/ null, prio, anchor);
}
```

- Add a top-level `import module` (no symbol import):

```java
boolean ensureModuleImport(PsiFile file, String qualifiedModule, PsiElement anchor) {
    return AddImportHelper.addImportStatement(file, qualifiedModule, /*asName*/ null, /*priority*/ null, anchor);
}
```

Both methods will:

- detect and avoid duplicate imports;
- reuse an existing `from x import ...` by appending `name` to it when splitting is not forced by code style;
- place the import in the correct block using `anchor` (e.g., inside a `try/except` or after another import);
- respect sorting and grouping according to your code style settings.

Tip: If you know the resolved `PsiFileSystemItem` for the module you’re importing from, you can compute a consistent
priority for grouping/sorting:

```java
import com.intellij.psi.PsiFileSystemItem;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;

ImportPriority priorityFor(PsiFileSystemItem toImport, PsiElement importLocation) {
    return AddImportHelper.getImportPriority(importLocation, toImport);
}
```

### Getting a canonical import path for a class

If you have a resolved `PyClass` (or any `PsiNamedElement`) and need its import path, rely on
`QualifiedNameFinder.findCanonicalImportPath` rather than crafting the path yourself. This API already accounts for
stubs vs sources and package layouts.

```kotlin
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

fun canonicalPathFor(target: PsiNamedElement, context: PyElement): String? =
    QualifiedNameFinder.findCanonicalImportPath(target, context)?.toString()
```

That value can be fed into `addOrUpdateFromImportStatement` (`from` part is the path without the symbol; `name` is the
target’s name).

### End-to-end example: add import for a resolved class usage

This example shows all guards and reuse of existing imports.

```kotlin
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.impl.PyBuiltinCache

fun ensureImportedClass(targetClass: PsiNamedElement, file: PsiFile, usage: PyElement) {
    // 1) Don’t import builtins
    if (PyBuiltinCache.getInstance(usage).isBuiltin(targetClass)) return

    // 2) Let the platform handle reuse and form selection
    AddImportHelper.addImport(targetClass, file, usage)

    // If you prefer explicit control, you can do:
    // val qn = QualifiedNameFinder.findCanonicalImportPath(targetClass, usage) ?: return
    // AddImportHelper.addOrUpdateFromImportStatement(file, qn.toString(), targetClass.name!!, null, null, usage)
}
```

### How reuse of existing imports works under the hood

- For `from` imports: `addOrUpdateFromImportStatement` scans existing `from` imports in the same import block (or the
  whole file if no block constraint) and appends the new name when the source matches. It will also sort names if your
  code style requires it.
- For `import module`: `addImportStatement` avoids duplicates and inserts in the proper import group.
- Both honor the `anchor` element to pick the correct import block and relative insertion position.

### Practical notes and pitfalls

- Always pass a meaningful `anchor` (the PSI of the usage site). This keeps imports in conditional or localized scopes
  if the usage is inside them.
- If you’re working within a code fragment, `PyCodeFragmentWithHiddenImports` is supported: imports will be added to the
  fragment’s hidden import storage instead of the physical file.
- Respect user settings:
    - `PyCodeInsightSettings.PREFER_FROM_IMPORT` toggles module import vs `from` import when both are viable.
    - Optimize-imports settings influence whether an existing `from` import is split or appended to and whether names
      are sorted.
- Builtins and stubs:
    - `PyBuiltinCache.isBuiltin(symbol)` is the authoritative check; skip adding any import if it returns true.
    - When the resolver yields a `.pyi` element, the platform attempts to find the original `.py` element automatically
      when determining import priority and paths, so you generally don’t need to special-case stubs beyond the builtin
      check.

### TL;DR

- Use `AddImportHelper.addImport(resolvedSymbol, file, usageAnchor)` for 90% of cases.
- Guard with `PyBuiltinCache.getInstance(anchor).isBuiltin(symbol)` to avoid importing builtins.
- For explicit control, call `addOrUpdateFromImportStatement` or `addImportStatement` directly with a canonical path
  from `QualifiedNameFinder`.
