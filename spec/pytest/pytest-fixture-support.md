# Pytest Fixture Support (PyCharm Plugin)

This guide describes how to implement pytest fixture navigation support in an external PyCharm plugin (not in the
IntelliJ Community repo). It focuses on using extension points (EPs) and existing Python PSI APIs to enable:

- Go to Declaration from fixture usages to the correct fixture definition(s).
- Show Implementations (Ctrl+Alt+B) on fixture declarations to navigate to overriding fixtures.

It also includes a set of test cases and test data suggestions.

---

## 1) Concepts You Need

### 1.1 Pytest fixture resolution (mental model)

Pytest resolves fixtures by name, with scoping/overrides. For a given usage `def test_something(my_fixture): ...`,
pytest picks a fixture based on proximity and scope:

1. **Class scope**: fixture methods on the test class or parent classes.
2. **Module scope**: fixtures defined in the same module file.
3. **Imports**: fixtures brought in via `from x import my_fixture` or `from x import *`.
4. **conftest.py**: fixtures in the nearest `conftest.py` walking up the directory tree.
5. **Reserved/builtin**: `_pytest` or built-in fixtures.

A plugin should reflect this precedence for both navigation and “show implementations” behavior.

### 1.2 Navigation expectations

- **Go to Declaration** should jump to the *effective* fixture definition, but also allow choosing from other overridden
  definitions in the chain.
- **Show Implementations** should show all overriding fixtures for a declaration.

### 1.3 Key IntelliJ APIs (relevant EPs)

These are the primary extension points you should rely on from a PyCharm plugin:

- `com.intellij.psi.referenceContributor`
    - Provide references for fixture usages (parameters or `pytest.mark.usefixtures` strings).
- `com.intellij.gotoDeclarationHandler`
    - Override GTD resolution behavior for fixture usage sites if required.
- `com.intellij.definitionsScopedSearch`
    - Provide implementation search results for “Show Implementations”.
- `com.intellij.targetElementEvaluator` (optional)
    - Customize inclusion rules for “Go to Implementations”.

The goal is to keep logic in your plugin, and not depend on modifications to PyCharm core.

---

## 2) Target Feature: Fixture Navigation

### 2.1 Enable references on usage sites

You need references from:

- **Fixture parameters** in test/fixture functions (e.g., `def test_x(my_fixture): ...`).
- **`pytest.mark.usefixtures("...")` string literals**.

Implementation idea:

1. Register a `PsiReferenceContributor` for Python.
2. In your `PsiReferenceProvider`, detect:
    - `PyNamedParameter` elements.
    - `PyStringLiteralExpression` used as `pytest.mark.usefixtures` args.
3. Provide a `PsiReference` that resolves to the fixture definition.

Your reference should be able to return **multiple resolution targets** when there is an override chain.

### 2.2 Resolve the fixture chain (override-aware)

Create a utility method that returns **all candidate fixtures** in pytest precedence order.

Suggested method signature:

```
List<FixtureLink> findFixtureChain(PsiElement usage, TypeEvalContext context)
```

Where `FixtureLink` holds:

- The fixture function element (e.g., `PyFunction`).
- Optional import element (if resolved through an import).
- Fixture name.

Order matters. Use the pytest precedence order described above.

### 2.3 Go To Declaration behavior

From a fixture usage site:

- **Single target**: navigate directly.
- **Multiple targets**: show the popup to choose among the chain.

This can be achieved by:

- Returning multiple targets via your `PsiReference` (`multiResolve`).
- Optionally, implementing `GotoDeclarationHandler` to return an ordered list if default reference resolution is
  insufficient.

### 2.4 Show Implementations behavior

Implement a `DefinitionsScopedSearch` executor that returns overriding fixtures when the caret is on a fixture
definition.

Your searcher should:

- Identify fixture declarations (e.g., decorated with `@pytest.fixture`, `@pytest_asyncio.fixture`).
- Discover fixtures with the same name that override the base fixture according to pytest precedence rules.

---

## 3) Step-by-Step Implementation Plan

### Step 1: Create plugin skeleton

- Use the IntelliJ Plugin SDK.
- Declare dependencies on Python plugin modules (PyCharm bundles them).

Example `plugin.xml` fragment:

```
<idea-plugin>
  <depends>com.intellij.modules.platform</depends>
  <depends>com.intellij.modules.python</depends>

  <extensions defaultExtensionNs="com.intellij">
    <psi.referenceContributor language="Python" implementation="com.mycompany.pytest.PytestFixtureReferenceContributor"/>
    <definitionsScopedSearch implementation="com.mycompany.pytest.PytestFixtureImplementationSearch"/>
    <gotoDeclarationHandler implementation="com.mycompany.pytest.PytestFixtureGotoDeclarationHandler"/>
  </extensions>
</idea-plugin>
```

