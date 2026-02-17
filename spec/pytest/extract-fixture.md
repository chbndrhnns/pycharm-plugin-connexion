# Extract Fixture Refactoring

This document describes the implementation plan for making the "Extract Method" refactoring work seamlessly with pytest
fixtures.

---

## 1) Problem Statement

PyCharm's built-in **Extract Method** refactoring (Ctrl+Alt+M) doesn't understand pytest fixtures. When extracting code
from a test function or fixture that uses fixture parameters:

1. The extracted function doesn't receive the `@pytest.fixture` decorator when appropriate
2. Fixture parameters used in the extracted code are not properly passed through
3. The extracted code doesn't integrate into pytest's fixture resolution system
4. No option to create the extracted code as a new fixture vs. a helper function

### Example: Current Behavior (Problem)

```python
import pytest


@pytest.fixture
def db_connection():
    return create_db()


def test_user_creation(db_connection):
    # User wants to extract this block
    user = User(name="test")
    db_connection.save(user)  # <-- uses fixture
    result = db_connection.get(user.id)
    # end of block to extract
    assert result.name == "test"
```

**Current result:** Plain function without fixture awareness:

```python
def extracted():  # Missing fixture parameter!
    user = User(name="test")
    db_connection.save(user)  # Broken reference
    result = db_connection.get(user.id)
    return result
```

### Desired Behavior

**Option A: Extract as helper function (pass fixture explicitly)**

```python
def create_and_fetch_user(db_connection):
    user = User(name="test")
    db_connection.save(user)
    result = db_connection.get(user.id)
    return result


def test_user_creation(db_connection):
    result = create_and_fetch_user(db_connection)
    assert result.name == "test"
```

**Option B: Extract as new fixture**

```python
@pytest.fixture
def created_user(db_connection):
    user = User(name="test")
    db_connection.save(user)
    return db_connection.get(user.id)


def test_user_creation(created_user):
    assert created_user.name == "test"
```

---

## 2) Goals

1. **Fixture-aware extraction:** Detect when extracted code uses fixture parameters and handle them correctly
2. **Extract as fixture option:** Allow users to create a new pytest fixture from selected code
3. **Fixture dependency chain:** Properly establish fixture dependencies in extracted fixtures
4. **Scope inference:** Suggest appropriate fixture scope based on context
5. **Placement options:** Extract to same file, `conftest.py`, or specified module

---

## 3) Implementation Approach

### 3.1 Architecture Overview

```
ExtractFixtureRefactoringAction (PyBaseRefactoringAction)
├── isAvailableOnElementInEditorAndFile() → checks if in test/fixture context
└── getHandler() → ExtractFixtureRefactoringHandler

ExtractFixtureRefactoringHandler (RefactoringActionHandler)
├── invoke() → analyzes selection, shows dialog
└── ExtractFixtureDialog (UI) → collects user choices
    ├── Name input
    ├── Extract as: [Helper function / Fixture]
    ├── Target location: [Same file / conftest.py / Other]
    ├── Fixture scope: [function / class / module / session]
    └── Parameters to include

ExtractFixtureProcessor (BaseRefactoringProcessor)
├── findUsages() → identifies affected call sites
├── performRefactoring() → executes the extraction
│   ├── Creates new function/fixture
│   ├── Adds @pytest.fixture decorator (if fixture mode)
│   ├── Updates original code to use extracted entity
│   └── Handles imports
└── getCommandName() → "Extract Fixture"
```

### 3.2 Key Classes and Responsibilities

#### `ExtractFixtureAction` / `ExtractFixtureRefactoringAction`

- Extends `PyBaseRefactoringAction` to appear in "Refactor This" menu
- Availability check:
    - Selection is inside a pytest test function or fixture
    - Selection contains statements that can be extracted
    - At least one fixture parameter is used in selection OR user explicitly wants a fixture

```kotlin
class ExtractFixtureRefactoringAction : PyBaseRefactoringAction() {
    override fun isAvailableInEditorOnly() = true

    override fun isAvailableOnElementInEditorAndFile(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        if (file !is PyFile) return false
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return false
        return isTestOrFixture(function) && hasSelection(editor)
    }

    override fun getHandler(dataContext: DataContext) = ExtractFixtureRefactoringHandler()
}
```

#### `ExtractFixtureRefactoringHandler`

- Analyzes the selection to determine:
    - What fixture parameters are used
    - What local variables are used/defined
    - What the return value should be
- Opens the dialog for user configuration
- Delegates to processor

