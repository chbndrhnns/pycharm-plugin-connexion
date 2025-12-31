### Plan for "Wrap Pytest Test Function in Class" Refactoring

Based on my analysis of the IntelliJ/PyCharm codebase, here's a comprehensive plan for implementing a plugin that wraps a module-level pytest test function inside a test class.

---

### What Can Be Reused

#### 1. **Test Detection Logic**
**File:** `/python/python-psi-impl/src/com/jetbrains/python/testing/PythonUnitTestDetectors.kt`

- `isTestFunction(PyFunction)` - Detects if a function is a test (starts with "test")
- `isTestClass(PyClass, TypeEvalContext)` - Validates test class structure (name starts/ends with "Test")
- These utilities ensure you only offer the refactoring on valid pytest test functions

#### 2. **Class Refactoring Utilities**
**File:** `/python/python-psi-impl/src/com/jetbrains/python/refactoring/classes/PyClassRefactoringUtil.java`

Key reusable methods:
- `addMethods(PyClass destination, boolean skipIfExist, PyFunction... methods)` - Adds methods to a class
- `insertMethodInProperPlace(PyStatementList destStatementList, PyFunction method)` - Inserts methods in correct position
- `restoreNamedReferences(PsiElement element)` - Fixes references after moving code
- `rememberNamedReferences(PsiElement element, String... namesToSkip)` - Tracks references before moving
- `optimizeImports(PsiFile file)` - Cleans up imports after refactoring

#### 3. **Test Creation Infrastructure**
**File:** `/python/src/com/jetbrains/python/codeInsight/testIntegration/PyTestCreator.java`

- `generateTest(PsiElement anchor, PyTestCreationModel model)` - Creates test classes and methods
- Shows how to generate pytest-style classes (plain class vs unittest.TestCase)
- Demonstrates proper formatting and code style application

#### 4. **PSI Manipulation Utilities**
**File:** `/python/python-psi-impl/src/com/jetbrains/python/refactoring/PyRefactoringUtil.java`

- `selectUniqueName(String templateName, PsiElement scopeAnchor)` - Generates unique class names
- `isValidNewName(String name, PsiElement scopeAnchor)` - Validates names

#### 5. **Refactoring Pattern**
**File:** `/python/python-psi-impl/src/com/jetbrains/python/refactoring/introduce/IntroduceHandler.java`

- General refactoring handler pattern
- Shows how to handle user interaction, validation, and execution
- Demonstrates inplace refactoring with templates

---

### Implementation Plan

#### **Phase 1: Core Refactoring Action**

##### 1.1 Create the Refactoring Handler
```java
public class WrapTestInClassHandler extends RefactoringActionHandler {
  @Override
  public void invoke(@NotNull Project project, Editor editor, 
                     PsiFile file, DataContext dataContext) {
    // 1. Get the test function at caret
    // 2. Validate it's a module-level test function
    // 3. Show dialog to get class name (or use smart default)
    // 4. Perform the wrapping
  }
}
```

**Key steps:**
1. Use `PythonUnitTestDetectors.isTestFunction()` to validate
2. Check function is at module level (not already in a class)
3. Generate default class name: `Test` + capitalize(function_name without "test_" prefix)
4. Create or find existing test class in same file

##### 1.2 Create the Refactoring Operation
```java
public class WrapTestInClassOperation {
  private final PyFunction testFunction;
  private final String targetClassName;
  private final boolean createNewClass;
  private final PyClass existingClass; // if wrapping into existing class
  
  // Execution logic
  public void execute() {
    // 1. Remember references
    // 2. Create/find target class
    // 3. Convert function to method (add self parameter)
    // 4. Move to class
    // 5. Update references
    // 6. Optimize imports
  }
}
```

##### 1.3 Function to Method Conversion
Key transformations needed:
- Add `self` as first parameter
- Update any fixture references (pytest fixtures work the same in classes)
- Handle decorators (e.g., `@pytest.mark.parametrize`)
- Preserve docstrings and comments

#### **Phase 2: User Interface**

