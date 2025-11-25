### Helpers for PSI-based Argument Generation

Yes, the **IntelliJ Platform** (specifically the Python plugin) provides helpers that simplify this task and allow you
to avoid manual string concatenation for the entire call.

The recommended approach is to generate individual `PyKeywordArgument` elements and add them to the existing
`PyArgumentList`. This delegates the responsibility of handling commas, formatting, and structural integrity to the
platform.

#### Recommended Approach

1. **Create Arguments**: Use `PyElementGenerator.createKeywordArgument` to generate valid PSI for each new parameter.
2. **Add to List**: Use `PyArgumentList.addArgument` to safely insert them into the call.

This replaces your manual string building (`newArgsText.append(...)`) and the "dummy call" parsing strategy.

#### Code Example

Refactoring your `invoke` method to use these helpers:

```kotlin
override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val pyFile = file as? PyFile ?: return
    val call = findCallExpression(editor, pyFile) ?: return

    val ctx = TypeEvalContext.userInitiated(project, pyFile)
    val missing = getMissingParameters(call, ctx)
    if (missing.isEmpty()) return

    val generator = PyElementGenerator.getInstance(project)
    // Ensure argument list exists (create one if missing, though rare for a call)
    val argumentList = call.argumentList ?: return
    val languageLevel = LanguageLevel.forElement(pyFile)

    for (param in missing) {
        val name = param.name ?: continue
        val type = param.getType(ctx)

        // Recursive generation logic (keep your existing logic, but return String)
        val valueStr = generateValue(type, ctx, 0)

        // Helper 1: Create the PSI element for the keyword argument
        // Internally this safely parses "foo(name=valueStr)" and extracts the argument
        val kwArg = generator.createKeywordArgument(languageLevel, name, valueStr)

        // Helper 2: Add to the argument list
        // This handles comma insertion and placement (after positional, before *args, etc.)
        argumentList.addArgument(kwArg)
    }
}
```

### Why this is better

* **Safety**: `PyArgumentList.addArgument` handles complex edge cases (like existing `*args`, `**kwargs`, or trailing
  commas) that manual string manipulation might break.
* **Readability**: The intent is clearer—you are adding arguments, not manipulating text.
* **Correctness**: The user's concern about "PSI structure issues" (arguments being swallowed) is exactly what
  `addArgument` is designed to prevent. It ensures the AST remains valid after each insertion.

### Regarding Recursive Generation

For the recursive `generateValue` part, your approach of inspecting `PyClassType` and `PyUnionType` is correct. There is
no standard "populate dataclass" helper in the platform that does exactly this, as the logic for *what* value to
generate (defaults vs. mocks vs. nested objects) is highly specific to the intention's goal.

However, your use of `PyCallExpression.multiMapArguments` (via `getMissingParameters`) is already using the correct
standard API for analyzing missing arguments.