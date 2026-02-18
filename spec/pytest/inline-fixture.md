# Inline Fixture Refactoring

This document describes the implementation plan for the "Inline Fixture" refactoring — the inverse of "Extract Fixture".

---

## 1) Problem Statement

When a pytest fixture is trivial, used in only one place, or adds unnecessary indirection, developers want to inline its
body back into the consuming test or fixture. PyCharm's built-in **Inline** refactoring doesn't understand pytest
fixtures — it doesn't know that fixture parameters are resolved by pytest's dependency injection rather than regular
function calls.

### Example: Current Behavior (Problem)

```python
import pytest


@pytest.fixture
def db_connection():
    return create_db()


@pytest.fixture
def user(db_connection):
    u = User(name="test")
    db_connection.save(u)
    return u


def test_user_name(user):
    assert user.name == "test"
```

If a developer wants to inline `user` into `test_user_name`, PyCharm's built-in Inline does nothing useful — it doesn't
understand that `user` in the parameter list is a fixture reference, not a regular parameter.

### Desired Behavior

After inlining `user` into `test_user_name`:

```python
import pytest


@pytest.fixture
def db_connection():
    return create_db()


def test_user_name(db_connection):
    u = User(name="test")
    db_connection.save(u)
    assert u.name == "test"
```

What happened:

1. The fixture parameter `user` was removed from the test signature
2. The fixture's dependencies (`db_connection`) were added to the test signature
3. The fixture body was inlined at the top of the test
4. The `return` statement was removed; references to the fixture name (`user`) in the test body were replaced with the
   returned variable (`u`)
5. The fixture definition was deleted (since it had only one usage)

---

## 2) Goals

1. **Fixture-aware inlining:** Detect that a parameter is a pytest fixture and inline its body
2. **Dependency promotion:** Move the inlined fixture's dependencies (parameters) into the consumer's signature
3. **Return value handling:** Replace fixture name references with the actual returned value/variable
4. **Single vs. multi-usage:** Support inlining into one usage (keep fixture) or all usages (remove fixture)
5. **Yield fixture support:** Handle `yield` fixtures by inlining only the setup portion
6. **Source locations:** Resolve fixtures from same file and from `conftest.py`
7. **Refactor menu integration:** Accessible via Refactor → Inline Pytest Fixture context menu

---

## 3) Implementation Approach

### 3.1 Architecture Overview

```
InlineFixtureRefactoringAction (PyBaseRefactoringAction)
├── isEnabledOnElementInsideEditor() → checks if caret is on a fixture parameter or fixture definition
└── getHandler() → InlineFixtureRefactoringHandler

InlineFixtureRefactoringHandler (RefactoringActionHandler)
├── invoke() → resolves fixture, analyzes usages, shows dialog
└── InlineFixtureDialog (UI) → collects user choices
    ├── Inline all usages and remove fixture
    ├── Inline this usage only (keep fixture)
    └── Preview of changes

InlineFixtureProcessor (BaseRefactoringProcessor)
├── findUsages() → finds all test/fixture functions consuming this fixture
├── performRefactoring() → executes the inlining
│   ├── Copies fixture body into consumer(s)
│   ├── Promotes fixture dependencies to consumer signature
│   ├── Replaces fixture-name references with returned value
│   ├── Removes fixture parameter from consumer signature
│   ├── Optionally deletes fixture definition
│   └── Handles imports
└── getCommandName() → "Inline Fixture"
```

### 3.2 Key Classes and Responsibilities

#### `InlineFixtureRefactoringAction`

- Extends `PyBaseRefactoringAction` to appear in "Refactor This" menu
- Availability check:
    - Caret is on a fixture parameter name in a function signature, OR
    - Caret is on a fixture function definition (decorated with `@pytest.fixture`)
- Gated behind `PluginSettingsState.enableInlinePytestFixtureRefactoring`

```kotlin
class InlineFixtureRefactoringAction : PyBaseRefactoringAction() {
    override fun isAvailableInEditorOnly() = true

    override fun isEnabledOnElementInsideEditor(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (!PluginSettingsState.instance().state.enableInlinePytestFixtureRefactoring) return false
        if (file !is PyFile) return false

        // Option 1: Caret is on a fixture parameter in a function signature
        val param = PsiTreeUtil.getParentOfType(element, PyNamedParameter::class.java)
        if (param != null && isFixtureParameter(param)) return true

        // Option 2: Caret is on a fixture function definition
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (function != null && isFixtureFunction(function)) return true

        return false
    }

    override fun getHandler(dataContext: DataContext) = InlineFixtureRefactoringHandler()
}
```

