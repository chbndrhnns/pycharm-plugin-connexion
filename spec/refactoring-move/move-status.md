### Analysis: What's Currently Missing from the Spec

Based on the comprehensive plan in `spec/refactoring-move/move.md`, here's what has been implemented versus what's missing:

---

### ✅ **What Has Been Implemented**

#### Core Functionality (Partial)
- ✅ **Basic intention action** (`WrapTestInClassIntention.kt`)
  - Detects module-level test functions
  - Validates refactoring availability (not inside class, starts with `test_`)
  - Converts function to method (adds `self` parameter)
  - Preserves decorators (e.g., `@pytest.mark.parametrize`)
  - Preserves type hints (parameter and return type annotations)
  - Generates smart class names (e.g., `test_user_login` → `TestUserLogin`)

#### UI Components
- ✅ **Intention action** registered in `plugin.xml` under `Refactorings` category
- ✅ **intentionDescriptions** directory with documentation files
- ✅ **Settings toggle** (`enableWrapTestInClassIntention`)

#### Testing
- ✅ **Comprehensive test suite** (`WrapTestInClassIntentionTest.kt`) with 18 tests covering:
  - Basic wrapping scenarios
  - Pytest decorators (`@pytest.mark.parametrize`, `@pytest.mark.skip`)
  - Type hints
  - Edge cases (availability checks, non-test functions, functions already in classes)
  - Settings toggle

---

### ❌ **What's Missing from the Spec**

#### 1. **Refactoring Action in Refactor Menu** (Phase 1 & 2)

**Missing:**
- No `<action>` entry in `plugin.xml` with `<add-to-group group-id="RefactoringMenu">`
- No `RefactoringActionHandler` implementation
- Currently only available as an intention (Alt+Enter), **not** in the Refactor menu (Ctrl+Alt+Shift+T)

**From spec (lines 342-350):**
```xml
<actions>
  <action id="WrapTestInClass" 
          class="com.yourcompany.pytest.refactoring.actions.WrapTestInClassAction"
          text="Wrap Test in Class"
          description="Wrap a module-level test function in a test class">
    <add-to-group group-id="RefactoringMenu" anchor="last"/>
  </action>
</actions>
```

**What's needed:**
- Create `WrapTestInClassAction` extending `RefactoringActionHandler`
- Register action in `plugin.xml` under `<actions>` section
- Wire action to invoke the intention logic

---

#### 2. **Dialog for Class Name Selection** (Phase 2.2)

**Missing:**
- No interactive dialog to choose/edit class name
- No option to add to existing test class vs. create new class
- No preview of changes before applying

**From spec (lines 123-130):**
```java
public class WrapTestInClassDialog extends RefactoringDialog {
  // Options:
  // - Create new class (with name field)
  // - Add to existing class (dropdown of test classes in file)
  // - Preview changes
}
```

**Current behavior:**
- Automatically generates class name without user input
- Always creates a new class (no option to add to existing class)

---

#### 3. **Advanced Refactoring Features** (Phase 1.2)

**Missing:**
- No reference tracking/updating (`PyClassRefactoringUtil.rememberNamedReferences`, `restoreNamedReferences`)
- No import optimization after refactoring (`PyClassRefactoringUtil.optimizeImports`)
- No proper PSI manipulation using `PyClassRefactoringUtil.addMethods` or `insertMethodInProperPlace`

**From spec (lines 19-24):**
```
- addMethods(PyClass destination, boolean skipIfExist, PyFunction... methods)
- insertMethodInProperPlace(PyStatementList destStatementList, PyFunction method)
- restoreNamedReferences(PsiElement element)
- rememberNamedReferences(PsiElement element, String... namesToSkip)
- optimizeImports(PsiFile file)
```

**Current implementation:**
- Uses simple string concatenation to build class text
- Doesn't track or update references to the moved function
- Doesn't optimize imports

---

#### 4. **Smart Features** (Phase 3)

**Missing:**

##### 3.1 Find Existing Test Classes
- No ability to detect existing test classes in the file
- No option to add function to an existing class

**From spec (lines 157-162):**
```java
private List<PyClass> findTestClassesInFile(PyFile file) {
  return file.getTopLevelClasses().stream()
    .filter(cls -> isTestClass(cls, TypeEvalContext.codeAnalysis(...)))
    .collect(Collectors.toList());
}
```