### Step 2: Build fixture identification helpers

- Recognize fixtures by decorators:
    - `pytest.fixture`
    - `fixture` (imported)
    - `pytest_asyncio.fixture`
- Resolve `name=` argument in `@pytest.fixture(name="..." )`.

### Step 3: Implement fixture chain resolution

Create a utility class that:

1. If usage is a parameter, find the enclosing function.
2. Collect fixture candidates in pytest precedence order:
    - Class fixtures (MRO order).
    - Module fixtures.
    - Imports (including wildcard import).
    - `conftest.py` files walking upward.
    - Built-in fixtures.
3. Return ordered `FixtureLink` objects.

### Step 4: Add references

- Add `PsiReferenceContributor` + `PsiReferenceProvider`.
- The reference’s `resolve()` returns the first fixture in the chain.
- `multiResolve()` returns all fixtures in the chain (plus import target).

### Step 5: Go To Declaration handler (if needed)

If the reference chain is not enough for a GTD popup, implement `GotoDeclarationHandler`:

- Detect fixture usage at caret.
- Return the chain in the desired order.

### Step 6: Show Implementations support

Implement a `DefinitionsScopedSearch` executor:

- Input is a fixture declaration element.
- Find overriding fixtures in descendants or deeper scopes:
    - For class fixtures: subclass fixtures with same name.
    - For module fixtures: class fixtures in the same module, plus fixtures in sub-conftest files.
    - For conftest fixtures: fixtures in `conftest.py` files in descendant directories and test modules under those
      directories.
- Filter out the original fixture.

### Step 7: Tests and test data

Use PyCharm’s test framework to verify navigation results.

---

## 4) Test Cases

Below is a test matrix that covers both “Go to Declaration” and “Show Implementations”.

### 4.1 Go To Declaration (usage)

1. **Simple module fixture**
    - `test_file.py`: `def my_fixture(): ...` and `def test_x(my_fixture): ...`
    - Expect GTD from `my_fixture` usage to `my_fixture` function.

2. **Class override**
    - `base_class.py` defines fixture `my_fixture`.
    - `test_file.py` defines subclass fixture `my_fixture`.
    - Expect chain order: subclass fixture -> base class fixture.

3. **Module vs conftest**
    - `conftest.py` defines `my_fixture`.
    - `test_file.py` defines `my_fixture`.
    - Expect GTD to module fixture; popup shows both.

4. **Nested conftest resolution**
    - root `conftest.py` defines `my_fixture`.
    - `subdir/conftest.py` defines `my_fixture`.
    - `subdir/test_x.py` uses it.
    - Expect chain order: `subdir/conftest.py`, then root `conftest.py`.

5. **usefixtures string**
    - `@pytest.mark.usefixtures("my_fixture")` on a test.
    - Expect GTD from the string to same chain as parameter usage.

6. **Import resolution**
    - `from fixtures import my_fixture`.
    - Usage should resolve to `fixtures.py` fixture definition.

### 4.2 Show Implementations (declaration)

1. **Base class fixture**
    - Caret on fixture in `BaseTest`.
    - Show Implementations should include fixture in `TestDerived`.

2. **Conftest chain**
    - Caret on fixture in root `conftest.py`.
    - Implementations should include `subdir/conftest.py` fixture with same name.

3. **Reserved fixture override**
    - Caret on builtin fixture definition (or synthetic element).
    - Implementations should include project fixture with same name.

### 4.3 Test data layout suggestion

```
pytestFixtureNavigation/
  testSimple/
    test_file.py
  testClassOverride/
    base_class.py
    test_file.py
  testConftestOverride/
    conftest.py
    test_file.py
  testNestedConftest/
    conftest.py
    subdir/
      conftest.py
      test_x.py
  testUsefixtures/
    test_usefixtures.py
  testImport/
    fixtures.py
    test_file.py
```

---

## 5) Notes and Caveats

- PyCharm already includes pytest fixture discovery logic; your plugin should reuse PSI utilities rather than duplicate
  logic where possible.
- Always keep ordering deterministic to match pytest precedence.
- For “Show Implementations”, stick to fixture name + scope rules instead of Python class inheritance rules (fixtures
  are not regular overrides).
- Ensure that fixture names defined via `name=` argument in `@pytest.fixture` are respected.
- When pytest is not the selected test runner in a module, consider gating resolution.

---

## 6) Next Steps

- Prototype the fixture chain utility and validate with test data.
- Add reference contributor and GTD handler.
- Implement definitions searcher for “Show Implementations”.
- Add tests for each case in the matrix.

If you want, I can expand this into a more detailed implementation checklist or a code skeleton for the plugin classes.
