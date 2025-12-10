### Goal

Add an **“Introduce Parameter Object”** refactoring for Python in PyCharm, implemented as a plugin. The refactoring
should:

- Take a function/method with several related parameters
- Introduce a new `@dataclass` that groups those parameters
- Change the function signature to accept an instance of that dataclass
- Update all call sites accordingly

Below is a sketch of:

- The **user-facing behavior** (MVP)
- The **main transformation algorithm** and edge cases
- The **minimal plugin structure** in the IntelliJ platform
- A **test‑driven, MWE-style plan** for getting there

I’ll assume Java/Kotlin plugin code operating on the Python PSI.

---

### 1. Define the minimal, initial behavior (MVP)

Start with a narrow, predictable feature set and grow from there.

#### 1.1. MVP constraints

For the first version:

- Only support:
    - Top-level functions and methods defined in Python files (`PyFunction`)
    - Positional‑only / standard positional parameters without defaults
    - Call sites using simple positional arguments, no `*args`/`**kwargs`
- The parameter object:
    - Is always introduced as a **new `@dataclass`** in the same file as the function
    - Has one field per selected parameter, in order
    - Uses simple type hints if they’re already present on the function parameters
- The refactoring entry point:
    - Invoked when caret is on a parameter list or function name via context menu:
      `Refactor | Introduce Parameter Object…`

#### 1.2. Example before/after (MWE target)

**Before**:

```python
from typing import Optional


def create_user(first_name: str, last_name: str, email: str, age: int) -> None:
    print(first_name, last_name, email, age)


def main() -> None:
    create_user("John", "Doe", "john@example.com", 30)
```

**After** (MVP behavior):

```python
from dataclasses import dataclass
from typing import Optional


@dataclass
class CreateUserParams:
    first_name: str
    last_name: str
    email: str
    age: int


def create_user(params: CreateUserParams) -> None:
    print(params.first_name, params.last_name, params.email, params.age)


def main() -> None:
    create_user(CreateUserParams("John", "Doe", "john@example.com", 30))
```

MVP focus is **signature + call sites**. Adjusting the function body (e.g., rewriting parameter usages to
`params.first_name` instead of `first_name`) can be treated as *phase 2* if you want to start ultra‑minimal. But in
practice you’ll probably want to do it from the start because leaving the body unchanged would break code.

---

### 2. Core transformations & PSI operations

Conceptually, the refactoring does three things:

1. **Introduce dataclass** (class definition + imports)
2. **Change function signature**
3. **Update function body and call sites**

Let’s outline them in PSI terms.

#### 2.1. Selecting parameters

User selects parameters via a dialog or preselection logic. For MVP, assume:

- If caret is inside parameter list, default to “all non‑`self` parameters that don’t have `*` or `**`”.
- Later you can add a UI checklist.

From PSI:

- Start with `PyFunction`:
    - `PyParameterList parameterList = function.getParameterList();`
    - Get `PyParameter[] parameters = parameterList.getParameters();`
    - Filter out `self` / `cls` for methods as needed.

Store:

- `List<PyNamedParameter> selectedParameters`
- Original order indices to map arguments at call sites.

#### 2.2. Introducing the dataclass

Steps:

1. **Name suggestion**
    - Derive name from function name: e.g., `create_user` → `CreateUserParams`.
    - Use existing name generation utilities (e.g. similar logic used in `IntroduceVariable`/`ExtractParameter`) or
      custom.

2. **Determine insertion point**
    - MVP: insert right above the function definition.

3. **Determine fields and types**
   For each `PyNamedParameter p`:
    - Field name: `p.getName()`
    - Field type:
        - If `p.getAnnotationValue()` is non‑null, use that expression as a text for the dataclass annotation.
        - Else, leave it unannotated in MVP.

4. **Generate class PSI**
    - Create a template code snippet as string:
      ```python
      @dataclass
      class {ClassName}:
          {field1}: {type1}
          {field2}: {type2}
      ```
    - Use `PyElementGenerator` with the project language level to parse it into a `PyClass` PSI element.
    - Insert via `file.addBefore(newClass, function)` or appropriate `addAfter`/`addBefore`.

5. **Ensure import of `dataclass`**
    - Use existing import utilities (`PyImportStatement`, `PyImportElement`, or helper in `PyPsiUtils` /
      `AddImportHelper`).
    - MVP: add `from dataclasses import dataclass` if not present.
    - Later: respect import style (combine imports, avoid duplicates, etc.).

#### 2.3. Adjust function signature

Replace the selected parameters with a single parameter.

