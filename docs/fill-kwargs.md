### Approach

To create an intention that populates fields for dataclasses or methods, focusing on kwonly arguments, you can implement
a `PyBaseIntentionAction`. The core logic relies on resolving the call at the caret and identifying missing parameters
using `PyCallExpression.multiMapArguments`.

#### Key Components

1. **Intention Action**: Extend `PyBaseIntentionAction` and override `isAvailable` and `invoke`.
2. **Resolution & Mapping**: Use `PyCallExpression` to get the `PyArgumentsMapping`. This mapping tells you which
   arguments are already provided and allows you to derive which parameters are missing.
3. **Code Generation**: Use `PyElementGenerator` to create the new keyword arguments (e.g., `arg=...`) and insert them
   into the `PyArgumentList`.
4. **Filtering**: While you can filter strictly for keyword-only arguments, "populating fields" typically implies
   filling all missing required (or even optional) arguments. Using keyword syntax for all generated arguments satisfies
   the "focus on kwonly" requirement and is safe for positional-or-keyword arguments.

### Implementation

Here is the suggested implementation for the intention action.

```java
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PopulateKwOnlyArgumentsIntention extends PyBaseIntentionAction implements HighPriorityAction {

    @Override
    public @NotNull String getFamilyName() {
        return "Populate arguments";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) return false;

        PyCallExpression call = findCallExpression(editor, file);
        if (call == null) return false;

        List<PyCallableParameter> missingParams = getMissingParameters(call, TypeEvalContext.codeAnalysis(project, file));
        if (missingParams.isEmpty()) return false;

        setText("Populate missing arguments with '...'");
        return true;
    }

    @Override
    public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyCallExpression call = findCallExpression(editor, file);
        if (call == null) return;

        TypeEvalContext context = TypeEvalContext.userInitiated(project, file);
        List<PyCallableParameter> missingParams = getMissingParameters(call, context);

        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        PyArgumentList argumentList = call.getArgumentList();

        if (argumentList == null) {
            // Should usually exist for a call, but if not, one could be created
            return;
        }

        for (PyCallableParameter param : missingParams) {
            String name = param.getName();
            if (name == null) continue;

            // Create argument: name=...
            PyKeywordArgument arg = generator.createKeywordArgument(LanguageLevel.forElement(file), name, "...");
            argumentList.addArgument(arg);
        }
    }

    private PyCallExpression findCallExpression(Editor editor, PsiFile file) {
        return findElementAt(file, editor.getCaretModel().getOffset(), PyCallExpression.class);
    }

    private List<PyCallableParameter> getMissingParameters(PyCallExpression call, TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PyCallExpression.PyArgumentsMapping> mappings = call.multiMapArguments(resolveContext);

        if (mappings.isEmpty()) return List.of();

        // Take the first valid mapping (usually sufficient)
        PyCallExpression.PyArgumentsMapping mapping = mappings.get(0);
        PyCallableType callableType = mapping.getCallableType();
        if (callableType == null) return List.of();

        List<PyCallableParameter> allParams = callableType.getParameters(context);
        if (allParams == null) return List.of();

        // Get parameters that are already mapped
        Map<PyExpression, PyCallableParameter> mappedParams = mapping.getMappedParameters();

        // Filter out self/cls, already mapped parameters, and positional containers (*args, **kwargs)
        return allParams.stream()
                .filter(p -> !p.isSelf())
                .filter(p -> !p.isPositionalContainer() && !p.isKeywordContainer())
                .filter(p -> !mappedParams.containsValue(p))
                .collect(Collectors.toList());
    }
}
```

### Tests

You should add a test class extending `PyIntentionTestCase` (or `PyQuickFixTestCase` depending on your setup) to verify
the behavior.

**File:** `testSrc/com/jetbrains/python/intentions/PopulateKwOnlyArgumentsIntentionTest.java`

```java
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyIntentionTestCase;
import com.jetbrains.python.psi.LanguageLevel;

public class PopulateKwOnlyArgumentsIntentionTest extends PyIntentionTestCase {

    public void testDataclassPopulation() {
        doTest("Populate missing arguments with '...'",
                """
                        from dataclasses import dataclass
                        @dataclass
                        class A:
                            x: int
                            y: int
                            z: int = 1
                        
                        a = A(<caret>)
                        """,
                """
                        from dataclasses import dataclass
                        @dataclass
                        class A:
                            x: int
                            y: int
                            z: int = 1
                        
                        a = A(x=..., y=..., z=...)
                        """);
    }

    public void testKwOnlyMethods() {
        doTest("Populate missing arguments with '...'",
                """
                        class C:
                            def foo(self, *, a, b):
                                pass
                        
                        C().foo(<caret>)
                        """,
                """
                        class C:
                            def foo(self, *, a, b):
                                pass
                        
                        C().foo(a=..., b=...)
                        """);
    }

    public void testNestedClass() {
        doTest("Populate missing arguments with '...'",
                """
                        from dataclasses import dataclass
                        class Outer:
                            @dataclass
                            class Inner:
                                f: int
                        
                        Outer.Inner(<caret>)
                        """,
                """
                        from dataclasses import dataclass
                        class Outer:
                            @dataclass
                            class Inner:
                                f: int
                        
                        Outer.Inner(f=...)
                        """);
    }

    public void testPartialArguments() {
        doTest("Populate missing arguments with '...'",
                """
                        def foo(a, b, c): pass
                        
                        foo(1, <caret>)
                        """,
                """
                        def foo(a, b, c): pass
                        
                        foo(1, b=..., c=...)
                        """);
    }
}
```

### Notes

* **Nested Classes**: `PyCallExpression` resolution handles nested classes automatically, so no special logic is needed
  there.
* **Keyword Arguments**: The implementation generates keyword arguments (`name=...`) for all missing parameters. This
  correctly handles keyword-only arguments (which *must* be named) and positional-or-keyword arguments (which *can* be
  named).
* **Missing vs Required**: The `getMissingParameters` logic above populates *all* parameters that weren't passed,
  including those with default values (like `z` in the first test case). If you prefer to only populate **required**
  arguments, you can add a check `!param.hasDefaultValue()` in the filter stream.
* **`...` (Ellipsis)**: This is a valid value in Python 3 and serves as a good placeholder.