##### 2.1 Intention Action
Create an intention that appears when cursor is on a test function:
```java
public class WrapTestInClassIntention extends PyBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, 
                             PsiFile file) {
    PyFunction function = findFunctionAtCaret(editor, file);
    return function != null 
        && isTestFunction(function)
        && !isInsideClass(function);
  }
  
  @Override
  public void doInvoke(@NotNull Project project, Editor editor, 
                       PyFile file) {
    // Launch refactoring
  }
}
```

##### 2.2 Dialog for Class Selection
```java
public class WrapTestInClassDialog extends RefactoringDialog {
  // Options:
  // - Create new class (with name field)
  // - Add to existing class (dropdown of test classes in file)
  // - Preview changes
}
```

##### 2.3 Quick Fix (Optional)
If you want to offer this as a quick fix for an inspection:
```java
public class ModuleLevelTestInspection extends PyInspection {
  // Suggests wrapping module-level tests in classes
}
```

#### **Phase 3: Smart Features**

##### 3.1 Class Name Suggestions
```java
private String suggestClassName(PyFunction testFunction) {
  String funcName = testFunction.getName();
  if (funcName.startsWith("test_")) {
    // test_user_login -> TestUserLogin
    String baseName = funcName.substring(5);
    return "Test" + CaseFormat.LOWER_UNDERSCORE
                              .to(CaseFormat.UPPER_CAMEL, baseName);
  }
  return "TestClass";
}
```

##### 3.2 Find Existing Test Classes
```java
private List<PyClass> findTestClassesInFile(PyFile file) {
  return file.getTopLevelClasses().stream()
    .filter(cls -> isTestClass(cls, TypeEvalContext.codeAnalysis(...)))
    .collect(Collectors.toList());
}
```

##### 3.3 Batch Operation
Allow wrapping multiple test functions at once:
- Select multiple functions
- Offer to wrap all in same class or separate classes

---

### Testing Strategy

#### **Test Structure**
Follow the pattern from `PyMoveTest.java`:

```java
public class WrapTestInClassTest extends PyTestCase {
  
  // Basic wrapping
  public void testWrapSimpleTestFunction() {
    doTest("TestSimple");
  }
  
  // With fixtures
  public void testWrapTestWithFixtures() {
    doTest("TestWithFixtures");
  }
  
  // With decorators
  public void testWrapParametrizedTest() {
    doTest("TestParametrized");
  }
  
  // Into existing class
  public void testWrapIntoExistingClass() {
    doTestWithExistingClass("TestExisting");
  }
  
  // Multiple functions
  public void testWrapMultipleFunctions() {
    doTestBatch("TestBatch");
  }
  
  private void doTest(String className) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    
    // Find test function
    PyFunction function = findTestFunction();
    
    // Execute refactoring
    new WrapTestInClassOperation(function, className, true, null)
        .execute();
    
    // Verify result
    myFixture.checkResultByFile(testName + ".after.py");
  }
}
```

#### **Test Data Examples**

**testWrapSimpleTestFunction.py:**
```python
def test_user_login():
    assert True
```

**testWrapSimpleTestFunction.after.py:**
```python
class TestUserLogin:
    def test_user_login(self):
        assert True
```

**testWrapTestWithFixtures.py:**
```python
def test_database_connection(db_session):
    assert db_session.is_connected()
```

**testWrapTestWithFixtures.after.py:**
```python
class TestDatabaseConnection:
    def test_database_connection(self, db_session):
        assert db_session.is_connected()
```

**testWrapParametrizedTest.py:**
```python
import pytest

@pytest.mark.parametrize("input,expected", [(1, 2), (2, 3)])
def test_increment(input, expected):
    assert input + 1 == expected
```

**testWrapParametrizedTest.after.py:**
```python
import pytest

class TestIncrement:
    @pytest.mark.parametrize("input,expected", [(1, 2), (2, 3)])
    def test_increment(self, input, expected):
        assert input + 1 == expected
```

#### **Test Categories**

1. **Basic Wrapping**
   - Simple test function
   - Test with parameters
   - Test with return value
   - Test with docstring

