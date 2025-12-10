### Plan for Adding String Reference Support

To treat literal strings as PSI elements in `mock.patch()` and `pytest.mark.filterwarnings()`, you need to implement a `PsiReferenceContributor`. This contributor will register a `PsiReferenceProvider` that inspects string literals and, if they appear in the specified contexts, injects references that resolve to the corresponding Python elements.

#### 1. Implementation Steps

1.  **Create a Reference Contributor**
    *   Create a class `PyTestReferenceContributor` extending `com.intellij.psi.PsiReferenceContributor`.
    *   Implement `registerReferenceProviders`.

2.  **Define Element Patterns**
    *   Use `PlatformPatterns.psiElement(PyStringLiteralExpression.class)` to match string literals.
    *   Refine the pattern to match arguments of `mock.patch` and `pytest.mark.filterwarnings`.
    *   For `mock.patch`: Check if the literal is an argument in a call expression where the callee resolves to `unittest.mock.patch` (or aliases).
    *   For `filterwarnings`: Check if the literal is an argument in a decorator call `@pytest.mark.filterwarnings`.

3.  **Implement Reference Providers**
    *   **Mock Patch Provider**:
        *   Extract the string value.
        *   Create a custom reference class (e.g., `PyQualifiedNameReference`) extending `PsiPolyVariantReferenceBase<PyStringLiteralExpression>`.
        *   In `multiResolve`, use `PyPsiFacade.getInstance(project).resolveQualifiedName(...)` to find the target element (module, class, method).
    *   **Filter Warnings Provider**:
        *   Parse the warning filter string (format: `action:message:category:module:lineno`).
        *   Extract the `category` part (exception class name).
        *   Calculate the text range for the category substring.
        *   Create a reference that resolves this class name using `PyPsiFacade`.

4.  **Register in Plugin.xml**
    *   Register the contributor in your `plugin.xml` (or specific module xml):
        ```xml
        <extensions defaultExtensionNs="com.intellij">
            <psi.referenceContributor language="Python" implementation="com.your.package.PyTestReferenceContributor"/>
        </extensions>
        ```

#### 2. Test Cases

Create a test class extending `PyTestCase` (standard base class for Python plugin tests).

**Test 1: Mock Patch Resolution**
```python
# test_mock_patch.py
from unittest.mock import patch
import os.path

# <caret> is the cursor position for the test
@patch('os.path.ex<caret>ists') 
def test_something(mock_exists):
    pass
```
*   **Action**: Call `myFixture.getElementAtCaret()`.
*   **Assertion**: Verify that the resolved element is the `exists` function in `os.path`.

**Test 2: Mock Patch Class Resolution**
```python
from unittest import mock

class MyClass:
    def method(self): pass

def test_foo():
    with mock.patch('MyMo<caret>dule.MyClass'):
        pass
```
*   **Assertion**: Verify resolution to `MyClass` in `MyModule`.

**Test 3: Pytest Filter Warnings Resolution**
```python
import pytest

@pytest.mark.filterwarnings("ignore::Depreca<caret>tionWarning")
def test_warning():
    pass
```
*   **Action**: Call `myFixture.getElementAtCaret()`.
*   **Assertion**: Verify that the resolved element is the `DeprecationWarning` class.

**Test 4: Rename Refactoring (Optional but recommended)**
*   Trigger rename on the target class/function.
*   Verify that the string inside `mock.patch` is updated.

### Code Skeleton

```java
public class PyTestReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PyStringLiteralExpression.class),
      new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          PyStringLiteralExpression literal = (PyStringLiteralExpression) element;
          // Add logic to check if literal is inside mock.patch or filterwarnings
          if (isMockPatchArgument(literal)) {
             return new PsiReference[]{ new PyQualifiedNameReference(literal) };
          }
          return PsiReference.EMPTY_ARRAY;
        }
      }
    );
  }
}
```