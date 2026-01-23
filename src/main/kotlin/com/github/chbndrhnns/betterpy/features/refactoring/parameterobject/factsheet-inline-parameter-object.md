# Inline Parameter Object

## What It Does

Reverses the "Introduce Parameter Object" refactoring by expanding a parameter object back into individual parameters. Replaces the parameter object with its fields in the function signature and body, and updates all call sites to pass field values directly.

```python
# Before
from dataclasses import dataclass

@dataclass
class CreateUserParams:
    first_name: str
    last_name: str
    age: int = 18

def create_user(params: CreateUserParams):
    print(params.first_name, params.last_name, params.age)

create_user(CreateUserParams(first_name="John", last_name="Doe", age=30))

# After
def create_user(first_name: str, last_name: str, age: int = 18):
    print(first_name, last_name, age)

create_user(first_name="John", last_name="Doe", age=30)
```

## Features

### Supported Parameter Object Types

Works with any parameter object created by "Introduce Parameter Object":
- dataclass
- NamedTuple
- TypedDict (uses subscription syntax `params["field"]`)
- pydantic.BaseModel

### Call Site Handling

Handles multiple call patterns:
- Direct constructor calls: `func(MyParams(a=1, b=2))` → `func(a=1, b=2)`
- Variable arguments: `func(params)` where `params = MyParams(a=1, b=2)` → `func(a=1, b=2)`
- Positional constructor calls: `MyParams(1, 2)` → `func(1, 2)`
- Keyword constructor calls: `MyParams(a=1, b=2)` → `func(a=1, b=2)`

### Class Removal

Optionally removes the parameter object class after inlining if:
- All occurrences are inlined
- No other usages remain (variable annotations, other functions, etc.)

### Multiple Occurrences

Option to inline:
- All occurrences (default): Updates all functions using this parameter object
- This occurrence only: Updates just the function under cursor (keeps class)

## How to Invoke

Place cursor on:
- The function name
- The parameter name
- The parameter type annotation (anywhere in the class name)

Access via:
- Right-click → Refactor → Inline Parameter Object
- Cmd/Ctrl+Shift+A → "Inline Parameter Object"

**Not available for:**
- Functions without a parameter object
- Parameters that don't resolve to a valid parameter object class

## Dialog Options

- **Inline all occurrences (N)**: Updates all functions using this parameter object (default)
- **Inline this occurrence only**: Updates only the function under cursor
- **Remove the parameter object class**: Deletes the class if no usages remain (enabled only for "Inline all")

## Constraints & Limitations

### Requirements

- Parameter must have a type annotation
- Type annotation must resolve to a class (dataclass, NamedTuple, TypedDict, or pydantic.BaseModel)
- Parameter object class must have at least one field

### Field Extraction

Extracts fields from class body statements:
- For dataclass/NamedTuple/Pydantic: Annotated attributes and assignments
- For TypedDict: Annotated attributes
- Preserves type annotations and default values
- Skips `model_config` (Pydantic configuration)

### Body Updates

- Replaces attribute access: `params.field` → `field`
- For TypedDict: Replaces subscription `params["field"]` → `field`
- Only replaces references that resolve to the parameter being inlined

### Known Limitations

- Variable arguments: If a call passes a variable containing the parameter object, the variable assignment is not removed
- Does not update type hints in other locations (function return types, variable annotations)
- Pydantic `model_config` attribute is excluded from fields
