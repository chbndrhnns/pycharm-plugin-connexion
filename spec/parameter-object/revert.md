# Inline Parameter Object (Reverse Intention)

## Overview

The **"Inline Parameter Object"** intention is the reverse of "Introduce Parameter Object". It converts a function that accepts a parameter object (dataclass/NamedTuple/TypedDict/attrs/Pydantic model) back to individual parameters, updating all call sites accordingly.

This refactoring is useful when:
- A parameter object was introduced prematurely and adds unnecessary complexity
- The grouped parameters are no longer conceptually related
- The codebase is being simplified or refactored
- A parameter object is only used in one place and the indirection isn't justified

---

## Trigger Conditions

### 1. From Function Signature (Definition Site)

The intention is available when the caret is on:
- A parameter that has a type annotation referencing a known parameter object class (dataclass, NamedTuple, TypedDict, attrs, Pydantic BaseModel)
- The parameter name itself (e.g., `params` in `def foo(params: MyParams)`)

**Detection criteria:**
- The parameter type resolves to a class decorated with `@dataclass`, or inheriting from `NamedTuple`, `TypedDict`, `BaseModel` (Pydantic), or decorated with `@attrs.define`/`@attr.s`
- The class has extractable fields (dataclass fields, NamedTuple fields, TypedDict keys, etc.)

### 2. From Call Site (Usage Site)

The intention is available when the caret is on:
- A parameter object instantiation passed as an argument: `foo(MyParams(a=1, b=2))`
- The class name in the instantiation
- Any of the arguments within the instantiation

**Detection criteria:**
- The argument is a call expression constructing a parameter object class
- The called function's signature accepts this parameter object type

---

## Scope Options

When the intention is invoked, the user should be presented with scope options:

### Option 1: Single Usage (Call Site Trigger Only)

- Only convert the specific call site where the intention was triggered
- The function signature remains unchanged
- Other call sites remain unchanged
- The parameter object class is NOT deleted

**Use case:** Gradual migration, testing the change at one location first

### Option 2: All Usages

- Convert the function signature to accept individual parameters
- Update ALL call sites that pass a parameter object instance
- Offer to delete the parameter object class if:
  - This was the only function using it, OR
  - All usages across all functions have been converted

### Option 3: All Usages of This Function

- Convert the function signature
- Update all call sites of THIS function only
- Other functions using the same parameter object class are not affected
- Class deletion is offered only if no other usages remain

---

## Class Deletion Logic

After inlining, the plugin should analyze whether the parameter object class is still used:

### Automatic Analysis

1. Search for all references to the parameter object class
2. Exclude the usages that were just converted
3. If no remaining usages exist:
   - Prompt: "The class `MyParams` is no longer used. Delete it?"
   - Options: "Delete", "Keep", "Delete and remove import"

### Remaining Usage Scenarios

The class should NOT be offered for deletion if:
- It's used as a type annotation elsewhere (e.g., in a variable declaration)
- It's used in another function's signature
- It's instantiated elsewhere (not as a direct argument)
- It's exported in `__all__`
- It's a base class for another class
- It's referenced in documentation strings

---

## Transformation Algorithm

### Step 1: Extract Field Information

From the parameter object class, extract:
- Field names
- Field types (if annotated)
- Default values (if any)
- Field order

### Step 2: Transform Function Signature

**Before:**
```python
def create_user(params: CreateUserParams) -> None:
    print(params.first_name, params.last_name)
```

**After:**
```python
def create_user(first_name: str, last_name: str, email: str, age: int) -> None:
    print(first_name, last_name)
```

### Step 3: Transform Function Body

Replace all `params.field_name` references with `field_name`:
- `params.first_name` → `first_name`
- `params.last_name` → `last_name`

Handle complex expressions:
- `params.first_name.upper()` → `first_name.upper()`
- `f"{params.first_name}"` → `f"{first_name}"`
- `getattr(params, 'first_name')` → `first_name` (or leave unchanged with warning)

