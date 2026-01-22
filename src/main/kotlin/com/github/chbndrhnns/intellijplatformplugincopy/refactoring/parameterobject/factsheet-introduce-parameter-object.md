# Introduce Parameter Object

## What It Does

Transforms multiple function parameters into a single parameter object. Creates a new class (dataclass, NamedTuple, TypedDict, or Pydantic BaseModel), updates the function signature and body, and updates all call sites across the project.

```python
# Before
def create_user(first_name: str, last_name: str, age: int = 18):
    print(first_name, last_name, age)

create_user("John", "Doe", 30)

# After
from dataclasses import dataclass

@dataclass(frozen=True, slots=True, kw_only=True)
class CreateUserParams:
    first_name: str
    last_name: str
    age: int = 18

def create_user(params: CreateUserParams):
    print(params.first_name, params.last_name, params.age)

create_user(CreateUserParams(first_name="John", last_name="Doe", age=30))
```

## Features

### Base Types

| Type | Options | Notes |
|------|---------|-------|
| dataclass (default) | frozen, slots, kw_only | |
| NamedTuple | none | Immutable |
| TypedDict | none | Uses `NotRequired` for optional fields, dict access syntax |
| pydantic.BaseModel | frozen | Uses `model_config` |

### Supported Function Types

- Top-level functions and methods (instance, class, static)
- Async functions
- Nested functions (creates parameter object in appropriate scope)
- Property setters
- Overloaded functions

### Inheritance

Updates root method and all overriding methods. Handles `super()` calls by wrapping parameters:

```python
super().foo(FooParams(a=params.a, b=params.b))
```

### Cross-File Updates

Automatically updates all call sites and adds imports to files that use the function.

### Variadic Parameters

Preserves `*args`, `**kwargs`, `*`, and `/` in signatures. Excludes them from parameter object.

## How to Invoke

Place cursor on function name, parameter name, parameter type annotation, or return type annotation. Available from call sites too.

Access via:
- Right-click → Refactor → Introduce Parameter Object
- Cmd/Ctrl+Shift+A → "Introduce Parameter Object"

**Not available for:**
- Stub files (`.pyi`)
- Pytest test functions or fixtures
- Functions with no eligible parameters

## Dialog Options

- **Class Name**: Default is function name in CamelCase + "Params" suffix
- **Parameter Name**: Default is `params`
- **Base Type**: Select from dropdown (default from plugin settings)
- **Parameters**: Checkboxes to select which parameters to include
- **Options**: frozen, slots, kw_only (varies by base type)

## Constraints & Limitations

### Validation

- Class name must be valid Python identifier, no conflicts with existing classes
- Parameter name must be valid identifier
- At least one parameter must be selected
- Cannot select `self`, `cls`, `*args`, or `**kwargs`

### Scope Rules

- Class methods: parameter object created at module level
- Nested functions: parameter object created in parent function scope
- Top-level functions: parameter object created at module level

### Known Limitations

- Docstrings and comments not updated with new parameter names
- Dynamic calls using `*args`/`**kwargs` unpacking may not update correctly
- String references to parameter names not updated
