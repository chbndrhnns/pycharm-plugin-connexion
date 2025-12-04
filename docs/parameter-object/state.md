### Analysis: Uncovered Cases for Parameter Object Refactoring

Based on my analysis of the current implementation (`PyIntroduceParameterObjectIntention.kt`, `PyIntroduceParameterObjectProcessor.kt`) and the documentation (`mwe-parameter-object.md`), here are the cases **not yet covered**:

---

### Parameter Types Not Supported

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Parameters with default values** | Refactoring disabled (`hasDefaultValue()` check) | Should support generating dataclass fields with `field(default=...)` |
| **`*args` / `**kwargs`** | Refactoring disabled | Could be preserved alongside the parameter object |
| **Keyword-only arguments** | Not explicitly handled | Parameters after `*` in signature |
| **Positional-only arguments** | Not handled | Python 3.8+ `def f(a, /, b)` syntax |
| **`cls` parameter** | Not filtered (only `self` is) | `@classmethod` methods would fail |

---

### Call Site Patterns Not Supported

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Keyword arguments** | Skipped (args count mismatch) | `create_user(first_name="John", ...)` |
| **Mixed positional/keyword** | Skipped | Common in real code |
| **Star unpacking** | Not handled | `create_user(*args)` or `create_user(**data)` |
| **Partial arguments (using defaults)** | Skipped | When fewer args than params |
| **Call sites in other files** | Partially supported | Search is project-wide but import of dataclass not added to other files |

---

### Function/Method Types Not Supported

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **`@staticmethod`** | Not checked | Would incorrectly process |
| **`@classmethod`** | Not checked | `cls` not filtered like `self` |
| **`@property`** | Not checked | Should be disabled |
| **`@overload` functions** | Not checked | Type stubs pattern |
| **Nested/local functions** | Not checked | Functions inside functions |
| **Lambda expressions** | Not applicable | No named parameters |
| **Functions in `.pyi` stub files** | Not checked | Should be disabled or handled specially |
| **Async functions** | Not tested | `async def` - may work but untested |

---

### Dataclass Generation Gaps

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Type annotations** | Uses `Any` if missing | Works but could infer types |
| **Complex type annotations** | Copied as-is | Forward references, generics may break |
| **Dataclass name conflicts** | No uniqueness check | Could clash with existing symbols |
| **Alternative containers** | Only `@dataclass` | Could offer Pydantic `BaseModel`, `NamedTuple`, `TypedDict` |
| **Frozen/slots options** | Not offered | `@dataclass(frozen=True, slots=True)` |
| **Field metadata** | Not supported | `field(default_factory=list)` patterns |

---

### Import Handling Gaps

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Existing `dataclass` import** | Naive text check | `file.text.contains(...)` is fragile |
| **Import style** | Always `from X import Y` | Doesn't respect project style |
| **Duplicate imports** | May add duplicates | No proper deduplication |
| **Cross-file imports** | Not added | Call sites in other files won't import the new dataclass |
| **`typing.Any` import** | Always added | Even when not needed (if all params have annotations) |

---

### UI/UX Features Not Implemented

| Feature | Status | Notes |
|---------|--------|-------|
| **Parameter selection dialog** | Not implemented | Currently selects ALL parameters |
| **Custom dataclass name** | Not offered | Always auto-generated |
| **Custom parameter object variable name** | Hardcoded `params` | No user choice |
| **Preview changes** | Not implemented | Standard refactoring preview |
| **Undo grouping** | Basic | Could be improved |
| **Refactor menu integration** | Intention only | Not in `Refactor | ...` menu |

---

### Edge Cases & Robustness

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Single parameter** | Disabled (< 2 params) | Correct behavior |
| **Read-only files** | Not checked | Could fail silently |
| **Syntax errors in file** | Not checked | May crash or produce invalid code |
| **Parameter name `params`** | Conflict possible | If existing param named `params` |
| **Recursive functions** | Not tested | Self-calls should be updated |
| **Decorators on function** | Not preserved/checked | May affect behavior |
| **Docstrings** | Not updated | Parameter docs become stale |

---

### Multi-File Scenarios

| Case | Current Behavior | Notes |
|------|------------------|-------|
| **Call sites in other modules** | Arguments updated, but... | Missing import for the new dataclass |
| **Re-exported functions** | Not handled | `from module import func` patterns |
| **Dynamic calls** | Cannot handle | `getattr(obj, 'method')(...)` |
| **Test files** | Treated same as production | May want different handling |

---

### Summary of Priority Gaps

**High Priority (Common real-world cases):**
1. Parameters with default values
2. Keyword arguments at call sites
3. Cross-file dataclass imports
4. `@classmethod` / `@staticmethod` handling
5. Parameter selection UI

**Medium Priority (Nice to have):**
1. Alternative container types (Pydantic, NamedTuple)
2. Proper import deduplication
3. Dataclass name conflict resolution
4. Preview dialog

**Low Priority (Edge cases):**
1. Positional-only parameters
2. Star unpacking at call sites
3. Docstring updates
4. Stub file handling