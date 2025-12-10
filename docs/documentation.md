# Python DDD Toolkit - Zensical Documentation Plan

This document outlines a comprehensive plan to document all features of the Python DDD Toolkit IntelliJ plugin using [Zensical](https://zensical.org/docs/setup/basics/).

---

## Documentation Structure Overview

The documentation will be organized into the following sections, each covering a specific feature category with detailed explanations and usage examples.

---

## 1. Introduction

### Content to Document

- **Project Overview**: Python DDD Toolkit is an IntelliJ Platform plugin designed to facilitate Domain-Driven Design (DDD) in Python projects
- **Purpose**: Focus on "Strong Typing" by making it easy to introduce and use custom domain types (Value Objects) instead of primitive types
- **Target Audience**: Python developers using PyCharm/IntelliJ IDEA who want to apply DDD principles
- **Prerequisites**: PyCharm or IntelliJ IDEA with Python plugin

### Zensical Setup Instructions

```toml
# zensical.toml
[project]
name = "Python DDD Toolkit"
site_url = "https://example.com/python-ddd-toolkit"
site_description = "IntelliJ plugin for Domain-Driven Design in Python"

[theme]
name = "material"
palette.scheme = "slate"
palette.primary = "indigo"

[nav]
- Home: index.md
- Getting Started:
  - Installation: getting-started/installation.md
  - Quick Start: getting-started/quick-start.md
- Features:
  - Type Mismatch: features/type-mismatch.md
  - Custom Types: features/custom-types.md
  - Arguments: features/arguments.md
  - Refactoring: features/refactoring.md
  - Visibility: features/visibility.md
  - Type Hints: features/type-hints.md
  - Dictionary Access: features/dict-access.md
  - Exceptions: features/exceptions.md
  - Local Variables: features/local-variables.md
- Inspections: inspections/index.md
- Actions: actions/index.md
- Advanced:
  - Import Handling: advanced/import-handling.md
  - PSI References: advanced/psi-references.md
  - Search Everywhere: advanced/search-everywhere.md
  - Structure View: advanced/structure-view.md
  - Console Filtering: advanced/console-filtering.md
- Settings: settings.md
```

### Documentation Files to Create

- `docs/index.md` - Landing page with overview
- `docs/getting-started/installation.md`
- `docs/getting-started/quick-start.md`

---

## 2. Installation Section

### Content to Document

#### IDE Built-in Plugin System
```
Settings/Preferences > Plugins > Marketplace > Search for "Python DDD Toolkit" > Install
```

#### JetBrains Marketplace
- Direct link to marketplace page
- "Install to IDE" button functionality

#### Manual Installation
- Download from GitHub releases
- Install via: `Settings/Preferences > Plugins > ⚙️ > Install plugin from disk...`

### Usage Examples

```markdown
!!! tip "Recommended Installation"
    The easiest way to install is through the IDE's built-in plugin system.
    This ensures automatic updates.

!!! warning "Compatibility"
    Requires PyCharm 2023.1+ or IntelliJ IDEA 2023.1+ with Python plugin.
```

### Documentation Files to Create

- `docs/getting-started/installation.md`

---

## 3. Type Mismatch Intentions Section

### Features to Document

#### 3.1 Wrap with Expected Type (`WrapWithExpectedTypeIntention`)

**Purpose**: Automatically wraps an expression that causes a type mismatch with the expected type constructor.

**Trigger**: Type mismatch between an expression and the expected type (determined by type hints).

**Usage Examples**:

```python
# Before: Type mismatch - expected UserId, got int
def get_user(user_id: UserId) -> User:
    pass

get_user(123)  # <-- Caret here, invoke intention

# After: Wrapped with expected type
get_user(UserId(123))
```

```python
# Union type example - shows popup to choose
def process(value: UserId | OrderId) -> None:
    pass

process(123)  # <-- Shows popup: "UserId" or "OrderId"
```

**Supported Scenarios**:
- Single types: `value` → `ExpectedType(value)`
- Union types: Popup to choose which type
- Container elements: Wrap specific items in list/set/tuple literals

**Exclusions**:
- Does not trigger if `Unwrap` would resolve the mismatch
- Does not trigger for `typing.Literal` types
- Does not trigger on `*args` or `**kwargs`
- Does not trigger on variable names in definition/assignment (LHS)

#### 3.2 Wrap Items with Expected Type (`WrapItemsWithExpectedTypeIntention`)

**Purpose**: Batch wrapping for container literals.

**Usage Examples**:

```python
# Before: List of ints where List[UserId] expected
def process_users(ids: list[UserId]) -> None:
    pass

process_users([1, 2, 3])  # <-- Invoke intention

# After: All items wrapped
process_users([UserId(1), UserId(2), UserId(3)])
```

**Supported Containers**: Lists, Sets, Tuples

**Limitations**: Dictionaries not supported (ambiguity between keys/values)

#### 3.3 Unwrap to Expected Type (`UnwrapToExpectedTypeIntention`)

**Purpose**: Removes unnecessary wrappers when the inner value matches the expected type.

**Usage Examples**:

```python
# Before: Wrapped value where primitive expected
def save_to_db(user_id: int) -> None:
    pass

save_to_db(UserId(123))  # <-- Invoke intention

# After: Unwrapped
save_to_db(123)
```

#### 3.4 Unwrap Items to Expected Type (`UnwrapItemsToExpectedTypeIntention`)

**Purpose**: Batch unwrapping for container literals.

**Usage Examples**:

```python
# Before: List of wrapped values where list[int] expected
def process_raw_ids(ids: list[int]) -> None:
    pass

process_raw_ids([UserId(1), UserId(2)])  # <-- Invoke intention

# After: All items unwrapped
process_raw_ids([1, 2])
```

### Documentation Files to Create

- `docs/features/type-mismatch.md`
- `docs/features/type-mismatch/wrap.md`
- `docs/features/type-mismatch/wrap-items.md`
- `docs/features/type-mismatch/unwrap.md`
- `docs/features/type-mismatch/unwrap-items.md`

---

## 4. Custom Types Section

### Features to Document

#### Introduce Custom Type from Stdlib (`IntroduceCustomTypeFromStdlibIntention`)

**Purpose**: Refactors primitive type usages into new custom domain types (Value Objects).

**Trigger**: Specific builtin types (`int`, `str`, etc.) in annotations, default values, or variable assignments.

**Usage Examples**:

```python
# Before: Primitive type annotation
def get_user(user_id: int) -> User:  # <-- Caret on 'int', invoke intention
    pass

# After: Custom type introduced
class UserId(int):
    pass

def get_user(user_id: UserId) -> User:
    pass
```

```python
# With default value
def create_order(quantity: int = 1):  # <-- Invoke intention
    pass

# After:
class Quantity(int):
    pass

def create_order(quantity: Quantity = Quantity(1)):
    pass
```

**Naming Strategy**:
- Derives name from context (variable name, parameter name, keyword argument)
- `user_id: int` → `UserId`
- `order_count: int` → `OrderCount`

**Supported Scenarios**:
- Annotations (including nested Union types)
- Default values in parameters
- Variable assignments

### Documentation Files to Create

- `docs/features/custom-types.md`
- `docs/features/custom-types/introduce-type.md`
- `docs/features/custom-types/naming-conventions.md`

---

## 5. Arguments Section

### Features to Document

#### Populate Arguments (`PopulateArgumentsIntention`)

**Purpose**: Automatically fills in function/method call arguments based on the function signature.

**Usage Examples**:

```python
# Function definition
def create_user(name: str, email: str, age: int, active: bool = True):
    pass

# Before: Empty call
create_user()  # <-- Invoke intention

# After: Arguments populated
create_user(name=, email=, age=)
```

```python
# Dataclass example
@dataclass
class User:
    name: str
    email: str
    age: int

# Before
User()  # <-- Invoke intention

# After
User(name=, email=, age=)
```

**Supported Types**:
- Regular functions and methods
- Dataclasses
- Pydantic models
- Named tuples

### Documentation Files to Create

- `docs/features/arguments.md`
- `docs/features/arguments/populate.md`

---

## 6. Refactoring Section

### Features to Document

#### 6.1 Introduce Parameter Object (`PyIntroduceParameterObjectIntention` / `PyIntroduceParameterObjectAction`)

**Purpose**: Refactors multiple function parameters into a single parameter object (dataclass).

**Usage Examples**:

```python
# Before: Function with many parameters
def create_order(
    customer_id: int,
    product_id: int,
    quantity: int,
    price: Decimal,
    discount: Decimal = Decimal(0)
):  # <-- Invoke intention
    pass

# After: Parameter object introduced
@dataclass
class CreateOrderParams:
    customer_id: int
    product_id: int
    quantity: int
    price: Decimal
    discount: Decimal = Decimal(0)

def create_order(params: CreateOrderParams):
    pass
```

**Access Points**:
- Intention action (Alt+Enter)
- Refactoring menu

#### 6.2 Copy Block with Dependencies (`CopyBlockWithDependenciesIntention`)

**Purpose**: Copies a code block along with all its dependencies (imports, referenced functions, classes).

**Usage Examples**:

```python
# Select this function and invoke intention
def calculate_total(items: list[Item]) -> Money:
    return Money(sum(item.price for item in items))

# Clipboard will contain:
# - The function
# - Import for Money class
# - Import for Item class (if needed)
```

### Documentation Files to Create

- `docs/features/refactoring.md`
- `docs/features/refactoring/parameter-object.md`
- `docs/features/refactoring/copy-with-dependencies.md`

---

## 7. Visibility Section

### Features to Document

#### Change Visibility (`PyChangeVisibilityIntention`)

**Purpose**: Toggles Python naming conventions for public/private/protected visibility.

**Usage Examples**:

```python
# Public to private
def my_function():  # <-- Invoke intention
    pass
# Result:
def _my_function():
    pass

# Private to dunder (name mangling)
def _my_method(self):  # <-- Invoke intention
    pass
# Result:
def __my_method(self):
    pass

# Works on variables too
my_var = 1  # → _my_var → __my_var
```

**Visibility Levels**:
- Public: `name`
- Protected/Private: `_name`
- Name-mangled: `__name`

### Documentation Files to Create

- `docs/features/visibility.md`

---

## 8. Type Hints Section

### Features to Document

#### Make Parameter Optional (`MakeParameterOptionalIntention`)

**Purpose**: Converts a required parameter to an optional one with `None` as default.

**Usage Examples**:

```python
# Before: Required parameter
def get_user(user_id: int):  # <-- Invoke intention on 'user_id'
    pass

# After: Optional parameter
def get_user(user_id: int | None = None):
    pass
```

```python
# With existing type hint
def process(data: str):  # <-- Invoke intention
    pass

# After:
def process(data: str | None = None):
    pass
```

### Documentation Files to Create

- `docs/features/type-hints.md`
- `docs/features/type-hints/optional-parameter.md`

---

## 9. Dictionary Access Section

### Features to Document

#### 9.1 Dict Access Intention (`PyDictAccessIntention`)

**Purpose**: Toggles between bracket access `d[key]` and safe `d.get(key)`.

**Usage Examples**:

```python
# Bracket to get
data["key"]  # <-- Invoke intention
# Result:
data.get("key")

# Get to bracket
data.get("key")  # <-- Invoke intention
# Result:
data["key"]
```

**Exclusions**:
- Assignment targets: `d["key"] = value`
- Augmented assignment: `d["key"] += 1`
- Delete statements: `del d["key"]`
- `get` with default argument (different semantics)

#### 9.2 Try/Except to Dict Get (`PyTryExceptToDictGetIntention`)

**Purpose**: Converts try/except KeyError blocks to `dict.get()`.

**Usage Examples**:

```python
# Before
try:
    value = data["key"]
except KeyError:
    value = default

# After (invoke intention)
value = data.get("key", default)
```

#### 9.3 Dict Get to Try/Except (`PyDictGetToTryExceptIntention`)

**Purpose**: Converts `dict.get()` to explicit try/except block.

**Usage Examples**:

```python
# Before
value = data.get("key", default)

# After (invoke intention)
try:
    value = data["key"]
except KeyError:
    value = default
```

### Documentation Files to Create

- `docs/features/dict-access.md`
- `docs/features/dict-access/bracket-vs-get.md`
- `docs/features/dict-access/try-except-conversion.md`

---

## 10. Exceptions Section

### Features to Document

#### 10.1 Add Exception Capture (`AddExceptionCaptureIntention`)

**Purpose**: Adds `as` clause to capture exception in except block.

**Usage Examples**:

```python
# Before
try:
    risky_operation()
except ValueError:  # <-- Invoke intention
    handle_error()

# After
try:
    risky_operation()
except ValueError as e:
    handle_error()
```

#### 10.2 Wrap Exceptions with Parentheses (`WrapExceptionsWithParenthesesIntention`)

**Purpose**: Wraps multiple exception types in parentheses (Python 3 style).

**Usage Examples**:

```python
# Before (Python 2 style - deprecated)
try:
    operation()
except ValueError, TypeError:  # <-- Invoke intention
    pass

# After (Python 3 style)
try:
    operation()
except (ValueError, TypeError):
    pass
```

### Documentation Files to Create

- `docs/features/exceptions.md`
- `docs/features/exceptions/capture.md`
- `docs/features/exceptions/parentheses.md`

---

## 11. Local Variables Section

### Features to Document

#### Create Local Variable (`CreateLocalVariableIntention`)

**Purpose**: Extracts an expression into a local variable.

**Usage Examples**:

```python
# Before
print(calculate_complex_value() * 2)  # <-- Select expression, invoke intention

# After
result = calculate_complex_value()
print(result * 2)
```

```python
# With type inference
users = get_users()
print(users[0].name)  # <-- Select 'users[0]', invoke intention

# After
user: User = users[0]
print(user.name)
```

### Documentation Files to Create

- `docs/features/local-variables.md`

---

## 12. Inspections Section

### Features to Document

#### 12.1 Symbol Not Exported in `__all__` (`PyMissingInDunderAllInspection`)

**Purpose**: Warns when a public symbol is not included in `__all__`.

**Usage Examples**:

```python
# Warning: 'MyClass' is not in __all__
__all__ = ["other_function"]

class MyClass:  # <-- Warning here
    pass

def other_function():
    pass
```

**Quick Fixes**:
- Add symbol to `__all__`
- Make symbol private (prefix with `_`)

#### 12.2 Import from Private Module (`PyPrivateModuleImportInspection`)

**Purpose**: Warns when importing from a private module when the symbol is exported from the package.

**Usage Examples**:

```python
# Warning: Import from private module
from mypackage._internal import MyClass  # <-- Warning

# Suggested fix:
from mypackage import MyClass  # If exported in __init__.py
```

#### 12.3 Dataclass Missing Decorator (`PyDataclassMissingInspection`)

**Purpose**: Warns when a class inherits from a dataclass but is not decorated with `@dataclass`.

**Usage Examples**:

```python
@dataclass
class BaseModel:
    id: int

# Warning: Should be decorated with @dataclass
class User(BaseModel):  # <-- Warning here
    name: str

# Fix:
@dataclass
class User(BaseModel):
    name: str
```

### Documentation Files to Create

- `docs/inspections/index.md`
- `docs/inspections/dunder-all.md`
- `docs/inspections/private-module-import.md`
- `docs/inspections/dataclass-missing.md`

---

## 13. Actions Section

### Features to Document

#### 13.1 Copy Package Content (`CopyPackageContentAction`)

**Purpose**: Copies the content of all files in a selected directory to clipboard.

**Access**: Right-click on folder in Project View > "Copy Package Content"

**Usage Examples**:

```
1. Right-click on a package folder
2. Select "Copy Package Content"
3. All Python files' content is copied to clipboard
```

#### 13.2 Copy Build Number (`CopyBuildNumberAction`)

**Purpose**: Copies the current IDE build number to clipboard.

**Access**: Help menu > "Copy Build Number"

**Use Case**: Useful for bug reports and compatibility checks.

#### 13.3 Copy Pytest Node IDs (`CopyPytestNodeIdAction`)

**Purpose**: Copies pytest node IDs for selected tests.

**Access**: Test tree context menu > "Copy Special" > "Copy Pytest Node IDs"

**Usage Examples**:

```
# Selected test in test tree
test_module.py::TestClass::test_method

# Copied to clipboard:
test_module.py::TestClass::test_method
```

#### 13.4 Copy FQNs (`CopyFQNAction`)

**Purpose**: Copies fully qualified names of selected test nodes.

**Access**: Test tree context menu > "Copy Special" > "Copy FQNs"

#### 13.5 Copy Stacktrace (`CopyStacktraceAction`)

**Purpose**: Copies the stacktrace from failed tests.

**Access**: Test tree context menu > "Copy Special" > "Copy Stacktrace"

### Documentation Files to Create

- `docs/actions/index.md`
- `docs/actions/copy-package-content.md`
- `docs/actions/copy-build-number.md`
- `docs/actions/pytest-actions.md`

---

## 14. Import Handling Section

### Features to Document

#### 14.1 Source Root Prefix Provider (`SourceRootPrefixProvider`)

**Purpose**: Provides canonical import paths based on source roots.

**Behavior**:
- Ensures imports use the correct prefix based on project structure
- Respects source root configuration

#### 14.2 Specific Import Path Provider (`SpecificImportPathProvider`)

**Purpose**: Provides the most specific import path for symbols.

**Usage Examples**:

```python
# Instead of:
from mypackage.subpackage.module import MyClass

# Suggests (if exported):
from mypackage import MyClass
```

#### 14.3 Relative Import Candidate Provider (`RelativeImportCandidateProvider`)

**Purpose**: Suggests relative imports within the same package.

**Usage Examples**:

```python
# In mypackage/submodule.py
# Instead of:
from mypackage.utils import helper

# Suggests:
from .utils import helper
```

### Documentation Files to Create

- `docs/advanced/import-handling.md`
- `docs/advanced/import-handling/source-roots.md`
- `docs/advanced/import-handling/relative-imports.md`

---

## 15. PSI References Section

### Features to Document

#### 15.1 Mock Patch Reference Contributor (`PyMockPatchReferenceContributor`)

**Purpose**: Provides navigation and completion for `unittest.mock.patch` target strings.

**Usage Examples**:

```python
from unittest.mock import patch

# Ctrl+Click on the string navigates to the actual function
@patch("mymodule.my_function")
def test_something(mock_func):
    pass
```

**Features**:
- Go to definition from patch target string
- Autocomplete for patch targets
- Rename refactoring support

#### 15.2 Filter Warnings Reference Contributor (`PyFilterWarningsReferenceContributor`)

**Purpose**: Provides navigation for `filterwarnings` category strings.

**Usage Examples**:

```python
import warnings

# Navigation support for warning category
warnings.filterwarnings("ignore", category=DeprecationWarning)
```

### Documentation Files to Create

- `docs/advanced/psi-references.md`
- `docs/advanced/psi-references/mock-patch.md`
- `docs/advanced/psi-references/filter-warnings.md`

---

## 16. Search Everywhere Section

### Features to Document

#### Pytest Identifier Contributor (`PytestIdentifierContributor`)

**Purpose**: Allows searching for pytest test nodes in "Search Everywhere" dialog.

**Usage Examples**:

```
1. Press Shift+Shift (Search Everywhere)
2. Type pytest node ID: "test_module::test_function"
3. Navigate directly to the test
```

**Supported Formats**:
- `test_file.py::test_function`
- `test_file.py::TestClass::test_method`
- `test_file.py::TestClass::test_method[param]`

### Documentation Files to Create

- `docs/advanced/search-everywhere.md`

---

## 17. Structure View Section

### Features to Document

#### Private Members Filter (`MyPrivateMembersFilter`)

**Purpose**: Filters private members (prefixed with `_`) in the Structure View.

**Usage Examples**:

```
1. Open Structure View (Alt+7 / Cmd+7)
2. Click filter icon
3. Toggle "Show Private Members"
```

**Behavior**:
- Hides/shows members starting with `_`
- Includes `__dunder__` methods option

### Documentation Files to Create

- `docs/advanced/structure-view.md`

---

## 18. Console & Usage Filtering Section

### Features to Document

#### 18.1 Console Filter Provider (`PyMessageFilterProvider`)

**Purpose**: Provides clickable links in console output for Python file references.

**Features**:
- Clickable file paths in tracebacks
- Navigation to specific line numbers
- Works with pytest output

#### 18.2 Type Annotation Filtering Rule Provider (`PyTypeAnnotationFilteringRuleProvider`)

**Purpose**: Filters usages that occur within type annotations in "Find Usages".

**Usage Examples**:

```
1. Find Usages on a class (Alt+F7)
2. Filter panel shows "Type Annotations" filter
3. Toggle to show/hide usages in type hints
```

### Documentation Files to Create

- `docs/advanced/console-filtering.md`
- `docs/advanced/usage-filtering.md`

---

## 19. Settings Section

### Features to Document

#### Plugin Settings (`PluginSettingsConfigurable` / `PluginSettingsState`)

**Access**: Settings > Tools > DDD Toolkit

**Configuration Options** (to be documented based on implementation):
- Enable/disable specific intentions
- Import path preferences
- Inspection severity levels
- Custom type naming conventions

### Documentation Files to Create

- `docs/settings.md`

---

## Implementation Checklist

### Phase 1: Setup
- [ ] Initialize Zensical project with `zensical.toml`
- [ ] Create directory structure
- [ ] Set up theme and navigation

### Phase 2: Core Documentation
- [ ] Write Introduction and Installation guides
- [ ] Document Type Mismatch intentions (highest priority)
- [ ] Document Custom Types feature
- [ ] Document Arguments feature

### Phase 3: Feature Documentation
- [ ] Document Refactoring features
- [ ] Document Visibility feature
- [ ] Document Type Hints feature
- [ ] Document Dictionary Access features
- [ ] Document Exceptions features
- [ ] Document Local Variables feature

### Phase 4: Inspections & Actions
- [ ] Document all Inspections
- [ ] Document all Actions

### Phase 5: Advanced Features
- [ ] Document Import Handling
- [ ] Document PSI References
- [ ] Document Search Everywhere
- [ ] Document Structure View
- [ ] Document Console & Usage Filtering

### Phase 6: Final
- [ ] Document Settings
- [ ] Add screenshots/GIFs for visual features
- [ ] Review and cross-link documentation
- [ ] Deploy documentation site

---

## Zensical-Specific Features to Use

### Admonitions
```markdown
!!! tip "Pro Tip"
    Use keyboard shortcut Alt+Enter to quickly access intentions.

!!! warning "Breaking Change"
    This feature requires PyCharm 2023.1 or later.

!!! note "Note"
    This intention only works with type-annotated code.
```

### Code Blocks with Annotations
```python
def get_user(user_id: int) -> User:  # (1)!
    pass

get_user(123)  # (2)!
```

1. Type hint indicates expected type
2. Caret position to invoke intention

### Content Tabs
```markdown
=== "Before"
    ```python
    get_user(123)
    ```

=== "After"
    ```python
    get_user(UserId(123))
    ```
```

### Keyboard Keys
```markdown
Press ++alt+enter++ to invoke the intention.
Press ++ctrl+shift+a++ to open actions.
```

---

## File Structure Summary

```
docs/
├── index.md
├── getting-started/
│   ├── installation.md
│   └── quick-start.md
├── features/
│   ├── type-mismatch.md
│   ├── custom-types.md
│   ├── arguments.md
│   ├── refactoring.md
│   ├── visibility.md
│   ├── type-hints.md
│   ├── dict-access.md
│   ├── exceptions.md
│   └── local-variables.md
├── inspections/
│   ├── index.md
│   ├── dunder-all.md
│   ├── private-module-import.md
│   └── dataclass-missing.md
├── actions/
│   ├── index.md
│   ├── copy-package-content.md
│   ├── copy-build-number.md
│   └── pytest-actions.md
├── advanced/
│   ├── import-handling.md
│   ├── psi-references.md
│   ├── search-everywhere.md
│   ├── structure-view.md
│   ├── console-filtering.md
│   └── usage-filtering.md
├── settings.md
└── zensical.toml
```