### Step 4: Transform Call Sites

**Before:**
```python
create_user(CreateUserParams("John", "Doe", "john@example.com", 30))
# or with keyword arguments:
create_user(CreateUserParams(first_name="John", last_name="Doe", email="john@example.com", age=30))
```

**After:**
```python
create_user("John", "Doe", "john@example.com", 30)
# or with keyword arguments:
create_user(first_name="John", last_name="Doe", email="john@example.com", age=30)
```

### Step 5: Handle Variable References

If the parameter object is stored in a variable before being passed:

**Before:**
```python
params = CreateUserParams("John", "Doe", "john@example.com", 30)
create_user(params)
```

**After (Option A - Inline the variable):**
```python
create_user("John", "Doe", "john@example.com", 30)
```

**After (Option B - Keep variable, extract fields):**
```python
params = CreateUserParams("John", "Doe", "john@example.com", 30)
create_user(params.first_name, params.last_name, params.email, params.age)
```

---

## Edge Cases

### 1. Partial Field Usage

The function only uses some fields of the parameter object:

```python
def greet(params: UserParams) -> str:
    return f"Hello, {params.first_name}!"  # last_name, email, age unused
```

**Behavior options:**
- A) Only add parameters for fields actually used in the function body
- B) Add all fields as parameters (preserves API compatibility)
- C) Ask user which approach to use

**Recommendation:** Default to (B) for API compatibility, with option to choose (A).

### 2. Nested Parameter Objects

A parameter object contains another parameter object:

```python
@dataclass
class Address:
    street: str
    city: str

@dataclass
class UserParams:
    name: str
    address: Address  # Nested parameter object
```

**Behavior:**
- Do NOT recursively inline nested objects
- The `address` field becomes a parameter of type `Address`
- User can run the intention again on the nested type if desired

### 3. Multiple Functions Using Same Class

```python
def create_user(params: UserParams) -> User: ...
def update_user(user_id: int, params: UserParams) -> User: ...
def validate_user(params: UserParams) -> bool: ...
```

**Behavior:**
- When triggered from one function, only that function is converted by default
- Offer "Convert all functions using UserParams" as an additional option
- Track which functions have been converted for class deletion logic

### 4. Inheritance / Subclasses

The parameter object class is subclassed:

```python
@dataclass
class BaseParams:
    id: int

@dataclass
class ExtendedParams(BaseParams):
    name: str
```

**Behavior:**
- If function uses `BaseParams`, only inline `BaseParams` fields
- If function uses `ExtendedParams`, inline all fields (inherited + own)
- Warn if the class has subclasses (deletion would break them)

### 5. Default Values

Parameter object fields have defaults:

```python
@dataclass
class UserParams:
    first_name: str
    last_name: str
    age: int = 0
    active: bool = True
```

**Behavior:**
- Preserve defaults in the new function signature:
  ```python
  def create_user(first_name: str, last_name: str, age: int = 0, active: bool = True) -> None:
  ```
- Handle `field(default=...)` and `field(default_factory=...)` appropriately

### 6. Type Annotations

Preserve type annotations from the parameter object:

```python
@dataclass
class UserParams:
    name: str
    age: int
    tags: list[str]
    metadata: dict[str, Any] | None = None
```

**After:**
```python
def process(name: str, age: int, tags: list[str], metadata: dict[str, Any] | None = None) -> None:
```

### 7. *args and **kwargs in Original Function

```python
def create_user(params: UserParams, *args, **kwargs) -> None:
```

**Behavior:**
- Insert inlined parameters before `*args`
- Preserve `*args` and `**kwargs` in their positions

### 8. Self/Cls Parameter in Methods

```python
class UserService:
    def create_user(self, params: UserParams) -> User:
```

**Behavior:**
- Keep `self`/`cls` as first parameter
- Insert inlined parameters after `self`/`cls`

### 9. Parameter Object Used Multiple Times in Signature

```python
def compare_users(user1: UserParams, user2: UserParams) -> bool:
```