##### 3.2 Batch Operation
- No support for wrapping multiple test functions at once
- No option to wrap all module-level tests into one class

**From spec (lines 165-169):**
```
Allow wrapping multiple test functions at once:
- Select multiple functions
- Offer to wrap all in same class or separate classes
```

##### 3.3 Unique Name Generation
- No use of `PyRefactoringUtil.selectUniqueName` to avoid name conflicts
- No validation with `PyRefactoringUtil.isValidNewName`

---

#### 5. **Test Coverage Gaps** (Phase 3 - Testing Strategy)

**Missing test categories from spec (lines 269-305):**

##### Code Complexity Tests
- ❌ Test with local variables
- ❌ Test with nested functions
- ❌ Test with imports used only in that test
- ❌ Test with global variable references

##### Target Class Scenarios
- ❌ Add to existing empty test class
- ❌ Add to existing class with other tests
- ❌ Handle name conflicts

##### Edge Cases
- ❌ Test function with same name as existing method
- ❌ Multiple test functions with related names
- ❌ Test in file with existing test classes

##### Reference Updates
- ❌ Other tests calling this test (if any)
- ❌ References in comments
- ❌ References in docstrings

---

#### 6. **Documentation** (Phase 4)

**Missing:**
- ❌ User guide
- ❌ API documentation
- ❌ Example use cases
- ❌ Known limitations

**From spec (lines 381-386):**
```
#### **Documentation**
- [ ] User guide
- [ ] API documentation
- [ ] Example use cases
- [ ] Known limitations
```

---

#### 7. **Integration with PyCharm Refactoring Infrastructure**

**Missing:**
- No use of `PythonUnitTestDetectors.isTestFunction()` (spec line 12)
- No use of `PythonUnitTestDetectors.isTestClass()` (spec line 13)
- No integration with `PyTestCreator.generateTest()` (spec line 29)
- No proper refactoring handler pattern from `IntroduceHandler.java` (spec line 40)

**Current implementation:**
- Uses simple string check (`name.startsWith("test_")`)
- Doesn't leverage existing PyCharm test infrastructure

---

### 📊 **Implementation Checklist Status**

From spec lines 357-386:

#### Core Functionality
- ✅ Detect module-level test functions
- ✅ Validate refactoring availability
- ✅ Generate smart class name suggestions
- ✅ Create new test class with proper structure
- ✅ Convert function to method (add `self` parameter)
- ✅ Preserve decorators and docstrings
- ✅ Handle pytest fixtures correctly
- ❌ Update all references
- ❌ Optimize imports

#### User Interface
- ✅ Intention action on test functions
- ❌ Refactoring menu action
- ❌ Dialog for class name/selection
- ❌ Preview changes
- ✅ Undo support (automatic via write action)

#### Testing
- ✅ Unit tests for basic scenarios
- ❌ Integration tests with real pytest projects
- ❌ Performance tests for large files
- ⚠️ Edge case handling (partial)

#### Documentation
- ❌ User guide
- ❌ API documentation
- ❌ Example use cases
- ❌ Known limitations

---

### 🎯 **Priority Recommendations**

#### High Priority (Core Functionality)
1. **Add Refactoring Menu Action** - Currently only accessible via Alt+Enter, not Ctrl+Alt+Shift+T
2. **Reference Tracking** - Use `PyClassRefactoringUtil` to properly track and update references
3. **Import Optimization** - Clean up imports after refactoring

#### Medium Priority (User Experience)
4. **Interactive Dialog** - Allow user to edit class name and choose existing class
5. **Find Existing Classes** - Offer to add to existing test class
6. **Preview Changes** - Show diff before applying

#### Low Priority (Advanced Features)
7. **Batch Operation** - Wrap multiple functions at once
8. **Integration Tests** - Test with real pytest projects
9. **Documentation** - User guide and examples

---

### 💡 **Summary**

**What works:** Basic intention action that wraps a single module-level test function in a new class with proper formatting, decorators, and type hints.

**What's missing:** Refactoring menu integration, interactive dialog, reference tracking, import optimization, ability to add to existing classes, batch operations, and comprehensive edge case testing.

**Key gap:** The spec describes a full-featured refactoring with dialog, preview, and menu integration, but the current implementation is a simpler intention action with automatic behavior and no user interaction beyond triggering it.