```kotlin
class ExtractFixtureRefactoringHandler : RefactoringActionHandler {
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return

        val analyzer = ExtractFixtureAnalyzer(file as PyFile, selectionModel)
        val model = analyzer.analyze() ?: return

        val dialog = ExtractFixtureDialog(project, model)
        if (!dialog.showAndGet()) return

        ExtractFixtureProcessor(project, model, dialog.result).run()
    }
}
```

#### `ExtractFixtureAnalyzer`

- Core analysis component
- Identifies:
    - Statements in selection
    - Referenced fixture parameters (via `PytestFixtureResolver`)
    - Local variable reads/writes
    - Possible return values

```kotlin
class ExtractFixtureAnalyzer(
    private val file: PyFile,
    private val selection: SelectionModel
) {
    fun analyze(): ExtractFixtureModel? {
        val statements = findSelectedStatements()
        if (statements.isEmpty()) return null

        val containingFunction = statements.first().parentOfType<PyFunction>() ?: return null
        val usedFixtures = findUsedFixtures(statements, containingFunction)
        val usedLocals = findUsedLocalVariables(statements)
        val definedLocals = findDefinedLocalVariables(statements)
        val outputVariables = findOutputVariables(statements, containingFunction)

        return ExtractFixtureModel(
            containingFunction = containingFunction,
            statements = statements,
            usedFixtures = usedFixtures,
            usedLocals = usedLocals,
            definedLocals = definedLocals,
            outputVariables = outputVariables
        )
    }

    private fun findUsedFixtures(
        statements: List<PyStatement>,
        function: PyFunction
    ): List<FixtureParameter> {
        val fixtureParams = function.parameterList.parameters
            .filterIsInstance<PyNamedParameter>()
            .filter { isFixtureParameter(it) }

        val usedNames = collectReferencedNames(statements)
        return fixtureParams.filter { it.name in usedNames }
            .map { FixtureParameter(it.name!!, resolveFixture(it)) }
    }
}
```

#### `ExtractFixtureDialog`

- Kotlin UI DSL dialog for user configuration
- Options:
    - **Name**: Name of extracted function/fixture
    - **Extraction mode**: Helper function vs. Fixture
    - **Target location**: Same file / conftest.py / Browse...
    - **Fixture scope** (if fixture mode): function / class / module / session
    - **Preview** of the generated code

```kotlin
class ExtractFixtureDialog(
    project: Project,
    private val model: ExtractFixtureModel
) : DialogWrapper(project) {

    var result: ExtractFixtureSettings by Delegates.notNull()

    private var nameField = JBTextField(suggestName())
    private var extractAsFixture = JBCheckBox("Extract as @pytest.fixture")
    private var scopeCombo: ComboBox<String>? = null
    private var targetCombo: ComboBox<TargetLocation>

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") { cell(nameField).focused() }

        row { cell(extractAsFixture) }

        row("Scope:") {
            scopeCombo = comboBox(listOf("function", "class", "module", "session"))
            cell(scopeCombo!!).enabledIf(extractAsFixture.selected)
        }

        row("Target:") {
            targetCombo = comboBox(buildTargetOptions())
            cell(targetCombo)
        }

        row {
            label("Parameters: ${model.usedFixtures.joinToString { it.name }}")
        }
    }

    private fun buildTargetOptions(): List<TargetLocation> {
        val options = mutableListOf(
            TargetLocation.SameFile,
            TargetLocation.NearestConftest(findNearestConftest())
        )
        if (canCreateNewConftest()) {
            options.add(TargetLocation.NewConftest(suggestConftestPath()))
        }
        options.add(TargetLocation.OtherFile)
        return options
    }
}
```

#### `ExtractFixtureProcessor`

- Executes the refactoring
- Uses `WriteCommandAction` for undo support
- Steps:
    1. Generate new function code
    2. Add `@pytest.fixture` decorator if needed
    3. Insert at target location
    4. Replace original statements with call/usage
    5. Update imports