1. **Create new param**
    - Name: `params` (or suggested by user).
    - Type annotation: the dataclass name, if the function already uses annotations.
    - Generate with `PyElementGenerator.createParameter("params: " + className)`.

2. **New parameter list**
    - Start from existing `PyParameterList`.
    - Remove the selected parameters.
    - Insert the new `params` parameter in their place (MVP: at position of the first selected parameter).

3. **Edge case (methods)**
    - If method has `self`, keep it as first parameter.
    - Do not apply refactoring to `self`/`cls`.

#### 2.4. Rewrite function body

Within the `PyFunction` body:

- For each selected parameter name `p_name`:
    - Find usages that resolve to that parameter symbol **inside this function**.
    - Replace each usage with `params.p_name` (or chosen parameter‑object variable name).

Implementation idea:

- Use a reference search (e.g. local search utilities), limited to the function scope.
- For each `PsiReference ref` → `PsiElement element` (a `PyReferenceExpression`):
    - Replace with new expression generated via
      `PyElementGenerator.createExpressionFromText("" + paramObjectName + "." + p_name)`.

#### 2.5. Rewrite call sites

For each call to the function:

- Find all `PyCallExpression` whose resolved callee is the chosen function.
- For each call:
    - Map the function parameters to arguments.
    - Build a `CreateUserParams(...)` call **in the correct argument order**.

MVP: only allow simple positional arguments.

Algorithm per call:

1. `PyArgumentList argList = call.getArgumentList();`
2. `PyExpression[] args = argList.getArguments();`
3. For each selected parameter in order, get its corresponding argument expression in this call.
4. Build inner call expression text:
   ```python
   {ClassName}({argExpr1}, {argExpr2}, ...)
   ```
5. Replace the original arguments for those parameters with a single `ClassName(...)` expression.

You also must consider:

- Some parameters might not be selected; they remain as separate arguments.
- `ClassName(...)` expression goes in the slot of the first selected argument.

---

### 3. Edge cases & future extensions

You want to be aware of these early, but you don’t need to support all in the MVP.

#### 3.1. Parameters you might skip initially

- Parameters with default values
- `*args`, `**kwargs`
- Keyword‑only arguments
- Positional‑only arguments (Python 3.8+ `def f(a, /, b)`)
- `@overload` functions
- Properties (`@property`)
- Static and class methods (`@staticmethod`, `@classmethod`)
- Functions defined in stubs (`.pyi`)
- Local (nested) functions and lambdas

For MVP behavior:

- Detect these patterns.
- Either:
    - Disable refactoring with a message, or
    - For some simpler cases (e.g., parameters with defaults), support them later.

#### 3.2. Call site complexities

Later, you may need to handle:

- Keyword arguments: `create_user(first_name="John", last_name="Doe", ...)`
- Mixed positional and keyword arguments
- Missing arguments (using defaults)
- Star‑unpacking: `create_user(*args)` or `create_user(**data)`

Reasonable strategy:

- MVP: if a call uses anything but simple positional arguments, either:
    - Skip that call and mark refactoring as partial, or
    - Forbid refactoring if any unsupported call pattern exists.

#### 3.3. Naming & conflicts

- Dataclass name conflicts with existing symbols in file/module.
    - Use name suggestion utilities to ensure uniqueness (e.g., `CreateUserParams`, `CreateUserParams1`, ...).
- Parameter object variable name conflicts with local variables.
    - Offer in‑place rename or just rely on local rename refactoring afterwards.

#### 3.4. Formatting & imports

- Use PyCharm’s code style manager to reformat the inserted class, function signature, and call expressions.
- Use Python import helpers to avoid duplicate imports and to respect project style (e.g. “one import per line” vs
  grouped).

---

### 4. Plugin architecture in IntelliJ/PyCharm

You’ll need:

1. **Action / Intention / Refactoring entry point**
    - You can implement either:
        - An **intention action** (e.g., quick‑fix style “Introduce parameter object”) shown when caret is on `def` name
          or parameter; or
        - A **refactoring** integrated into `Refactor | …` menu.
    - MVP: Intention is simpler to wire:
        - Implement `IntentionAction` / `PsiElementBaseIntentionAction` specialized for Python PSI.

2. **Refactoring processor**
    - Create a dedicated class, e.g. `PyIntroduceParameterObjectProcessor`, responsible for applying all PSI changes
      inside a write action.
    - This makes it testable and lets you reuse it from tests and UI.

3. **Extension registration**
    - Register the intention/refactoring in `plugin.xml` under `com.intellij.codeInsight.intentionAction` or
      `com.intellij.refactoring` as appropriate.

