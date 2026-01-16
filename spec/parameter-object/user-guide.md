# Parameter Object Refactoring - User Guide

## Overview

The **Parameter Object** refactoring is a powerful feature that helps you manage functions with multiple parameters by grouping them into a single, cohesive object. This refactoring is particularly useful in Domain-Driven Design (DDD) contexts where you want to create explicit value objects or data transfer objects (DTOs).

### Benefits

- **Reduces parameter clutter**: Functions with many parameters become cleaner and more maintainable
- **Improves code organization**: Related parameters are grouped together logically
- **Enhances type safety**: Parameter objects provide stronger typing and validation
- **Facilitates evolution**: Adding new parameters becomes easier without changing function signatures everywhere
- **Supports DDD patterns**: Naturally creates value objects and command objects

## Features

This plugin provides two complementary refactorings:

1. **Introduce Parameter Object**: Converts multiple function parameters into a single parameter object
2. **Inline Parameter Object**: Reverses the operation, expanding a parameter object back into individual parameters

## Introduce Parameter Object

### How to Use

There are multiple ways to invoke this refactoring:

1. **From the function definition**:
   - Place your caret on the function name or anywhere in the parameter list
   - Press `Alt+Enter` (or `Option+Return` on macOS)
   - Select "Introduce Parameter Object"

2. **From a call site**:
   - Place your caret on a function call
   - Press `Alt+Enter`
   - Select "Introduce Parameter Object"

3. **Via the Refactor menu**:
   - Place your caret on the function name
   - Press `Ctrl+T` (or `Ctrl+Alt+Shift+T`)
   - Select "Introduce Parameter Object"

### Configuration Dialog

When you invoke the refactoring, a dialog appears with the following options:

#### Parameter Selection
- **Checkbox list**: Select which parameters to include in the parameter object
- You can choose all parameters or just a subset

#### Class Configuration
- **Class Name**: The name of the new parameter object class (auto-generated based on function name)
- **Parameter Name**: The name of the parameter in the function signature (default: `params`)

#### Base Type
Choose from four different base types for your parameter object:

1. **dataclass** (default)
   - Python's built-in dataclass decorator
   - Supports frozen, slots, and kw_only options
   - Best for general-purpose parameter objects

2. **NamedTuple**
   - Immutable tuple with named fields
   - Lightweight and memory-efficient
   - No configuration options (always immutable)

3. **TypedDict**
   - Dictionary with typed keys
   - Useful when you need dict-like access
   - Parameters accessed via dictionary syntax (`params["name"]`)

4. **pydantic.BaseModel**
   - Pydantic's BaseModel for validation
   - Supports frozen option
   - Best when you need runtime validation

#### Options (availability depends on base type)

- **Frozen**: Makes the dataclass immutable (dataclass and Pydantic only)
- **Slots**: Adds `__slots__` for memory optimization (dataclass only)
- **kw_only**: Requires keyword arguments when instantiating (dataclass only)

### Examples

#### Example 1: Basic Dataclass

**Before:**
```python
def create_user(first_name: str, last_name: str, email: str, age: int):
    print(f"Creating user: {first_name} {last_name}")
    # ... implementation

# Call site
create_user("John", "Doe", "john@example.com", 30)
```

**After:**
```python
from dataclasses import dataclass
from typing import Any

@dataclass(frozen=True, slots=True, kw_only=True)
class CreateUserParams:
    first_name: str
    last_name: str
    email: str
    age: int

def create_user(params: CreateUserParams):
    print(f"Creating user: {params.first_name} {params.last_name}")
    # ... implementation

# Call site
create_user(CreateUserParams(
    first_name="John",
    last_name="Doe",
    email="john@example.com",
    age=30
))
```

#### Example 2: NamedTuple

**Before:**
```python
def calculate_price(base_price: float, tax_rate: float, discount: float):
    return base_price * (1 + tax_rate) - discount

result = calculate_price(100.0, 0.2, 10.0)
```

**After:**
```python
from typing import NamedTuple

class CalculatePriceParams(NamedTuple):
    base_price: float
    tax_rate: float
    discount: float

def calculate_price(params: CalculatePriceParams):
    return params.base_price * (1 + params.tax_rate) - params.discount

result = calculate_price(CalculatePriceParams(
    base_price=100.0,
    tax_rate=0.2,
    discount=10.0
))
```

#### Example 3: TypedDict