```kotlin
class ExtractFixtureProcessor(
    project: Project,
    private val model: ExtractFixtureModel,
    private val settings: ExtractFixtureSettings
) : BaseRefactoringProcessor(project) {

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        WriteCommandAction.runWriteCommandAction(myProject) {
            val newFunction = generateExtractedFunction()
            val inserted = insertFunction(newFunction, settings.target)
            replaceOriginalCode(inserted)
            optimizeImports()
        }
    }

    private fun generateExtractedFunction(): PyFunction {
        val generator = PyElementGenerator.getInstance(myProject)

        val params = buildParameterList()
        val body = model.statements.joinToString("\n") { it.text }
        val returnStmt = generateReturnStatement()
        val decorator = if (settings.asFixture) "@pytest.fixture" else ""
        val scope = if (settings.asFixture && settings.scope != "function")
            "(scope=\"${settings.scope}\")" else ""

        val code = """
            |$decorator$scope
            |def ${settings.name}($params):
            |    $body
            |    $returnStmt
        """.trimMargin()

        return generator.createFromText(
            LanguageLevel.forElement(model.containingFunction),
            PyFunction::class.java,
            code
        )
    }
}
```

---

## 4) Detailed Implementation Steps

### Phase 1: Core Infrastructure

1. **Create `ExtractFixtureModel`** - Data class holding analysis results
    - `containingFunction: PyFunction`
    - `statements: List<PyStatement>`
    - `usedFixtures: List<FixtureParameter>`
    - `usedLocals: List<LocalVariable>`
    - `outputVariables: List<String>`

2. **Create `ExtractFixtureAnalyzer`** - Selection analysis
    - Leverage existing `PytestFixtureResolver` for fixture detection
    - Use `PyControlFlowAnalyzer` for variable flow analysis
    - Handle edge cases: yield fixtures, async fixtures

3. **Create `ExtractFixtureSettings`** - User choices container
    - Name, extraction mode, target location, scope

### Phase 2: Dialog and UI

4. **Create `ExtractFixtureDialog`** using Kotlin UI DSL
    - Real-time validation (name conflicts, invalid identifiers)
    - Preview panel showing generated code
    - Remember last used settings via `PropertiesComponent`

5. **Implement `TargetLocation` handling**
    - Same file: insert before/after containing function
    - conftest.py: find nearest or create new
    - Other file: file chooser dialog

### Phase 3: Refactoring Processor

6. **Create `ExtractFixtureProcessor`**
    - Code generation using `PyElementGenerator`
    - Proper indentation and formatting
    - Import management for pytest, typing, etc.

7. **Implement replacement logic**
    - For helper function: `result = extracted_func(fixture1, fixture2)`
    - For fixture: replace parameter, remove original code

8. **Handle special cases**
    - `yield` fixtures (setup/teardown pattern)
    - `async` fixtures
    - Fixtures with `autouse=True` context
    - Class-based test fixtures

### Phase 4: Integration

9. **Register action in plugin.xml**
   ```xml
   <action id="ExtractFixtureRefactoring"
           class="...ExtractFixtureRefactoringAction"
           text="Extract Fixture...">
       <add-to-group group-id="RefactoringMenu" anchor="after" relative-to-action="ExtractMethod"/>
   </action>
   ```

10. **Feature toggle integration**
    - Add `enableExtractFixture` to `PluginSettingsState`
    - Gate availability behind feature flag

11. **Create intention descriptions**
    - `intentionDescriptions/ExtractFixtureRefactoringAction/`
    - `description.html`, `before.py.template`, `after.py.template`

### Phase 5: Testing

12. **Unit tests for analyzer**
    - Fixture detection accuracy
    - Variable flow analysis
    - Edge cases (nested functions, closures)

13. **Integration tests for processor**
    - Simple extraction scenarios
    - Multi-fixture extraction
    - Different target locations
    - conftest.py generation

14. **UI tests (optional)**
    - Dialog interaction
    - Keyboard navigation

---

## 5) Extension Points to Use

| Extension Point                                       | Purpose                                         |
|-------------------------------------------------------|-------------------------------------------------|
| `com.intellij.refactoring.refactoringElementListener` | Track elements during refactoring               |
| `com.intellij.lang.refactoringSupport`                | Register Python-specific support                |
| N/A (direct action)                                   | `RefactoringMenu` group for action registration |

---

## 6) Dependencies on Existing Code

### From This Plugin

- `PytestFixtureUtil` - Fixture detection utilities
- `PytestFixtureResolver` - Fixture resolution
- `PytestFixtureFeatureToggle` - Feature flag management
- `PluginSettingsState` - Settings management

### From IntelliJ/PyCharm Platform

- `PyBaseRefactoringAction` - Base class for Python refactorings
- `BaseRefactoringProcessor` - Undo/redo support
- `PyElementGenerator` - PSI element creation
- `PyControlFlowBuilder` - Variable analysis
- `PsiTreeUtil` - PSI navigation
- `CodeStyleManager` - Code formatting

