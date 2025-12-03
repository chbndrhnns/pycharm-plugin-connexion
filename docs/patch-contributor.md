### Short answer
Create a real `PsiReference` from the string literal and register it with a `PsiReferenceContributor`. Rename refactoring only skips plain-text searches when “Search in comments and strings” is off; it still updates any real `PsiReference`s. So if your plugin returns a reference for the `@patch("…")` argument, it will be renamed automatically.

### What to implement
- A `PsiReferenceContributor` that registers a provider for Python string literals that appear as arguments of the `@patch` decorator (or anywhere you decide).
- A `PsiReference` (usually derived from `PsiReferenceBase`) that
  - resolves the dotted text in the string to a Python `PsiElement` (function, class, attribute, etc.), and
  - overrides `handleElementRename()` to rewrite the text inside the quotes.

### Why this works
- The “Search in comments and strings” checkbox only influences plain-text searching; the rename processor always collects and updates proper `PsiReference`s. If your string literal supplies a reference, it will be included and updated during rename.

### Minimal skeleton
```xml
<!-- plugin.xml -->
<idea-plugin>
  <depends>com.intellij.modules.python</depends>
  <extensions defaultExtensionNs="com.intellij">
    <psi.referenceContributor implementation="your.pkg.PatchStringReferenceContributor"/>
  </extensions>
</idea-plugin>
```

```java
// your/pkg/PatchStringReferenceContributor.java
public class PatchStringReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PyStringLiteralExpression.class)
        .withParent(PyArgumentList.class),
      new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext ctx) {
          PyStringLiteralExpression str = (PyStringLiteralExpression) element;
          if (!isPatchArgument(str)) return PsiReference.EMPTY_ARRAY;  // check decorator name, position, etc.
          TextRange range = getRangeInsideQuotes(str);                 // map to the content w/o quotes/prefix
          return new PsiReference[]{ new PatchStringReference(str, range) };
        }
      }
    );
  }

  private static boolean isPatchArgument(PyStringLiteralExpression str) {
    PyArgumentList args = (PyArgumentList) str.getParent();
    if (args == null) return false;
    PsiElement call = args.getParent();
    if (!(call instanceof PyDecorator)) return false;
    String name = ((PyDecorator) call).getName();
    return "patch".equals(name) || "mock.patch".equals(name);
  }

  private static TextRange getRangeInsideQuotes(PyStringLiteralExpression str) {
    // For Python strings, use the node’s content range; a safe fallback is:
    int start = 1;                                    // skip starting quote
    int end = str.getTextLength() - 1;                // skip ending quote
    return new TextRange(start, end);
  }
}
```

```java
// your/pkg/PatchStringReference.java
public class PatchStringReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiPolyVariantReference {
  public PatchStringReference(@NotNull PyStringLiteralExpression element, @NotNull TextRange rangeInElement) {
    super(element, rangeInElement, /* soft = */ false);
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    String qName = getValue(); // text inside quotes for the given range
    if (qName.isEmpty()) return ResolveResult.EMPTY_ARRAY;

    // Resolve dotted name to Python PSI using the Python plugin APIs
    // (QualifiedName + resolver). Keep this part project-specific.
    Collection<PsiElement> targets = resolvePythonQualifiedName(myElement, qName);
    return targets.stream().map(PsiElementResolveResult::new).toArray(ResolveResult[]::new);
  }

  @Override
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  public Object @NotNull [] getVariants() { return EMPTY_ARRAY; }

  @Override
  public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
    // When the target’s simple name changes, replace just the last segment of the dotted string.
    String old = getValue();
    int dot = old.lastIndexOf('.');
    String updated = (dot >= 0 ? old.substring(0, dot + 1) : "") + newName;
    // Write the new content back into the string literal
    PyElementGenerator gen = PyElementGenerator.getInstance(myElement.getProject());
    PyStringLiteralExpression replacement = gen.createStringLiteralFromString(updated);
    return myElement.replace(replacement);
  }
}
```

#### Notes on resolving the Python name
- Use Python plugin utilities to resolve a dotted qualified name from a string:
  - Build a `QualifiedName` (`com.jetbrains.python.psi.resolve.QualifiedName`),
  - Create a `PyResolveContext` from the current element,
  - Use a `QualifiedNameResolver`/`PyQualifiedNameResolveContext` to find matching `PsiElement`s in the project.
- This keeps the reference “real” (it resolves to a `PsiElement`), which is the key for the rename engine.

### Testing checklist
- Put the cursor on a function/class that is referenced by `@patch("pkg.mod.Func")`.
- Invoke Rename (Shift+F6) with “Search in comments and strings” unchecked.
- The string in `@patch("…")` updates automatically because your `PsiReference` participates in rename.

### Extras / alternatives
- If you also want code completion inside the string, implement `getVariants()` to return symbol names.
- If you need to rename full qualified names when a package/module is renamed, update `handleElementRename()` to compute and rewrite the fully-qualified path based on `PsiElement` → `QualifiedName` mapping.
- You can generalize this to any decorator or framework-specific annotation by adjusting the `isPatchArgument` predicate and the resolver.