**Before:**
```python
def process_order(order_id: int, customer_id: int, total: float):
    print(f"Processing order {order_id}")
    # ... implementation

process_order(123, 456, 99.99)
```

**After:**
```python
from typing import TypedDict

class ProcessOrderParams(TypedDict):
    order_id: int
    customer_id: int
    total: float

def process_order(params: ProcessOrderParams):
    print(f"Processing order {params['order_id']}")
    # ... implementation

process_order(ProcessOrderParams(
    order_id=123,
    customer_id=456,
    total=99.99
))
```

#### Example 4: Pydantic BaseModel

**Before:**
```python
def register_user(username: str, email: str, password: str):
    # ... validation and registration logic
    pass

register_user("johndoe", "john@example.com", "secret123")
```

**After:**
```python
from pydantic import BaseModel

class RegisterUserParams(BaseModel):
    username: str
    email: str
    password: str

def register_user(params: RegisterUserParams):
    # ... validation and registration logic
    pass

register_user(RegisterUserParams(
    username="johndoe",
    email="john@example.com",
    password="secret123"
))
```

#### Example 5: Method with Self Parameter

The refactoring works with class methods too:

**Before:**
```python
class OrderService:
    def create_order(self, customer_id: int, product_id: int, quantity: int):
        # ... implementation
        pass
```

**After:**
```python
from dataclasses import dataclass

@dataclass(frozen=True, slots=True, kw_only=True)
class CreateOrderParams:
    customer_id: int
    product_id: int
    quantity: int

class OrderService:
    def create_order(self, params: CreateOrderParams):
        # ... implementation
        pass
```

#### Example 6: Partial Parameter Selection

You can choose to refactor only some parameters:

**Before:**
```python
def send_email(recipient: str, subject: str, body: str, priority: int, retry_count: int):
    # ... implementation
    pass
```

**After (selecting only subject, body, priority):**
```python
from dataclasses import dataclass

@dataclass(frozen=True, slots=True, kw_only=True)
class SendEmailParams:
    subject: str
    body: str
    priority: int

def send_email(recipient: str, params: SendEmailParams, retry_count: int):
    # ... implementation
    pass
```

## Inline Parameter Object

The **Inline Parameter Object** refactoring reverses the "Introduce Parameter Object" operation. It takes a function that uses a parameter object and expands it back into individual parameters.

### How to Use

1. Place your caret on:
   - The function name, OR
   - The parameter name in the function signature, OR
   - The type annotation (the parameter object class name)

2. Press `Alt+Enter` (or `Option+Return` on macOS)

3. Select "Inline Parameter Object"

### What It Does

The refactoring will:
- Extract all fields from the parameter object class
- Replace the single parameter with individual parameters in the function signature
- Update all call sites to pass individual arguments instead of creating the parameter object
- Preserve type annotations from the parameter object fields
- Update all usages within the function body

### Examples

#### Example 1: Inline Dataclass

**Before:**
```python
from dataclasses import dataclass

@dataclass(frozen=True, slots=True, kw_only=True)
class CreateUserParams:
    first_name: str
    last_name: str
    email: str
    age: int

def create_user(params: CreateUserParams):
    print(params.first_name, params.last_name, params.email, params.age)

def main():
    create_user(CreateUserParams(
        first_name="John",
        last_name="Doe",
        email="john@example.com",
        age=30
    ))
```

**After:**
```python
from dataclasses import dataclass

def create_user(first_name: str, last_name: str, email: str, age: int):
    print(first_name, last_name, email, age)

def main():
    create_user(
        first_name="John",
        last_name="Doe",
        email="john@example.com",
        age=30
    )
```

#### Example 2: Inline NamedTuple

**Before:**
```python
from typing import NamedTuple

class CalculatePriceParams(NamedTuple):
    base_price: float
    tax_rate: float
    discount: float

def calculate_price(params: CalculatePriceParams):
    return params.base_price * (1 + params.tax_rate) - params.discount

result = calculate_price(CalculatePriceParams(
    base_price=100.0,
    tax_rate=0.2,
    discount=10.0
))
```

**After:**
```python
from typing import NamedTuple

def calculate_price(base_price: float, tax_rate: float, discount: float):
    return base_price * (1 + tax_rate) - discount

result = calculate_price(
    base_price=100.0,
    tax_rate=0.2,
    discount=10.0
)
```

#### Example 3: Inline TypedDict