---

## 7) Test Cases

### Basic Extraction

```python
# Before
@pytest.fixture
def db():
    return Database()


def test_save(db):
    # <selection>
    item = Item()
    db.save(item)
    result = db.get(item.id)
    # </selection>
    assert result is not None


# After (as fixture)
@pytest.fixture
def saved_item(db):
    item = Item()
    db.save(item)
    return db.get(item.id)


def test_save(saved_item):
    assert saved_item is not None
```

### Multiple Fixtures Used

```python
# Before
def test_complex(db, cache, logger):
    # <selection>
    logger.info("Starting")
    db.save(data)
    cache.invalidate()
    # </selection>
    assert db.get() is not None


# After (as helper)
def setup_data(db, cache, logger):
    logger.info("Starting")
    db.save(data)
    cache.invalidate()


def test_complex(db, cache, logger):
    setup_data(db, cache, logger)
    assert db.get() is not None
```

### Extract to conftest.py

```python
# test_module.py - Before
def test_user(db):
    # <selection>
    user = User(name="test")
    db.save(user)
    # </selection>
    assert user.id is not None


# conftest.py - After
@pytest.fixture
def created_user(db):
    user = User(name="test")
    db.save(user)
    return user


# test_module.py - After
def test_user(created_user):
    assert created_user.id is not None
```

### Yield Fixture Extraction

```python
# Before
@pytest.fixture
def managed_resource(db):
    # <selection>
    resource = db.acquire()
    yield resource
    resource.release()
    # </selection>


# After (preserves yield pattern)
@pytest.fixture
def extracted_resource(db):
    resource = db.acquire()
    yield resource
    resource.release()
```

### Async Fixture Extraction

```python
# Before
@pytest.fixture
async def async_db():
    return await create_db()


async def test_async(async_db):
    # <selection>
    result = await async_db.query()
    processed = transform(result)
    # </selection>
    assert processed is not None


# After
@pytest.fixture
async def query_result(async_db):
    result = await async_db.query()
    return transform(result)


async def test_async(query_result):
    assert query_result is not None
```

---

## 8) Edge Cases and Considerations

1. **Name conflicts:** Check for existing fixtures with same name in scope
2. **Circular dependencies:** Validate that new fixture doesn't create cycles
3. **Scope compatibility:** Warn if extracted fixture has incompatible scope with dependencies
4. **Multiple return values:** Support tuple unpacking or suggest named tuple
5. **Side effects:** Warn if extraction changes execution order
6. **Parametrized tests:** Handle `@pytest.mark.parametrize` correctly
7. **Class-based fixtures:** Support `self` parameter in class methods

---

## 9) Future Enhancements

1. **Inline Fixture:** Inverse operation - inline a fixture into its usages
2. **Change Fixture Scope:** Refactoring to safely change fixture scope
3. **Convert Helper to Fixture:** Turn existing helper function into a fixture
4. **Fixture Documentation:** Auto-generate docstring for extracted fixture

---

## 10) Related YouTrack Issues

- [PY-66243](https://youtrack.jetbrains.com/issue/PY-66243/Refactor-Inline-for-pytest-fixtures) - Inline for pytest
  fixtures
- [PY-56186](https://youtrack.jetbrains.com/issue/PY-56186/Support-Push-Down-Up-for-pytest-fixtures) - Push Down/Up for
  fixtures

---

## 11) File Structure

```
src/main/kotlin/com/github/chbndrhnns/betterpy/features/pytest/extract/
├── ExtractFixtureRefactoringAction.kt      # Action class
├── ExtractFixtureRefactoringHandler.kt     # Handler
├── ExtractFixtureAnalyzer.kt               # Selection analysis
├── ExtractFixtureDialog.kt                 # UI dialog
├── ExtractFixtureProcessor.kt              # Refactoring processor
├── ExtractFixtureModel.kt                  # Data models
└── ExtractFixtureFeatureToggle.kt          # Feature flag

src/main/resources/
├── intentionDescriptions/ExtractFixtureRefactoringAction/
│   ├── description.html
│   ├── before.py.template
│   └── after.py.template
└── features/extract-pytest-fixture.yaml    # Feature definition

src/test/kotlin/com/github/chbndrhnns/betterpy/features/pytest/extract/
├── ExtractFixtureAnalyzerTest.kt
├── ExtractFixtureProcessorTest.kt
└── ExtractFixtureIntegrationTest.kt
```
