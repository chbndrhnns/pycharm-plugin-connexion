### Relevant Cases

We should handle `try-except` blocks that are semantically equivalent to `d.get()`. The transformation must preserve the logic that "default" is only used if the key is missing (ignoring the subtle difference that `get` evaluates default eagerly, which we must handle).

#### Case 1: Assignment with Fallback
Assigns a value from the dict, falling back to a default value on error.
```python
# Before
try:
    val = data[key]
except KeyError:
    val = "default"

# After
val = data.get(key, "default")
```

#### Case 2: Pre-initialized Variable (Pass Pattern)
The variable is initialized before the try block, and the except block does nothing (`pass`), effectively keeping the initial value.
```python
# Before
val = 0
try:
    val = data[key]
except KeyError:
    pass

# After
val = data.get(key, 0)
```
*Constraint: This requires identifying the immediately preceding assignment to the same variable.*

#### Case 3: Return Statement
Returns the value immediately, falling back to returning a default.
```python
# Before
try:
    return data[key]
except KeyError:
    return None

# After
return data.get(key)
```
*Note: If the default is `None`, we can omit the second argument to `get`.*

#### Case 4: None as Default
Specific optimization when the fallback is explicit `None`.
```python
# Before
try:
    x = d[k]
except KeyError:
    x = None

# After
x = d.get(k)
```

### Edge Cases to Exclude (Safety Checks)

1.  **Complex Defaults (Eager vs Lazy)**:
    `try: x = d[k] except: x = heavy_calculation()`
    Transforming this to `x = d.get(k, heavy_calculation())` changes behavior because `heavy_calculation()` runs *always* in the `get` version.
    **Rule**: Only support defaults that are **literals** (strings, numbers, None) or **simple references** (variables).

2.  **Nested Access**:
    `try: x = d[a][b] except: ...`
    This catches `KeyError` from both `d[a]` and the result `[b]`. Converting to `d[a].get(b)` crashes if `d[a]` fails.
    **Rule**: The subscription operand must be a simple reference or call, not another subscription.

3.  **Multiple Statements**:
    If the `try` block contains other logic (e.g., logging), we cannot safely collapse it.

4.  **Mixed Exceptions**:
    `except (KeyError, IndexError):` cannot be converted to `get`.

### Implementation Idea

The logic should be encapsulated in a new intention (e.g., `PyTryExceptToDictGetIntention`) or added to the existing one if checks allow.

#### 1. Availability Check (`isAvailable`)
This method verifies if the `try-except` structure matches one of the supported patterns.

*   **Trigger**: Cursor on `try` keyword or inside the `try` block.
*   **Structure**:
    *   `PyTryExceptStatement` must have exactly one `PyExceptPart`.
    *   The exception class must be `KeyError`.
    *   No `else` or `finally` parts.
    *   `try` part has exactly **one statement**.
*   **Pattern Matching**:
    *   **Assignment**:
        *   Try stmt: `target = dict[key]`
        *   Except stmt: `target = default`
        *   `target` names must match.
        *   `dict` must be a Mapping type (use `PyABCUtil`).
    *   **Pass**:
        *   Try stmt: `target = dict[key]`
        *   Except stmt: `pass`
        *   Previous sibling of `try` stmt is `target = default`.
    *   **Return**:
        *   Try stmt: `return dict[key]`
        *   Except stmt: `return default`
*   **Safety**:
    *   Verify `default` is "safe" (literal or reference).
    *   Verify `dict` operand is simple.

#### 2. Execution (`invoke`)
Constructs the new expression and replaces the code.

```kotlin
override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val tryExcept = PsiTreeUtil.getParentOfType(element, PyTryExceptStatement::class.java) ?: return
    val tryPart = tryExcept.tryPart
    val exceptPart = tryExcept.exceptParts[0]
    
    // Helper to extract data (implemented based on pattern matching logic)
    val info = extractInfo(tryExcept) ?: return 
    // info contains: target, dict, key, default, isPassCase

    val generator = PyElementGenerator.getInstance(project)
    
    // Build arguments for .get()
    val args = if (info.default.text == "None") {
        info.key.text // Optimize 'None'
    } else {
        "${info.key.text}, ${info.default.text}"
    }

    // Build the new statement text
    val newStatementText = if (info.isReturn) {
        "return ${info.dict.text}.get($args)"
    } else {
        "${info.target.text} = ${info.dict.text}.get($args)"
    }

    val newStatement = generator.createFromText(LanguageLevel.forElement(element), PyStatement::class.java, newStatementText)

    // Modification
    if (info.isPassCase) {
        // Remove the preceding initialization statement
        val prevStmt = PsiTreeUtil.getPrevSiblingOfType(tryExcept, PyStatement::class.java)
        prevStmt?.delete()
    }
    
    tryExcept.replace(newStatement)
}
```

#### 3. Structure Analysis Helper
You will need a robust helper to extract the components.

```kotlin
data class ExtractionInfo(
    val target: PsiElement, // The variable or return keyword
    val dict: PyExpression,
    val key: PyExpression,
    val default: PyExpression,
    val isReturn: Boolean = false,
    val isPassCase: Boolean = false
)

fun extractInfo(statement: PyTryExceptStatement): ExtractionInfo? {
    // ... Implement the logic described in "Availability Check" ...
    // 1. Check Try body (Assignment or Return)
    // 2. Check Except body (Assignment, Return, or Pass)
    // 3. Match targets
    // 4. Return ExtractionInfo or null
}
```