**Before:**
```python
from typing import TypedDict

class ProcessOrderParams(TypedDict):
    order_id: int
    customer_id: int
    total: float

def process_order(params: ProcessOrderParams):
    print(f"Processing order {params['order_id']}")
    return params['total']

process_order(ProcessOrderParams(
    order_id=123,
    customer_id=456,
    total=99.99
))
```

**After:**
```python
from typing import TypedDict

def process_order(order_id: int, customer_id: int, total: float):
    print(f"Processing order {order_id}")
    return total

process_order(
    order_id=123,
    customer_id=456,
    total=99.99
)
```

#### Example 4: Inline Pydantic BaseModel

**Before:**
```python
from pydantic import BaseModel

class RegisterUserParams(BaseModel):
    username: str
    email: str
    password: str

def register_user(params: RegisterUserParams):
    print(f"Registering {params.username}")

register_user(RegisterUserParams(
    username="johndoe",
    email="john@example.com",
    password="secret123"
))
```

**After:**
```python
from pydantic import BaseModel

def register_user(username: str, email: str, password: str):
    print(f"Registering {username}")

register_user(
    username="johndoe",
    email="john@example.com",
    password="secret123"
)
```

## Workflows

### Workflow 1: Refactoring Legacy Code

When working with legacy code that has functions with many parameters:

1. **Identify** functions with 4+ parameters that are logically related
2. **Invoke** "Introduce Parameter Object" on the function
3. **Select** all related parameters in the dialog
4. **Choose** dataclass as the base type (most versatile)
5. **Enable** frozen=True for immutability (recommended for DDD)
6. **Review** the generated code and all updated call sites
7. **Add validation** or business logic to the parameter object class if needed

### Workflow 2: Creating Command Objects (DDD)

For Domain-Driven Design command patterns:

1. **Start** with a function that represents a command (e.g., `create_order`, `update_user`)
2. **Apply** "Introduce Parameter Object"
3. **Name** the class with a "Command" suffix (e.g., `CreateOrderCommand`)
4. **Choose** dataclass or Pydantic BaseModel
5. **Enable** frozen=True to make it immutable
6. **Move** the command class to a dedicated `commands.py` module
7. **Add** validation logic or business rules to the command class

### Workflow 3: API Request/Response Objects

When building APIs:

1. **Define** handler functions with multiple parameters
2. **Apply** "Introduce Parameter Object"
3. **Choose** Pydantic BaseModel for automatic validation
4. **Name** classes with "Request" or "Response" suffix
5. **Add** Pydantic validators and field constraints
6. **Use** the parameter objects as FastAPI/Flask request models

### Workflow 4: Experimenting with Different Types

If you're unsure which base type to use:

1. **Start** with dataclass (most flexible)
2. **Test** the refactoring
3. If you need immutability and simplicity, **inline** and re-introduce as NamedTuple
4. If you need validation, **inline** and re-introduce as Pydantic BaseModel
5. If you need dict-like access, **inline** and re-introduce as TypedDict

### Workflow 5: Gradual Refactoring

For large codebases:

1. **Enable** the parameter object feature in plugin settings
2. **Start** with the most problematic functions (most parameters)
3. **Apply** the refactoring one function at a time
4. **Run tests** after each refactoring
5. **Commit** each refactoring separately for easy rollback
6. **Use** "Inline Parameter Object" if you need to revert

## Tips and Best Practices

### Naming Conventions

- **Command objects**: Use `<Verb><Noun>Command` (e.g., `CreateOrderCommand`)
- **Query objects**: Use `<Noun>Query` (e.g., `UserQuery`)
- **General params**: Use `<FunctionName>Params` (e.g., `CreateUserParams`)
- **Request/Response**: Use `<Operation>Request/Response` (e.g., `LoginRequest`)

### When to Use Each Base Type

- **dataclass**: Default choice, most flexible, good for general use
- **NamedTuple**: When you need immutability and don't need to add methods
- **TypedDict**: When interfacing with dict-based APIs or JSON
- **Pydantic BaseModel**: When you need validation, serialization, or API integration

### Parameter Selection

- **Group related parameters**: Only combine parameters that are logically related
- **Keep independent parameters separate**: Don't force unrelated parameters into an object
- **Consider evolution**: Group parameters that are likely to change together

### Immutability

- **Enable frozen=True** for dataclasses when creating value objects or commands
- **Use NamedTuple** when you want guaranteed immutability
- **Pydantic frozen** for validated immutable objects

### Performance Considerations

