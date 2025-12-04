### Analysis: Uncovered Cases for Parameter Object Refactoring

Based on the current implementation and recent updates, several key gaps have been addressed. However, the following
cases remain **not yet covered** or require further improvement:

---

### Call Site Patterns Not Supported

| Case               | Current Behavior      | Notes                                                                                            |
|--------------------|-----------------------|--------------------------------------------------------------------------------------------------|
| **Star unpacking** | Not handled correctly | `create_user(*args)` where `args` maps to named parameters is not resolved to individual fields. |
| **Dynamic calls**  | Cannot handle         | `getattr(obj, 'method')(...)`                                                                    |

---

### Function/Method Types Not Supported

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **`@property`** | Not checked | Should be disabled |
| **`@overload` functions** | Not checked | Type stubs pattern |
| **Nested/local functions** | Not checked | Functions inside functions |
| **Lambda expressions** | Not applicable | No named parameters |
| **Functions in `.pyi` stub files** | Not checked | Should be disabled or handled specially |
| **Async functions** | Not tested | `async def` - may work but untested |

---

### Dataclass Generation Gaps

| Case                         | Current Behavior             | Notes                                                       |
|------------------------------|------------------------------|-------------------------------------------------------------|
| **Type annotations**         | `typing.Any` always imported | Import added even if not used (redundant)                   |
| **Complex type annotations** | Copied as-is                 | Forward references, generics may break if context changes   |
| **Alternative containers**   | Only `@dataclass`            | Could offer Pydantic `BaseModel`, `NamedTuple`, `TypedDict` |
| **Frozen/slots options**     | Not offered                  | `@dataclass(frozen=True, slots=True)`                       |
| **Field metadata**           | Not supported                | `field(default_factory=list)` patterns                      |

---

### UI/UX Features Not Implemented

| Feature                                   | Status             | Notes                                                             |
|-------------------------------------------|--------------------|-------------------------------------------------------------------|
| **Custom dataclass name**                 | Not offered        | Always auto-generated (though conflict resolution is implemented) |
| **Custom parameter object variable name** | Hardcoded `params` | No user choice                                                    |
| **Preview changes**                       | Not implemented    | Standard refactoring preview                                      |
| **Undo grouping**                         | Basic              | Could be improved                                                 |
| **Refactor menu integration**             | Intention only     | Not in `Refactor                                                  | ...` menu |

---

### Edge Cases & Robustness

| Case                        | Current Behavior      | Notes                                                                         |
|-----------------------------|-----------------------|-------------------------------------------------------------------------------|
| **Read-only files**         | Not checked           | Could fail silently                                                           |
| **Syntax errors in file**   | Not checked           | May crash or produce invalid code                                             |
| **Parameter name `params`** | Conflict possible     | If existing param named `params` (variable name conflict)                     |
| **Recursive functions**     | Not tested            | Self-calls should be updated                                                  |
| **Decorators on function**  | Not preserved/checked | May affect behavior (except `@classmethod`/`@staticmethod` which are handled) |
| **Docstrings**              | Not updated           | Parameter docs become stale                                                   |

---

### Multi-File Scenarios

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Re-exported functions** | Not handled | `from module import func` patterns |
| **Test files** | Treated same as production | May want different handling |

---

### Summary of Priority Gaps

**High Priority:**

1. **`typing.Any` import cleanup**: Avoid adding unused imports.
2. **Docstring updates**: Essential for maintaining code quality.
3. **Parameter name conflict**: Handle case where a parameter is already named `params`.

**Medium Priority:**

1. **Alternative container types**: Pydantic support would be valuable.
2. **Refactor menu integration**: Improve discoverability.
3. **Preview dialog**: Standard refactoring experience.

**Low Priority:**

1. **Star unpacking at call sites**: Rare for named parameters.
2. **Stub file handling**: specialized use case.
