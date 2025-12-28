### Plan for Supporting `unittest.mock` in PyCharm Typing System

To support better typing for `Mock` and `AsyncMock` when `spec` or `spec_set` is provided, we need to implement a specialized `PyTypeProvider`. This provider will intercept calls to these mock constructors and attributes, ensuring that the resulting objects and their members reflect the types from the specified `spec`.

#### 1. Implementation Strategy

*   **Custom `PyTypeProvider`**: Implement a new `PyMockTypeProvider` (extending `PyTypeProviderBase`) to handle `unittest.mock.Mock`, `unittest.mock.MagicMock`, and `unittest.mock.AsyncMock`.
*   **Intercepting Constructor Calls**:
    *   Override `getCallType` to detect calls to mock classes.
    *   If `spec` or `spec_set` argument is present, extract the type (usually a `PyClass` or `PyClassType`).
    *   Return a specialized `PyMockType` that wraps the target `spec` type.
*   **Specialized `PyMockType`**:
    *   This type should wrap the original `spec` type (the "skeleton").
    *   **Member Resolution**: Override `resolveMember` and `getMemberTypes`.
        *   When a member (method or property) is accessed, it should return another `Mock` instance but with its type "matching" the member's type in the `spec`.
        *   For example, if `spec=MyClass` and `MyClass.do_something` returns `int`, then `mock_instance.do_something` should be a `Mock` object that, when called, is known to return `int`.
*   **Handling `AsyncMock`**:
    *   Ensure that for `AsyncMock`, the members are also treated as `AsyncMock` or awaitable where appropriate according to the `spec`.

#### 2. Technical Details

*   **EP Registration**: Register the provider in `plugin.xml` under the `Pythonid.typeProvider` extension point.
*   **Type Matching**: Use `TypeEvalContext` to resolve the `spec` argument to a `PyType`.
*   **Member Type Inference**:
    *   If the member in `spec` is a function, the mock member should be a `Mock`/`Callable` whose `getCallType` returns the original function's return type.
    *   If the member is a property/attribute, the mock member's type should be inferred as a `Mock` wrapping the attribute's type.

---

### Test Cases

These test cases should be implemented as `Py3TypeTest` or specialized `PyMockTypeTest` scenarios.

#### Test Case 1: Constructor with `spec` as Class
```python
from unittest.mock import Mock

class MyClass:
    def do_something(self) -> int: ...
    @property
    def name(self) -> str: ...

def test_mock_spec():
    m = Mock(spec=MyClass)
    # Expected: m.do_something is inferred as a mock of a method returning int
    expr1 = m.do_something()
    # assert_type(expr1, "int")
    
    # Expected: m.name is inferred as a mock of a property of type str
    expr2 = m.name
    # assert_type(expr2, "str | Mock") # Depending on how deep we go
```

#### Test Case 2: `AsyncMock` with `spec`
```python
from unittest.mock import AsyncMock

class MyAsyncClass:
    async def compute(self) -> float: ...

async def test_async_mock():
    m = AsyncMock(spec=MyAsyncClass)
    # Expected: compute is an AsyncMock
    expr = await m.compute()
    # assert_type(expr, "float")
```

#### Test Case 3: `spec_set` enforcement
```python
from unittest.mock import Mock

class MyClass:
    existing_attr: int

def test_spec_set():
    m = Mock(spec_set=MyClass)
    # Expected: completion should show 'existing_attr'
    # Expected: 'non_existing_attr' should be flagged as unresolved by inspection
    print(m.existing_attr)
    print(m.non_existing_attr) # Should show warning
```

#### Test Case 4: Deep Mocking (Chained calls)
```python
from unittest.mock import Mock

class Internal:
    def value(self) -> int: ...

class Outer:
    def get_internal(self) -> Internal: ...

def test_deep_mock():
    m = Mock(spec=Outer)
    # m.get_internal() returns a Mock(spec=Internal)
    # m.get_internal().value() returns int
    res = m.get_internal().value()
    # assert_type(res, "int")
```

#### Test Case 5: `MagicMock` and Magic Methods
```python
from unittest.mock import MagicMock

class IterableClass:
    def __iter__(self):
        return iter([1, 2, 3])

def test_magic_mock():
    m = MagicMock(spec=IterableClass)
    # Expected: m should be recognized as iterable
    for x in m:
        # assert_type(x, "int") # If spec allows inferring element type
        pass
```

### Summary of Tasks
1.  **Identify** `unittest.mock` calls in `PyTypeProvider`.
2.  **Extract** `spec`/`spec_set` class information.
3.  **Proxy** member resolution from the Mock to the `spec` class.
4.  **Wrap** resolved member types back into Mock types to maintain the "mocking" behavior while preserving type hints.
5.  **Verify** with completion and type-checking tests.