- **Use slots=True** for dataclasses if you're creating many instances
- **NamedTuple** is more memory-efficient than dataclass
- **TypedDict** has no runtime overhead (it's just a type hint)

### Testing

After applying the refactoring:
1. **Run all tests** to ensure call sites are updated correctly
2. **Check type hints** are preserved
3. **Verify imports** are added correctly
4. **Review** any manual adjustments needed for complex cases

## Limitations and Known Issues

### Current Limitations

1. **Variadic parameters**: Functions with `*args` or `**kwargs` may require manual adjustment
2. **Decorators**: Some decorators may need manual updates after refactoring
3. **Dynamic calls**: Calls using `getattr()` or other dynamic invocation won't be updated automatically
4. **Cross-file references**: All call sites in the project are updated, but external callers (other projects) won't be

### Workarounds

- **For variadic parameters**: Manually adjust the parameter object or keep variadic parameters separate
- **For decorators**: Review and update decorator logic if it depends on parameter names
- **For dynamic calls**: Search for string references to the function name and update manually

## Settings

The parameter object refactoring can be configured in the plugin settings:

1. Go to **Settings/Preferences** → **Tools** → **Python DDD Toolkit**
2. Find the **Parameter Object** section
3. Configure:
   - **Enable Parameter Object Refactoring**: Toggle the feature on/off
   - **Default Base Type**: Choose the default base type for new parameter objects

## Keyboard Shortcuts

- **Invoke refactoring**: `Alt+Enter` (or `Option+Return` on macOS)
- **Refactor menu**: `Ctrl+T` or `Ctrl+Alt+Shift+T`
- **Navigate to parameter object class**: `Ctrl+B` or `Cmd+B` on the type annotation

## Troubleshooting

### Refactoring not available

**Problem**: The "Introduce Parameter Object" option doesn't appear in the menu.

**Solutions**:
- Ensure the feature is enabled in plugin settings
- Check that your caret is on a function name or parameter
- Verify the function has at least one parameter
- Make sure you're in a Python file

### Call sites not updated

**Problem**: Some call sites weren't updated after refactoring.

**Solutions**:
- Check if the calls use dynamic invocation (e.g., `getattr()`)
- Look for calls in generated code or external libraries
- Search for the function name manually and update remaining calls

### Type hints missing

**Problem**: The generated parameter object has `Any` type hints instead of specific types.

**Solutions**:
- Add type hints to the original function parameters before refactoring
- Manually update the parameter object class after refactoring
- Use Pydantic BaseModel for automatic type validation

### Import errors

**Problem**: Import statements are missing or incorrect after refactoring.

**Solutions**:
- Use the IDE's "Optimize Imports" feature (`Ctrl+Alt+O` or `Cmd+Option+O`)
- Manually add missing imports
- Check that the parameter object class is in the correct module

## FAQ

**Q: Can I refactor only some parameters?**  
A: Yes! In the dialog, uncheck the parameters you want to keep as individual parameters.

**Q: Will this work with async functions?**  
A: Yes, the refactoring works with both sync and async functions.

**Q: Can I use this with class methods?**  
A: Yes, it works with regular methods, class methods, and static methods. The `self` or `cls` parameter is automatically excluded.

**Q: What happens to default values?**  
A: Default values are preserved in the parameter object class definition.

**Q: Can I undo the refactoring?**  
A: Yes, use the "Inline Parameter Object" refactoring to reverse it, or use the IDE's undo feature (`Ctrl+Z` or `Cmd+Z`).

**Q: Does this work across multiple files?**  
A: Yes, all call sites across your entire project are automatically updated.

**Q: Can I customize the generated code?**  
A: After the refactoring, you can manually edit the parameter object class to add validation, methods, or other customizations.

**Q: What about type checking?**  
A: The refactoring preserves type hints, and tools like mypy will continue to work correctly.

## Related Features

- **Populate Arguments**: Automatically fills in missing arguments at call sites
- **Type Mismatch Helpers**: Wrap/unwrap expressions to match expected types
- **Change Visibility**: Modify function/class visibility modifiers

## Further Reading

- [Martin Fowler - Introduce Parameter Object](https://refactoring.com/catalog/introduceParameterObject.html)
- [Python dataclasses documentation](https://docs.python.org/3/library/dataclasses.html)
- [Pydantic documentation](https://docs.pydantic.dev/)
- [Domain-Driven Design patterns](https://martinfowler.com/tags/domain%20driven%20design.html)
