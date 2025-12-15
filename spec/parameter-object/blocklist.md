### Problem analysis
The *availability* check for “Introduce parameter object” is centralized in `IntroduceParameterObjectTarget.isAvailable(element)`. Right now it only:

- resolves the `PyFunction` via `find(element)`
- rejects stubs (`.pyi`)
- checks the number of “real” parameters (≥ 2)

Because there is **no test-specific exclusion**, the intention/refactoring action can appear on:

- pytest test functions/methods (e.g. `def test_x(...): ...`)
- pytest fixtures (e.g. `@pytest.fixture` / `@fixture`)

Both the intention (`PyIntroduceParameterObjectIntention`) and the refactoring action (`IntroduceParameterObjectRefactoringAction`) delegate to `IntroduceParameterObjectTarget.isAvailable`, so fixing it in one place fixes both.

### Proposed solution
Add explicit exclusions to `IntroduceParameterObjectTarget.isAvailable`:

1) **Exclude test functions/methods** using the existing utility `PyTestDetection.isTestFunction`.
2) **Exclude pytest fixtures** by checking decorators for `pytest.fixture` and `fixture`.

Implement the checks *before* the parameter-count logic so we short-circuit quickly.

### Code change (recommended)
Edit:
- `src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/intention/parameterobject/IntroduceParameterObjectTarget.kt`

Add imports:
```kotlin
import com.github.chbndrhnns.intellijplatformplugincopy.search.PyTestDetection
```

Update `isAvailable` and add a small helper:
```kotlin
fun isAvailable(element: PsiElement): Boolean {
    val function = find(element) ?: return false

    if (function.containingFile.name.endsWith(".pyi")) return false

    // Do not offer in pytest tests
    if (PyTestDetection.isTestFunction(function)) return false

    // Do not offer in pytest fixtures
    if (isPytestFixture(function)) return false

    val parameters = function.parameterList.parameters
        .filterIsInstance<PyNamedParameter>()
        .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

    return parameters.size >= 2
}

private fun isPytestFixture(function: PyFunction): Boolean {
    val decoratorList = function.decoratorList ?: return false

    // Works for @pytest.fixture, @pytest.fixture(...), and often for @fixture / @fixture(...)
    if (decoratorList.findDecorator("pytest.fixture") != null) return true
    if (decoratorList.findDecorator("fixture") != null) return true

    // Fallback for cases where `findDecorator` doesn’t match (aliases, formatting, etc.)
    return decoratorList.decorators.any { deco ->
        val t = deco.text
        t.startsWith("@pytest.fixture") || t.startsWith("@fixture")
    }
}
```
Notes:
- Using `PyTestDetection.isTestFunction` matches the project’s current convention (“`test_` prefix”).
- The decorator checks cover the two common fixture styles:
  - `import pytest` + `@pytest.fixture`
  - `from pytest import fixture` + `@fixture`

### Tests to add/update
Add two availability tests to:
- `src/test/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/intention/parameterobject/PyIntroduceParameterObjectIntentionTest.kt`

1) **Unavailable in pytest test function**:
```kotlin
fun testUnavailableInPytestTestFunction() {
    myFixture.configureByText(
        "test_a.py",
        """
        def test_something(<caret>a, b):
            return a + b
        """.trimIndent()
    )

    assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
}
```

2) **Unavailable in pytest fixture**:
```kotlin
fun testUnavailableInPytestFixture() {
    myFixture.configureByText(
        "test_a.py",
        """
        import pytest


        @pytest.fixture
        def user_factory(<caret>first_name, last_name):
            return {"first": first_name, "last": last_name}
        """.trimIndent()
    )

    assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
}
```

If you also want to cover `from pytest import fixture`:
```kotlin
fun testUnavailableInFixtureImportedDirectly() {
    myFixture.configureByText(
        "test_a.py",
        """
        from pytest import fixture


        @fixture
        def user_factory(<caret>first_name, last_name):
            return {"first": first_name, "last": last_name}
        """.trimIndent()
    )

    assertEmpty(myFixture.filterAvailableIntentions("Introduce parameter object"))
}
```

### Why this meets the requirement
- The availability logic is tightened exactly where it’s decided (`IntroduceParameterObjectTarget.isAvailable`).
- Both UI entry points (intention + refactoring action) inherit the fix automatically.
- Tests ensure the intention is not offered for pytest tests and fixtures going forward.

### Optional follow-ups
If you want broader “test method” detection beyond name prefix (e.g., `unittest.TestCase` methods), you could extend `PyTestDetection` (or add a second detector) to use the Python plugin’s test framework APIs. But for this repository’s current conventions, prefix + fixture-decorator checks are consistent and minimal.