4. **Language‑specific hooks**
    - Use `PyElementTypes`, `PyElementGenerator`, and Python PSI structures (`PyFunction`, `PyClass`,
      `PyCallExpression`, etc.).

---

### 5. Test‑driven/MWE path

Build the feature in **very small, test‑driven steps**.

Assuming you use the existing Python test framework in the `intellij-community` repo (e.g. `PyLightFixtureTestCase` /
`PyTestCase` style), you can:

#### 5.1. Test 1 – Simplest happy path

Test file content (input):

```python
# caret: <caret> on 'create_user'

def create_user(first_name, last_name, email, age):
    print(first_name, last_name, email, age)


def main():
    create_user("John", "Doe", "john@example.com", 30)
```

Expected result after the intention/refactoring:

```python
from dataclasses import dataclass


@dataclass
class CreateUserParams:
    first_name: Any
    last_name: Any
    email: Any
    age: Any


def create_user(params: CreateUserParams):
    print(params.first_name, params.last_name, params.email, params.age)


def main():
    create_user(CreateUserParams("John", "Doe", "john@example.com", 30))
```

You can skip type hints (use `Any` or no annotation) in the very first test to avoid type‑related complexity.

Test steps (in Kotlin/Java pseudocode):

```kotlin
fun `test simple introduce parameter object`() {
    myFixture.configureByText("a.py", initialText)
    myFixture.launchIntention("Introduce parameter object") // your intention name
    myFixture.checkResult(expectedText)
}
```

#### 5.2. Test 2 – Method with self

Input:

```python
class UserService:
    def create_user(self, first_name, last_name):
        return first_name + last_name


def main(service: UserService):
    service.create_user("John", "Doe")
```

Expected:

```python
from dataclasses import dataclass


@dataclass
class CreateUserParams:
    first_name: Any
    last_name: Any


class UserService:
    def create_user(self, params: CreateUserParams):
        return params.first_name + params.last_name


def main(service: UserService):
    service.create_user(CreateUserParams("John", "Doe"))
```

Checks:

- `self` is preserved.
- Member call is updated.

#### 5.3. Test 3 – Existing dataclass import

Input has `from dataclasses import dataclass` already. Assert that you don’t duplicate it.

#### 5.4. Test 4 – Function with type‑annotated parameters

Ensure type annotations propagate to the dataclass fields and optionally to the parameter object type in the signature.

#### 5.5. Negative tests

- Function uses `*args` or `**kwargs` → refactoring **not available**, or shows a meaningful error.
- Call sites with keyword arguments → same.

These tests drive the validations in your “isAvailable” logic.

---

### 6. Rough class outline (Kotlin‑flavored sketch)

```kotlin
class PyIntroduceParameterObjectIntention : PsiElementBaseIntentionAction() {

    override fun getText() = "Introduce parameter object"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        val parameters = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filterNot { it.isSelf || it.isCls }

        if (parameters.size < 2) return false
        // Further checks for unsupported features / calls here
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
        PyIntroduceParameterObjectProcessor(function).run()
    }
}
```

Processor sketch:

```kotlin
class PyIntroduceParameterObjectProcessor(private val function: PyFunction) {

    fun run() {
        val project = function.project
        WriteCommandAction.runWriteCommandAction(project) {
            val params = collectParameters(function)
            val dataclass = createDataclass(function, params)
            replaceFunctionSignature(function, dataclass, params)
            updateFunctionBody(function, dataclass, params)
            updateCallSites(function, dataclass, params)
        }
    }
}
```

This is enough for a meaningful MWE in tests.

---

### 7. Summary of cases to consider

When you’re enumerating cases for this refactoring, think in three buckets:

1. **Signature patterns**
    - Simple positional parameters (MVP)
    - Defaults, `*args`, `**kwargs`, keyword‑only, positional‑only
    - Methods (`self`/`cls`), static, class methods
    - Overloads and properties

2. **Call site patterns**
    - Simple positional
    - Keyword calls
    - Missing arguments (defaults used)
    - Star‑unpacking, partial application patterns

3. **Context & semantics**
    - Imports, dataclass decoration
    - Type hints on parameters and return types
    - Name conflicts for class and parameter object variable
    - Scope (top‑level vs nested functions)

Starting small with a narrow, test‑backed MWE for **top‑level simple functions + positional calls** will give you a
solid foundation. From there, you can add support for more parameter kinds and call patterns, each guarded by focused
tests.

If you’d like, next we can:

- Draft a concrete test class in your repo’s style (e.g., `PyIntroduceParameterObjectTest`), or
- Sketch the exact PSI utility functions you’ll need for mapping parameters to arguments robustly.