2. **Pytest Features**
   - With fixtures (function and class scope)
   - With parametrize decorator
   - With marks (skip, xfail, etc.)
   - With multiple decorators

3. **Code Complexity**
   - Test with local variables
   - Test with nested functions
   - Test with imports used only in that test
   - Test with global variable references

4. **Target Class Scenarios**
   - Create new class
   - Add to existing empty test class
   - Add to existing class with other tests
   - Handle name conflicts

5. **Edge Cases**
   - Test function with same name as existing method
   - Multiple test functions with related names
   - Test in file with existing test classes
   - Test with type hints

6. **Reference Updates**
   - Other tests calling this test (if any)
   - References in comments
   - References in docstrings

---

### Plugin Structure

```
com.yourcompany.pytest.refactoring/
├── actions/
│   ├── WrapTestInClassAction.java
│   └── WrapTestInClassIntention.java
├── refactoring/
│   ├── WrapTestInClassHandler.java
│   ├── WrapTestInClassOperation.java
│   └── WrapTestInClassDialog.java
├── utils/
│   ├── PytestRefactoringUtil.java
│   └── TestClassNameGenerator.java
└── resources/
    └── META-INF/
        └── plugin.xml
```

**plugin.xml:**
```xml
<idea-plugin>
  <id>com.yourcompany.pytest-refactoring</id>
  <name>Pytest Refactoring Tools</name>
  
  <depends>com.intellij.modules.python</depends>
  
  <extensions defaultExtensionNs="com.intellij">
    <intentionAction>
      <className>com.yourcompany.pytest.refactoring.actions.WrapTestInClassIntention</className>
      <category>Python/Pytest</category>
    </intentionAction>
  </extensions>
  
  <actions>
    <action id="WrapTestInClass" 
            class="com.yourcompany.pytest.refactoring.actions.WrapTestInClassAction"
            text="Wrap Test in Class"
            description="Wrap a module-level test function in a test class">
      <add-to-group group-id="RefactoringMenu" anchor="last"/>
    </action>
  </actions>
</idea-plugin>
```

---

### Implementation Checklist

#### **Core Functionality**
- [ ] Detect module-level test functions
- [ ] Validate refactoring availability
- [ ] Generate smart class name suggestions
- [ ] Create new test class with proper structure
- [ ] Convert function to method (add `self` parameter)
- [ ] Preserve decorators and docstrings
- [ ] Handle pytest fixtures correctly
- [ ] Update all references
- [ ] Optimize imports

#### **User Interface**
- [ ] Intention action on test functions
- [ ] Refactoring menu action
- [ ] Dialog for class name/selection
- [ ] Preview changes
- [ ] Undo support

#### **Testing**
- [ ] Unit tests for all scenarios
- [ ] Integration tests with real pytest projects
- [ ] Performance tests for large files
- [ ] Edge case handling

#### **Documentation**
- [ ] User guide
- [ ] API documentation
- [ ] Example use cases
- [ ] Known limitations

---

### Key Considerations

1. **Pytest vs Unittest**: The refactoring should create plain classes for pytest (not inheriting from TestCase), unless the project uses unittest-style tests.

2. **Fixture Handling**: Pytest fixtures work the same in classes and functions, so no special handling needed beyond adding `self`.

3. **Class Scope**: Consider offering to convert function-scoped fixtures to class-scoped when wrapping.

4. **Naming Conventions**: Follow pytest conventions (class name starts or ends with "Test").

5. **Backward Compatibility**: Ensure the refactoring doesn't break existing test runs.

6. **Public API**: Use only public PyCharm APIs (avoid `@ApiStatus.Internal` as per your requirements).

---

### Next Steps

1. **Prototype**: Start with a simple intention that wraps a basic test function
2. **Iterate**: Add support for fixtures, decorators, and complex scenarios
3. **Test**: Create comprehensive test suite
4. **Polish**: Add UI, preview, and smart suggestions
5. **Document**: Write user guide and examples
6. **Release**: Publish as PyCharm plugin