**Behavior:**
- Prefix field names to avoid collision: `user1_first_name`, `user2_first_name`
- Or use a naming dialog to let user choose prefixes
- Update body references accordingly

### 10. Decorated Functions

```python
@validate_input
@log_calls
def create_user(params: UserParams) -> User:
```

**Behavior:**
- Preserve all decorators
- Only modify the function signature and body

### 11. Async Functions

```python
async def create_user(params: UserParams) -> User:
```

**Behavior:**
- Preserve `async` keyword
- Transform normally

### 12. Property Getters/Setters

```python
@property
def user_params(self) -> UserParams:
    return self._params
```

**Behavior:**
- Properties returning parameter objects are NOT candidates for inlining
- Only function/method parameters are candidates

### 13. Call Site with Spread Operator

```python
data = {"first_name": "John", "last_name": "Doe"}
create_user(UserParams(**data))
```

**Behavior:**
- Convert to: `create_user(**data)` if all fields match
- Or warn that manual intervention may be needed

### 14. Imports Cleanup

After inlining and potential class deletion:
- Remove unused imports of the parameter object class
- Remove unused `from dataclasses import dataclass` if no dataclasses remain

---

## Test Cases

### Basic Functionality

#### TC-001: Simple dataclass parameter object
```python
# Before
@dataclass
class UserParams:
    name: str
    age: int

def greet(params: UserParams) -> str:
    return f"Hello, {params.name}!"

greet(UserParams("Alice", 30))

# After
def greet(name: str, age: int) -> str:
    return f"Hello, {name}!"

greet("Alice", 30)
```

#### TC-002: Parameter object with defaults
```python
# Before
@dataclass
class Config:
    host: str = "localhost"
    port: int = 8080

def connect(config: Config) -> None:
    print(f"{config.host}:{config.port}")

connect(Config())

# After
def connect(host: str = "localhost", port: int = 8080) -> None:
    print(f"{host}:{port}")

connect()
```

#### TC-003: Method with self parameter
```python
# Before
class Service:
    def process(self, params: TaskParams) -> None:
        print(params.task_id)

# After
class Service:
    def process(self, task_id: str, priority: int) -> None:
        print(task_id)
```

### Trigger Conditions

#### TC-010: Trigger from function signature
- Caret on `params` in `def foo(params: MyParams)`
- Intention should be available

#### TC-011: Trigger from parameter type
- Caret on `MyParams` in `def foo(params: MyParams)`
- Intention should be available

#### TC-012: Trigger from call site instantiation
- Caret on `MyParams(...)` in `foo(MyParams(a=1, b=2))`
- Intention should be available

#### TC-013: Not available for non-parameter-object types
- Caret on `params` in `def foo(params: str)`
- Intention should NOT be available

#### TC-014: Not available for regular class (not dataclass/etc)
- Caret on `params` in `def foo(params: RegularClass)`
- Intention should NOT be available

### Scope Options

#### TC-020: Single usage conversion
```python
# Before
def foo(params: P) -> None: ...
foo(P(1, 2))  # <- trigger here
foo(P(3, 4))

# After (single usage)
def foo(params: P) -> None: ...  # unchanged
foo(1, 2)  # converted - BUT THIS IS INVALID without signature change
foo(P(3, 4))  # unchanged
```
**Note:** Single usage from call site requires special handling - may need to extract to variables or warn user.

#### TC-021: All usages conversion
```python
# Before
def foo(params: P) -> None:
    print(params.x)

foo(P(1, 2))
foo(P(3, 4))

# After
def foo(x: int, y: int) -> None:
    print(x)

foo(1, 2)
foo(3, 4)
```

### Class Deletion

#### TC-030: Delete unused class
```python
# Before
@dataclass
class OnlyUsedHere:
    x: int

def foo(params: OnlyUsedHere) -> None:
    print(params.x)

# After (with deletion)
def foo(x: int) -> None:
    print(x)
# Class OnlyUsedHere is deleted
```

