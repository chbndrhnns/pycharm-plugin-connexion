### Plan to Implement "Toggle Lambda <-> Function" Quickfix in PyCharm

To implement a "toggle" feature, we need two complementary intention actions:

1. `PyConvertLambdaToFunctionIntention` (Existing): Converts a lambda expression to a named function.
2. `PyConvertFunctionToLambdaIntention` (New): Converts a simple named function into a lambda expression assigned to a
   variable.

These two intentions will appear contextually, effectively providing a "toggle" experience.

#### 1. Prerequisites & Feasibility

**For Function -> Lambda conversion:**

* **Single Expression Body:** The function body must consist of a single statement that can be reduced to an expression.
    * `return <expression>` -> `lambda: <expression>`
    * `<expression>` (ExpressionStatement) -> `lambda: <expression>` (common in callbacks)
* **No Complex Logic:** No control flow statements (`if`, `for`, `while`, `try`) unless they are part of an expression (
  e.g., ternary `if` or list comprehensions).
* **No Decorators:** Python lambdas cannot be decorated with `@`.
* **No Type Annotations:** Python lambdas do not support type hints. The intention should either be unavailable if hints
  exist, or strip them with a warning (unavailable is safer/cleaner).
* **No Async/Generator:** Lambdas cannot be `async` or contain `yield`.
* **Naming:** A function `def foo(): ...` transforms into `foo = lambda: ...`.

#### 2. Implementation Plan

**A. Modify/Verify Existing `PyConvertLambdaToFunctionIntention`**

* Ensure it handles edge cases like preserving default parameter values and `*args`/`**kwargs`.
* Ensure it generates a unique name if converting an anonymous lambda (already does).

**B. Create `PyConvertFunctionToLambdaIntention`**

**Class Structure:**

```java
public class PyConvertFunctionToLambdaIntention extends PyBaseIntentionAction {
    // ...
}
```

**`isAvailable` Logic:**

1. **Target:** Check if the element at the caret is a `PyFunction` or its name identifier.
2. **Body Check:**
    * Get `function.getStatementList()`.
    * Must have exactly 1 statement.
    * Statement must be `PyReturnStatement` OR `PyExpressionStatement`.
3. **Attributes Check:**
    * `function.getDecoratorList().getDecorators()` must be empty.
    * `function.isAsync()` must be false.
    * `function.isGenerator()` must be false.
    * `function.getDocStringValue()` must be null (lambdas have no docstrings).
    * Check parameters and return type for annotations. If present, return `false`.

**`doInvoke` Logic:**

1. **Extract Components:**
    * **Name:** `function.getName()`
    * **Parameters:** `function.getParameterList().getText()` (need to parse/rebuild to ensure clean format, or just
      reuse text if valid).
    * **Body:** Extract the expression from the `return` statement or the expression statement.
2. **Construct Replacement:**
    * Format: `name = lambda params: expression`
    * Create a `PyAssignmentStatement` using `PyElementGenerator`.
3. **Replace:**
    * Replace the original `PyFunction` element with the new `PyAssignmentStatement`.

#### 3. Edge Cases

| Edge Case          | Handling Strategy                                                                                                                                                    |
|:-------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Recursion**      | `def f(): return f()` -> `f = lambda: f()`. This is valid in Python as `f` is bound in the enclosing scope.                                                          |
| **Self Parameter** | `class A: def m(self): return 1`. -> `class A: m = lambda self: 1`. Valid.                                                                                           |
| **Usage**          | The change from `def name` to `name = lambda` preserves the symbol `name`, so usages remain valid.                                                                   |
| **Comments**       | If the function has comments inside the body, they will be lost or need to be moved before the assignment. **Decision:** Drop internal comments or make unavailable. |
| **Docstrings**     | Unavailable if docstring exists.                                                                                                                                     |

#### 4. Extensive Testing Plan

Create a new test class `PyConvertFunctionToLambdaIntentionTest` extending `PyIntentionTest`.

**Positive Tests:**

* **Basic Return:**
  ```python
  def foo(x): return x + 1
  # Becomes
  foo = lambda x: x + 1
  ```
* **No Args:** `def foo(): return 42` -> `foo = lambda: 42`
* **Multiple Args:** `def add(x, y): return x + y` -> `add = lambda x, y: x + y`
* **Default Values:** `def foo(x=1): return x` -> `foo = lambda x=1: x`
* **Varargs/Kwargs:** `def foo(*args, **kwargs): return 1` -> `foo = lambda *args, **kwargs: 1`
* **Expression Body (No Return):** `def log(m): print(m)` -> `log = lambda m: print(m)`
* **Ternary Operator:** `def abs(n): return n if n > 0 else -n` -> `abs = lambda n: n if n > 0 else -n`

**Negative Tests (Intention should not appear):**

* **Multiple Statements:**
  ```python
  def foo():
      print("a")
      return 1
  ```
* **Control Flow:**
  ```python
  def foo(x):
      if x: return 1
      return 2
  ```
* **Decorators:** `@decorator def foo(): ...`
* **Type Hints:** `def foo(x: int) -> int: ...`
* **Docstrings:** `def foo(): """doc""" ...`
* **Async:** `async def foo(): ...`
* **Yield:** `def foo(): yield 1`

#### 5. Integration

* Register the new intention in `plugin.xml` (or `python-psi-impl.xml`) under `<intentionAction>`.
* Ensure it shares the same family name if you want them to be grouped, though usually "Convert lambda to function"
  and "Convert function to lambda" are distinct enough.

This plan covers the implementation of the missing direction of the toggle, ensuring a robust user experience.