#### `InlineFixtureAnalyzer`

- Resolves the target fixture from the caret position
- Finds all usages (consumers) of the fixture
- Analyzes the fixture body:
    - Identifies dependencies (fixture parameters of the fixture being inlined)
    - Determines the return/yield value and variable
    - Detects setup/teardown split for yield fixtures

```kotlin
class InlineFixtureAnalyzer(
    private val project: Project,
    private val file: PyFile
) {
    fun analyze(element: PsiElement): InlineFixtureModel? {
        val fixtureFunction = resolveFixtureFunction(element) ?: return null
        val fixtureName = fixtureFunction.name ?: return null

        val body = fixtureFunction.statementList.statements.toList()
        val dependencies = fixtureFunction.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .map { it.name!! }

        val returnInfo = analyzeReturnValue(body)
        val usages = findFixtureUsages(fixtureName, fixtureFunction)

        return InlineFixtureModel(
            fixtureFunction = fixtureFunction,
            fixtureName = fixtureName,
            body = body,
            dependencies = dependencies,
            returnInfo = returnInfo,
            usages = usages,
            isYieldFixture = containsYield(body)
        )
    }

    private fun findFixtureUsages(
        fixtureName: String,
        fixtureFunction: PyFunction
    ): List<FixtureUsage> {
        // Search all test/fixture functions in the scope that have this fixture as a parameter
        // Include same file and files in the same directory / subdirectories
    }
}
```

#### `InlineFixtureModel`

```kotlin
data class InlineFixtureModel(
    val fixtureFunction: PyFunction,
    val fixtureName: String,
    val body: List<PyStatement>,
    val dependencies: List<String>,
    val returnInfo: ReturnInfo,
    val usages: List<FixtureUsage>,
    val isYieldFixture: Boolean
)

data class ReturnInfo(
    val returnedExpression: String?,    // e.g. "u" or "create_db()"
    val returnStatement: PyStatement?,  // the return/yield statement itself
    val statementsBeforeReturn: List<PyStatement>,
    val statementsAfterYield: List<PyStatement>  // teardown for yield fixtures
)

data class FixtureUsage(
    val function: PyFunction,          // the consuming function
    val parameter: PyNamedParameter,   // the parameter referencing the fixture
    val file: PyFile                   // file containing the usage
)

enum class InlineMode {
    INLINE_ALL_AND_REMOVE,  // Inline into all usages, delete fixture
    INLINE_THIS_ONLY        // Inline into current usage, keep fixture
}

data class InlineFixtureOptions(
    val inlineMode: InlineMode = InlineMode.INLINE_ALL_AND_REMOVE
)
```

#### `InlineFixtureDialog`