#### TC-031: Keep class with remaining usages
```python
# Before
@dataclass
class SharedParams:
    x: int

def foo(params: SharedParams) -> None: ...
def bar(params: SharedParams) -> None: ...  # Still uses it

# After (inline foo only)
def foo(x: int) -> None: ...
def bar(params: SharedParams) -> None: ...  # Unchanged
# Class SharedParams is kept
```

#### TC-032: Keep class used as type annotation
```python
# Before
@dataclass
class Params:
    x: int

def foo(params: Params) -> None: ...
result: Params = get_params()  # Used as type annotation

# After
def foo(x: int) -> None: ...
result: Params = get_params()  # Still valid
# Class Params is kept
```

### Edge Cases

#### TC-040: Partial field usage
```python
# Before
@dataclass
class BigParams:
    a: int
    b: int
    c: int
    d: int

def use_some(params: BigParams) -> int:
    return params.a + params.b  # c, d unused

# After (all fields)
def use_some(a: int, b: int, c: int, d: int) -> int:
    return a + b
```

#### TC-041: Nested parameter object
```python
# Before
@dataclass
class Inner:
    x: int

@dataclass
class Outer:
    inner: Inner
    y: int

def foo(params: Outer) -> None:
    print(params.inner.x, params.y)

# After
def foo(inner: Inner, y: int) -> None:
    print(inner.x, y)
# Inner is NOT inlined
```

#### TC-042: Multiple same-type parameters
```python
# Before
def compare(a: Point, b: Point) -> float:
    return ((a.x - b.x)**2 + (a.y - b.y)**2)**0.5

# After
def compare(a_x: int, a_y: int, b_x: int, b_y: int) -> float:
    return ((a_x - b_x)**2 + (a_y - b_y)**2)**0.5
```

#### TC-043: Inheritance - subclass fields
```python
# Before
@dataclass
class Base:
    id: int

@dataclass  
class Extended(Base):
    name: str

def foo(params: Extended) -> None:
    print(params.id, params.name)

# After
def foo(id: int, name: str) -> None:
    print(id, name)
```

#### TC-044: Default factory
```python
# Before
@dataclass
class WithFactory:
    items: list[str] = field(default_factory=list)

def foo(params: WithFactory) -> None:
    print(params.items)

# After - need to handle default_factory specially
def foo(items: list[str] | None = None) -> None:
    if items is None:
        items = []
    print(items)
```

#### TC-045: Variable reference at call site
```python
# Before
params = UserParams("John", 30)
process(params)

# After (Option A - inline)
process("John", 30)

# After (Option B - extract fields)
params = UserParams("John", 30)
process(params.name, params.age)
```

#### TC-046: Keyword arguments in instantiation
```python
# Before
foo(Params(b=2, a=1))  # Out of order

# After
foo(a=1, b=2)  # Preserve keyword style, normalize order
```

#### TC-047: Mixed positional and keyword
```python
# Before
foo(Params(1, b=2))

# After
foo(1, b=2)
```

#### TC-048: Async function
```python
# Before
async def fetch(params: QueryParams) -> Response:
    return await client.get(params.url)

# After
async def fetch(url: str, timeout: int) -> Response:
    return await client.get(url)
```

#### TC-049: Decorated function
```python
# Before
@retry(times=3)
@log_calls
def api_call(params: ApiParams) -> Response:
    return call(params.endpoint)

# After
@retry(times=3)
@log_calls
def api_call(endpoint: str, method: str) -> Response:
    return call(endpoint)
```

#### TC-050: With *args and **kwargs
```python
# Before
def flexible(params: BaseParams, *args, **kwargs) -> None:
    print(params.id, args, kwargs)

# After
def flexible(id: int, name: str, *args, **kwargs) -> None:
    print(id, args, kwargs)
```

### Different Parameter Object Types

