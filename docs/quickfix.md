### Short answer

- A “quick fix with a lightbulb” is just the standard IntelliJ/PyCharm intention/quick‑fix UI: the lightbulb appears
  when there’s at least one intention action or inspection quick‑fix available at the caret. The bulb’s color reflects
  the highest problem severity at the caret (e.g., red for error, yellow for warning/weak warning; when only intentions
  are available and no problem is highlighted, it shows as a suggestion bulb).
- A “normal quick fix” usually means a fix offered by an inspection (reported problem) versus an intention action (no
  problem needed). Both are invoked with Alt+Enter and both show under the same popup; the bulb appears in the gutter
  whenever at least one is available.

In plugin terms:

- Intention actions implement `IntentionAction` (or a convenience base like `PsiElementBaseIntentionAction`). They do
  not require any prior highlighting; they provide a bulb by themselves when applicable.
- Inspection quick‑fixes implement `LocalQuickFix` (or `LocalQuickFixOnPsiElement`) and are provided by a
  `LocalInspectionTool` (or an `Annotator`). The bulb appears because there is a highlighted problem that offers fixes.

### What the bulb colors mean (practical mental model)
- Red bulb: there’s an error at the caret that has at least one fix.
- Yellow bulb: there is a warning/weak‑warning at the caret or at least an intention is available for the current
  context.
- No bulb: no intention or fix is currently applicable at the caret position.

### How to create a lightbulb in your plugin
You have two common routes. Both put an item into Alt+Enter and show the bulb when applicable.

#### Option A: Intention action (lightbulb without needing an inspection)
Use this when you want a context action even if no problem is highlighted.

```java
public class ConvertFooToBarIntention extends PsiElementBaseIntentionAction {
    @NotNull
    @Override
    public String getText() {
        return "Convert foo() to bar()";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Foo/Bar intentions";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        // Inspect PSI around caret; return true only when applicable
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        return element != null && isFooCall(element);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        WriteCommandAction.runWriteCommandAction(project, () -> applyTransformation(editor, file));
    }
}
```

Register it in `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
    <intentionAction
            languages="Python"
            implementationClass="your.pkg.ConvertFooToBarIntention"
            category="Refactoring"/>
</extensions>
```

When `isAvailable` returns true at the caret, the IDE shows the lightbulb and your action appears in Alt+Enter.

#### Option B: Inspection + quick‑fix (lightbulb because of a reported problem)
Use this when you want the IDE to highlight a problem and offer a fix.

1) Implement a `LocalInspectionTool` and register a problem via `ProblemsHolder`:

```java
public class FooProblemInspection extends LocalInspectionTool {
    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PyRecursiveElementVisitor() { // or language-appropriate visitor
            @Override
            public void visitCallExpression(PyCallExpression node) {
                if (isSuspiciousFoo(node)) {
                    holder.registerProblem(
                            node,
                            "Suspicious foo() usage",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            new ReplaceFooWithBarFix()
                    );
                }
            }
        };
    }
}
```

2) Provide the fix by implementing `LocalQuickFix`:

```java
public class ReplaceFooWithBarFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
        return "Replace foo() with bar()";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        WriteCommandAction.runWriteCommandAction(project, () -> replaceCall(element));
    }
}
```

3) Register the inspection in `plugin.xml`:

```xml
<extensions defaultExtensionNs="com.intellij">
    <localInspection
            language="Python"
            shortName="FooProblem"
            displayName="Detect suspicious foo() usage"
            groupPath="Python"
            groupName="Code style"
            enabledByDefault="true"
            level="WARNING"
            implementationClass="your.pkg.FooProblemInspection"/>
</extensions>
```

With this setup, when the inspection detects the problem, the code will be highlighted; the lightbulb appears at the
caret and Alt+Enter will show `Replace foo() with bar()`.

#### Alternative: Annotator with a fix

If you already have an `Annotator`, you can attach a fix directly to the annotation; this also shows the bulb when the
caret hits the highlighted range:

```java
public class FooAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (isSuspiciousFoo(element)) {
      holder.newAnnotation(HighlightSeverity.WARNING, "Suspicious foo() usage")
            .range(element)
            .withFix(new ReplaceFooWithBarFix())
            .create();
    }
  }
}
```

### Common gotchas

- The bulb only shows when the caret is inside a range that has an applicable intention or fix. For intentions, that
  means `isAvailable(...)` must be true for the current PSI context. For inspection/annotator fixes, the caret must be
  within the highlighted `TextRange`.
- Severity controls bulb color. If you want a red bulb, your inspection must produce an error-level highlight (e.g.,
  `ProblemHighlightType.GENERIC_ERROR` or `GENERIC_ERROR_OR_WARNING` with level set to ERROR). Warnings/weak warnings
  lead to a yellow bulb. Intentions alone lead to the suggestion bulb.
- Don’t perform PSI writes outside a write action; wrap changes in `WriteCommandAction.runWriteCommandAction`.
- Provide a stable `getFamilyName()`; the IDE may group fixes by family name.

### When to choose which
- Use an intention when it’s an optional transformation or assist (no problem to highlight).
- Use an inspection + fix when you want to flag code as problematic and guide the user to fix it.

If you share a bit about your exact use case (Python PSI element you’re targeting, whether you need a highlight, and
desired severity), I can sketch a minimal plugin skeleton tailored to it.