- Simple dialog (similar to IntelliJ's built-in Inline dialogs)
- Shows fixture name and number of usages
- Options:
    - **Inline all usages and remove the fixture** (default when single usage)
    - **Inline this usage only** (keep fixture for other consumers)
- Invoked from caret on a parameter: default to "Inline this only"
- Invoked from fixture definition: default to "Inline all"

#### `InlineFixtureProcessor`

- Executes the inlining within a `WriteCommandAction` for undo support
- For each target usage:
    1. Copy fixture body statements (excluding return/yield) into the consumer, at the top of the function body
    2. Remove the fixture parameter from the consumer's signature
    3. Add fixture dependencies to the consumer's signature (avoiding duplicates)
    4. Replace all references to the fixture name in the consumer body with the returned expression/variable
    5. Handle imports if the fixture was in a different file
- If `INLINE_ALL_AND_REMOVE`: delete the fixture definition after processing all usages
- Reformat affected code via `CodeStyleManager`

---

## 4) Detailed Implementation Steps

### Phase 1: Core Infrastructure

1. **Create `InlineFixtureModel`** - Data classes for analysis results
    - `InlineFixtureModel`, `ReturnInfo`, `FixtureUsage`, `InlineMode`, `InlineFixtureOptions`

2. **Create `InlineFixtureAnalyzer`** - Fixture and usage analysis
    - Resolve fixture from caret position (parameter reference or definition)
    - Find all consuming functions across the file/project scope
    - Analyze return/yield value
    - Detect fixture dependencies

3. **Create `InlineFixtureOptions`** - User choices container

### Phase 2: Dialog and UI

4. **Create `InlineFixtureDialog`** using Kotlin UI DSL
    - Radio buttons for inline mode (all vs. this only)
    - Display fixture name and usage count
    - Disable "inline all" when invoked from parameter and fixture has multiple usages (optional)

### Phase 3: Refactoring Processor

5. **Create `InlineFixtureProcessor`**
    - Inline body into consumer: insert statements before existing body
    - Dependency promotion: merge fixture params into consumer signature
    - Return value substitution: replace fixture-name references with returned variable/expression
    - Handle yield fixtures: only inline setup portion, warn about teardown loss
    - Import management: add necessary imports when inlining from conftest.py

6. **Handle special cases**
    - `yield` fixtures (setup/teardown pattern) — inline setup, warn about teardown
    - `async` fixtures — preserve `await` expressions
    - Fixtures with `autouse=True` — warn that inlining removes auto-injection
    - Fixtures referenced via `@pytest.mark.usefixtures` — update decorator
    - Fixtures with `scope` != `function` — warn about scope semantics change
    - Parametrized fixtures (`@pytest.fixture(params=...)`) — refuse or warn

### Phase 4: Integration

7. **Register action in plugin.xml**
   ```xml
   <action id="com.github.chbndrhnns.betterpy.features.pytest.inlinefixture.InlineFixtureRefactoringAction"
           class="com.github.chbndrhnns.betterpy.features.pytest.inlinefixture.InlineFixtureRefactoringAction"
           text="BetterPy: Inline Pytest Fixture..."
           description="Inlines a pytest fixture body into its consumer(s)">
       <add-to-group group-id="RefactoringMenu" anchor="last"/>
   </action>
   ```

8. **Feature toggle integration**
    - Add `enableInlinePytestFixtureRefactoring` to `PluginSettingsState`
    - Gate availability behind feature flag

9. **Create feature definition**
    - `src/main/resources/features/inline-pytest-fixture.yaml`

### Phase 5: Testing

10. **Unit tests for analyzer**
    - Fixture resolution from parameter and from definition
    - Usage detection across same file
    - Return value analysis (simple return, variable return, yield)

11. **Integration tests for processor**
    - Basic inlining scenarios
    - Multi-dependency promotion
    - Yield fixture handling
    - Conftest fixture inlining
    - Class-based test inlining

---

## 5) Test Cases

### 5.1 Action Availability

#### Test: Action available on fixture parameter in test function

```python
import pytest


@pytest.fixture
def db():
    return "db"


def test_save(< caret > db):
    assert db is not None
```

→ Action **should be available**

#### Test: Action available on fixture definition

```python
import pytest


@pytest.fixture
def <caret > db():


return "db"


def test_save(db):
    assert db is not None
```

→ Action **should be available**

#### Test: Action NOT available when feature disabled

Same code, but `enableInlinePytestFixtureRefactoring = false` → Action **should NOT be available**

#### Test: Action NOT available on regular parameter (non-fixture)

```python
def helper(x):
    return < caret > x + 1
```

→ Action **should NOT be available**

#### Test: Action NOT available on regular function (non-fixture)

```python
def <caret > regular_function():


return 1
```

→ Action **should NOT be available**

### 5.2 Basic Inlining

#### Test: Inline simple fixture with return value

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


def test_query(db):
    assert db is not None
```

```python
# After
import pytest


def test_query():
    db = create_db()
    assert db is not None
```

#### Test: Inline fixture with multi-line body

```python
# Before
import pytest


@pytest.fixture
def user():
    u = User(name="test")
    u.activate()
    return u


def test_user_name(user):
    assert user.name == "test"
```

```python
# After
import pytest


def test_user_name():
    user = User(name="test")
    user.activate()
    assert user.name == "test"
```

Note: The `return u` is removed, and all references to `user` (the fixture name) in the test body remain as-is because
`u` is renamed to `user` (the parameter name) when inlining. Alternatively, a local variable `user = u` is introduced
before the assertion.

### 5.3 Dependency Promotion

#### Test: Inline fixture that depends on another fixture

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


@pytest.fixture
def user(db):
    u = User(name="test")
    db.save(u)
    return u


def test_user_name(user):
    assert user.name == "test"
```

```python
# After
import pytest


@pytest.fixture
def db():
    return create_db()


def test_user_name(db):
    user = User(name="test")
    db.save(user)
    assert user.name == "test"
```

What happened:

- `user` parameter removed from `test_user_name`
- `db` (dependency of `user` fixture) added to `test_user_name` signature
- Fixture body inlined, `return u` replaced with `user = u` assignment (or `u` renamed to `user`)

#### Test: Inline fixture — no duplicate dependencies

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


@pytest.fixture
def saved_item(db):
    item = Item()
    db.save(item)
    return item


def test_item(db, saved_item):
    result = db.get(saved_item.id)
    assert result is not None
```

```python
# After
import pytest


@pytest.fixture
def db():
    return create_db()


def test_item(db):
    saved_item = Item()
    db.save(saved_item)
    result = db.get(saved_item.id)
    assert result is not None
```

`db` was already in the test signature, so it is not added again.

#### Test: Inline fixture with multiple dependencies

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


@pytest.fixture
def cache():
    return create_cache()


@pytest.fixture
def service(db, cache):
    return Service(db, cache)


def test_service(service):
    assert service.is_ready()
```

```python
# After
import pytest


@pytest.fixture
def db():
    return create_db()


@pytest.fixture
def cache():
    return create_cache()


def test_service(db, cache):
    service = Service(db, cache)
    assert service.is_ready()
```

### 5.4 Yield Fixture Handling

#### Test: Inline yield fixture — setup only

```python
# Before
import pytest


@pytest.fixture
def db():
    conn = create_connection()
    yield conn
    conn.close()


def test_query(db):
    assert db.query("SELECT 1") is not None
```

```python
# After (setup portion only, teardown is lost — user is warned)
import pytest


def test_query():
    db = create_connection()
    assert db.query("SELECT 1") is not None
```

> **Note:** The teardown (`conn.close()`) cannot be safely inlined into a test function. The dialog should warn the user
> that teardown code will be lost.

### 5.5 Inline Modes

#### Test: Inline all usages and remove fixture

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


def test_one(db):
    assert db is not None


def test_two(db):
    assert db.query("SELECT 1")
```

```python
# After (InlineMode.INLINE_ALL_AND_REMOVE)
import pytest


def test_one():
    db = create_db()
    assert db is not None


def test_two():
    db = create_db()
    assert db.query("SELECT 1")
```

Fixture definition is removed.

#### Test: Inline this usage only (keep fixture)

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


def test_one(db):  # <-- caret here, inline this only
    assert db is not None


def test_two(db):
    assert db.query("SELECT 1")
```

```python
# After (InlineMode.INLINE_THIS_ONLY)
import pytest


@pytest.fixture
def db():
    return create_db()


def test_one():
    db = create_db()
    assert db is not None


def test_two(db):
    assert db.query("SELECT 1")
```

Fixture definition is kept.

### 5.6 Class-Based Tests

#### Test: Inline fixture in class-based test

```python
# Before
import pytest


@pytest.fixture
def db():
    return create_db()


class TestOperations:
    def test_save(self, db):
        assert db is not None
```

```python
# After
import pytest


class TestOperations:
    def test_save(self):
        db = create_db()
        assert db is not None
```

`self` parameter is preserved, `db` is removed and inlined.

### 5.7 Conftest Fixture Inlining

#### Test: Inline fixture from conftest.py

```python
# conftest.py — Before
import pytest


@pytest.fixture
def db():
    return create_db()


@pytest.fixture
def cache():
    return create_cache()


# test_module.py — Before
def test_query(db):
    assert db is not None
```

```python
# conftest.py — After (fixture removed since single usage)
import pytest


@pytest.fixture
def cache():
    return create_cache()


# test_module.py — After
def test_query():
    db = create_db()
    assert db is not None
```

### 5.8 Usefixtures Decorator

#### Test: Inline fixture referenced via usefixtures

```python
# Before
import pytest


@pytest.fixture
def setup_db():
    create_db()


@pytest.mark.usefixtures("setup_db")
def test_query():
    assert True
```

```python
# After
import pytest


def test_query():
    create_db()
    assert True
```

The `@pytest.mark.usefixtures("setup_db")` decorator is removed and the fixture body is inlined.

### 5.9 Edge Cases

#### Test: Fixture with no return value (side-effect only)

```python
# Before
import pytest


@pytest.fixture
def setup_env():
    os.environ["KEY"] = "value"


def test_env(setup_env):
    assert os.environ["KEY"] == "value"
```

```python
# After
import pytest


def test_env():
    os.environ["KEY"] = "value"
    assert os.environ["KEY"] == "value"
```

#### Test: Refuse to inline parametrized fixture

```python
import pytest


@pytest.fixture(params=["a", "b", "c"])
def <caret > letter(request):


return request.param


def test_letter(letter):
    assert letter in ["a", "b", "c"]
```

→ Action **should show an error message**: "Cannot inline parametrized fixture"

#### Test: Warn on inlining fixture with non-function scope

```python
import pytest


@pytest.fixture(scope="session")
def <caret > db():


return create_db()


def test_one(db):
    assert db is not None


def test_two(db):
    assert db is not None
```

→ Dialog should warn: "Fixture has session scope — inlining will change semantics (each test will get its own
instance)"