#### TC-060: NamedTuple
```python
# Before
class Point(NamedTuple):
    x: int
    y: int

def distance(p: Point) -> float:
    return (p.x**2 + p.y**2)**0.5

# After
def distance(x: int, y: int) -> float:
    return (x**2 + y**2)**0.5
```

#### TC-061: TypedDict
```python
# Before
class Options(TypedDict):
    verbose: bool
    debug: bool

def configure(opts: Options) -> None:
    if opts["verbose"]:
        print("Verbose mode")

# After
def configure(verbose: bool, debug: bool) -> None:
    if verbose:
        print("Verbose mode")
```

#### TC-062: Pydantic BaseModel
```python
# Before
class UserModel(BaseModel):
    name: str
    email: str

def create(user: UserModel) -> None:
    print(user.name)

# After
def create(name: str, email: str) -> None:
    print(name)
```

#### TC-063: attrs class
```python
# Before
@define
class Item:
    id: int
    name: str

def process(item: Item) -> None:
    print(item.id)

# After
def process(id: int, name: str) -> None:
    print(id)
```

### Import Handling

#### TC-070: Remove unused dataclass import
```python
# Before
from dataclasses import dataclass

@dataclass
class Params:
    x: int

def foo(params: Params) -> None:
    print(params.x)

# After (with class deletion)
def foo(x: int) -> None:
    print(x)
# dataclass import removed
```

#### TC-071: Keep dataclass import if other dataclasses exist
```python
# Before
from dataclasses import dataclass

@dataclass
class Params:
    x: int

@dataclass
class OtherClass:
    y: int

def foo(params: Params) -> None: ...

# After
from dataclasses import dataclass  # Kept

@dataclass
class OtherClass:
    y: int

def foo(x: int) -> None: ...
```

### Error Handling

#### TC-080: Parameter object class not found
- Type annotation references non-existent class
- Show error message, do not proceed

#### TC-081: Circular reference in parameter object
- Parameter object references itself
- Handle gracefully, inline only direct fields

#### TC-082: Dynamic field access
```python
def foo(params: Params) -> None:
    field_name = "x"
    print(getattr(params, field_name))
```
- Warn user that dynamic access cannot be automatically converted
- Proceed with static field references only

---

## UI/UX Considerations

### Intention Text
- From signature: "Inline parameter object 'params: UserParams'"
- From call site: "Inline parameter object instantiation"

### Dialog Options
When "All usages" is selected, show a preview dialog with:
1. List of affected call sites
2. Preview of signature change
3. Checkbox: "Delete class if no longer used"
4. Checkbox: "Remove unused imports"

### Conflict Resolution
If field names conflict with existing parameters:
```python
def foo(x: int, params: Params) -> None:  # Params also has field 'x'
```
- Show dialog to rename conflicting fields
- Suggest: `params_x` or `x_from_params`

---

## Implementation Notes

### Detection of Parameter Object Types

Check if a class is a parameter object by looking for:
1. `@dataclass` decorator
2. Inheritance from `NamedTuple` or `typing.NamedTuple`
3. Inheritance from `TypedDict` or `typing.TypedDict`
4. Inheritance from `pydantic.BaseModel`
5. `@attr.s`, `@attrs.define`, `@define` decorators

### Field Extraction

For each type:
- **dataclass**: Use `dataclasses.fields()` equivalent in PSI
- **NamedTuple**: Parse `_fields` or class body annotations
- **TypedDict**: Parse `__annotations__` or class body
- **Pydantic**: Parse `model_fields` or class body annotations
- **attrs**: Parse `__attrs_attrs__` or class body

### Settings Integration

Add to `PluginSettingsState`:
```kotlin
var enableInlineParameterObjectIntention: Boolean = true
```

### File Structure

```
intention/parameterobject/inline/
├── InlineParameterObjectIntention.kt
├── InlineParameterObjectTarget.kt
├── InlineParameterObjectProcessor.kt
├── InlineParameterObjectDialog.kt
├── InlineParameterObjectSettings.kt
└── FieldExtractor.kt
```
