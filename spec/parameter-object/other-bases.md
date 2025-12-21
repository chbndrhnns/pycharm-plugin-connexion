# Alternative Base Types for Parameter Objects

## Overview

This feature extends the "Introduce Parameter Object" refactoring to support multiple base types beyond the default `@dataclass`. Users can choose from:

- **dataclass** (default) - Standard Python dataclass from `dataclasses` module
- **NamedTuple** - Immutable tuple subclass from `typing` module
- **TypedDict** - Dictionary with typed keys from `typing` module
- **pydantic.BaseModel** - Pydantic model for validation and serialization

## Configuration

### Global Default Setting

A new setting `defaultParameterObjectBaseType` is added to `PluginSettingsState` to configure the default base type for all parameter object introductions.

Location: **Settings > Tools > Python DDD Toolkit > Parameter Object > Default Base Type**

### Per-Invocation Override

The `IntroduceParameterObjectDialog` includes a dropdown to override the default base type for individual refactoring operations.

## Base Type Implementations

### 1. dataclass (Default)

```python
from dataclasses import dataclass

@dataclass(frozen=True, slots=True, kw_only=True)
class CreateUserParams:
    first_name: str
    last_name: str
    email: str
    age: int
```

**Features:**
- Supports `frozen`, `slots`, and `kw_only` options
- Automatic `__init__`, `__repr__`, `__eq__` generation
- Full attribute access via dot notation

**Import:** `from dataclasses import dataclass`

### 2. NamedTuple

```python
from typing import NamedTuple

class CreateUserParams(NamedTuple):
    first_name: str
    last_name: str
    email: str
    age: int
```

**Features:**
- Immutable by design (no `frozen` option needed)
- Tuple unpacking support
- Memory efficient
- Full attribute access via dot notation

**Import:** `from typing import NamedTuple`

**Limitations:**
- No `frozen` option (always immutable)
- No `slots` option (uses tuple storage)
- No `kw_only` option (positional arguments supported)
- Default values must be at the end (like function parameters)

### 3. TypedDict

```python
from typing import TypedDict

class CreateUserParams(TypedDict):
    first_name: str
    last_name: str
    email: str
    age: int
```

**Features:**
- Dictionary-like access with type hints
- Compatible with JSON serialization
- Supports `total=False` for optional keys

**Import:** `from typing import TypedDict`

**Limitations:**
- No `frozen` option (dictionaries are mutable)
- No `slots` option (uses dict storage)
- No `kw_only` option (not applicable)
- **Access via bracket notation** (`params["first_name"]`) not dot notation
- Body rewrites must use `params["field"]` instead of `params.field`
- No automatic `__init__` - instantiated as dict literal: `CreateUserParams(first_name="John", ...)`

### 4. pydantic.BaseModel

```python
from pydantic import BaseModel

class CreateUserParams(BaseModel):
    first_name: str
    last_name: str
    email: str
    age: int

    class Config:
        frozen = True
```

**Features:**
- Runtime validation
- JSON serialization/deserialization
- Rich configuration options
- Full attribute access via dot notation

**Import:** `from pydantic import BaseModel`

**Limitations:**
- **Requires external dependency** (`pydantic` package must be installed)
- `slots` option requires Pydantic v2+ with `model_config`
- `kw_only` not directly supported (all fields are keyword-only by default)
- Slightly more verbose for frozen configuration

## Implementation Details

### ParameterObjectBaseType Enum

```kotlin
enum class ParameterObjectBaseType(val displayName: String) {
    DATACLASS("dataclass"),
    NAMED_TUPLE("NamedTuple"),
    TYPED_DICT("TypedDict"),
    PYDANTIC_BASE_MODEL("pydantic.BaseModel")
}
```

### Generator Strategy Pattern

The `createDataclass()` method is refactored into a strategy pattern with separate generators:

- `DataclassGenerator` - Generates `@dataclass` decorated classes
- `NamedTupleGenerator` - Generates `NamedTuple` subclasses
- `TypedDictGenerator` - Generates `TypedDict` subclasses
- `PydanticGenerator` - Generates `pydantic.BaseModel` subclasses

Each generator implements:
- `generateClass(className, params, options)` - Creates the class definition
- `getRequiredImports()` - Returns the imports needed for this base type

### Import Handling

The `addDataclassImport()` method is updated to `addRequiredImports()` which adds appropriate imports based on the selected base type:

| Base Type | Imports |
|-----------|---------|
| dataclass | `from dataclasses import dataclass`, `from typing import Any` |
| NamedTuple | `from typing import NamedTuple, Any` |
| TypedDict | `from typing import TypedDict, Any` |
| pydantic.BaseModel | `from pydantic import BaseModel`, `from typing import Any` |

### Options Applicability Matrix

| Option | dataclass | NamedTuple | TypedDict | pydantic.BaseModel |
|--------|-----------|------------|-----------|-------------------|
| frozen | ✓ | N/A (always) | N/A | ✓ (via Config) |
| slots | ✓ | N/A | N/A | ✓ (v2+ only) |
| kw_only | ✓ | N/A | N/A | N/A (default) |

When options are not applicable, they are ignored and the corresponding UI elements are disabled.

## Edge Cases

### Optional Fields with Default Values

```python
# dataclass
@dataclass
class Params:
    required: str
    optional: str = "default"

# NamedTuple
class Params(NamedTuple):
    required: str
    optional: str = "default"

# TypedDict (using total=False or NotRequired)
class Params(TypedDict, total=False):
    required: str  # Still optional due to total=False
    optional: str

# pydantic
class Params(BaseModel):
    required: str
    optional: str = "default"
```

### Type Annotations

All base types preserve the original type annotations from the function parameters.

### Call Site Updates

Call sites are updated to use the appropriate instantiation syntax:

```python
# dataclass, NamedTuple, pydantic
func(CreateUserParams(first_name="John", last_name="Doe", ...))

# TypedDict
func(CreateUserParams(first_name="John", last_name="Doe", ...))  # Same syntax, dict-like
```

## Testing

Tests cover:
1. Generation of each base type with various parameter configurations
2. Correct import statements for each base type
3. Options handling (enabled/disabled based on base type)
4. Default values and optional fields
5. Call site updates for each base type
6. Body rewrites (especially TypedDict bracket notation)
7. Global setting persistence
8. Per-invocation override behavior