---

## 6) Edge Cases and Considerations

1. **Name collisions:** When inlining, the returned variable name might conflict with existing local variables in the
   consumer. Detect and rename if needed.
2. **Parametrized fixtures:** Fixtures with `params=` cannot be meaningfully inlined. Refuse with an error.
3. **Scope semantics:** Inlining a `session`/`module`/`class` scoped fixture changes test semantics (shared instance →
   per-test instance). Warn the user.
4. **Yield teardown loss:** When inlining a yield fixture, the teardown code after `yield` is lost. Warn the user.
5. **Autouse fixtures:** Inlining an `autouse=True` fixture into one test doesn't help other tests that implicitly use
   it. Refuse or handle carefully.
6. **Circular dependencies:** After inlining, verify no circular fixture dependencies are introduced.
7. **Fixture used in conftest and tests:** If a fixture is used by both other fixtures and tests, "inline all" must
   handle both kinds of consumers.
8. **Multiple return statements:** Fixtures with conditional returns or multiple return paths cannot be trivially
   inlined. Refuse or warn.
9. **Async fixtures:** Preserve `async`/`await` when inlining async fixtures into async tests.

---

## 7) Dependencies on Existing Code

### From This Plugin

- `PluginSettingsState` — Feature flag management
- Extract fixture classes — Reuse `FixtureParameter` model, fixture detection utilities
- `PytestFixtureUtil` / `PytestFixtureResolver` — Fixture resolution

### From IntelliJ/PyCharm Platform

- `PyBaseRefactoringAction` — Base class for Python refactorings
- `BaseRefactoringProcessor` — Undo/redo support
- `PyElementGenerator` — PSI element creation
- `PsiTreeUtil` — PSI navigation
- `CodeStyleManager` — Code formatting
- `ReferencesSearch` / `PyRefactoringUtil` — Finding usages

---

## 8) Future Enhancements

1. **Inline from conftest.py with import handling:** Automatically add imports for symbols used in the fixture body that
   are imported in conftest.py but not in the target test file.
2. **Partial inline:** Allow inlining only the setup portion of a yield fixture, automatically wrapping teardown in a
   `try/finally` or `addCleanup`.
3. **Smart rename on inline:** When the returned variable name differs from the fixture parameter name, offer to rename
   all references.
4. **Inline chain:** Recursively inline a fixture and all its dependencies in one step.

---

## 9) Related Issues

- [PY-66243](https://youtrack.jetbrains.com/issue/PY-66243/Refactor-Inline-for-pytest-fixtures) — Inline for pytest
  fixtures
- Extract Fixture spec: `spec/pytest/extract-fixture.md` (inverse operation)

---

## 10) File Structure

```
src/main/kotlin/com/github/chbndrhnns/betterpy/features/pytest/inlinefixture/
├── InlineFixtureRefactoringAction.kt       # Action class
├── InlineFixtureRefactoringHandler.kt      # Handler
├── InlineFixtureAnalyzer.kt                # Fixture and usage analysis
├── InlineFixtureDialog.kt                  # UI dialog
├── InlineFixtureProcessor.kt              # Refactoring processor
└── InlineFixtureModel.kt                  # Data models

src/main/resources/
├── features/inline-pytest-fixture.yaml     # Feature definition
└── intentionDescriptions/InlineFixtureRefactoringAction/
    ├── description.html
    ├── before.py.template
    └── after.py.template

src/test/kotlin/com/github/chbndrhnns/betterpy/features/pytest/inlinefixture/
├── InlineFixtureRefactoringTest.kt         # Action availability + integration tests
└── InlineFixtureAnalyzerTest.kt            # Analyzer unit tests
```
