### Goal
Provide a Python intention that “upgrades” a value/shape that is currently represented as a `dict` into progressively more structured types:

1) `dict` literal / `dict[...]`-typed object → **`TypedDict`**
2) `TypedDict` → **`@dataclass`**
3) `@dataclass` → **`pydantic.BaseModel`**

The intention should infer fields and types from:
- the **dict literal itself** (keys + values), and/or
- **existing annotations** (e.g., `x: dict[str, Any]`, `x: dict[str, int]`, `x: Mapping[str, ...]`, `x: MyTypedDict`, etc.), and/or
- **usage context** (assignment target annotation, function parameter expected type, return type, `TypedDict` already referenced).

### Non-goals (initially)
To keep the first version shippable and testable:
- Don’t attempt whole-program inference across modules.
- Don’t attempt runtime dataflow inference (values changing across branches) beyond local heuristics.
- Don’t attempt inference for non-string keys for `TypedDict` (TypedDict keys are `str` literals).
- Don’t attempt full pydantic v1/v2 compatibility magic in v1; pick one default (recommend pydantic v2), and optionally add a setting later.

---

### UX proposal
#### Intention family
`Upgrade type representation` (or `Promote mapping to model`).

#### Contexts where intention is offered
Offer in at least these locations:
1) On a dict literal expression:
   - `payload = {"id": 1, "name": "x"}`
   - `foo({"id": 1})`
   - return expression: `return {"id": 1}`
2) On a variable/parameter annotation that is a dict-like:
   - `payload: dict[str, Any]`
3) On a `TypedDict` definition to convert to dataclass/BaseModel.
4) On a dataclass definition to convert to BaseModel.

#### Available actions (sub-intentions or one intention with chooser)
When invoked, present options based on what is valid:
- `Convert dict to TypedDict` (creates a new `TypedDict` + updates annotation / usage)
- `Convert TypedDict to dataclass`
- `Convert dataclass to Pydantic model`

If multiple targets are possible, show a small chooser dialog (or a popup list) to select the upgrade step.

#### Name + placement rules
- Proposed new type name derived from target symbol:
  - `payload` → `Payload`
  - `user_data` → `UserData`
  - fallback: `GeneratedModel` / `GeneratedTypedDict` with a unique suffix.
- Placement:
  - Default: insert new class/TypedDict **near the first usage**, preferably above the current function/class scope if appropriate.
  - If converting an inline literal passed as argument, insert near the containing function top or module top.

#### Import management
- TypedDict: `from typing import TypedDict` (or `typing_extensions` depending on language level; since tests run with Python 3.11, standard `typing.TypedDict` is fine).
- Dataclass: `from dataclasses import dataclass`
- Pydantic: `from pydantic import BaseModel` (and `Field` only when needed).

---

### Type inference strategy
Inference needs to be deterministic and consistent. A good MVP is a **two-phase approach**:

#### Phase 1: Extract a “shape”
Create an internal representation:

- `Field(name: str, type: PyType?, required: Boolean, defaultExpr: PyExpression?, doc: String?)`
- `Shape(fields: List[Field], isOpen: Boolean, problems: List[Problem])`

Rules:
- Only consider keys that are **string literals** (`"id"`, `'name'`).
- If keys are not literals (`k`, `"id" if cond else "x"`), record a problem and either:
  - do not offer intention, or
  - offer but generate `dict[str, Any]`/“open” model with `extra` (pydantic) + `NotRequired` fields disabled.
  
Recommend: **Don’t offer** for non-literal keys in v1.

Extract values:
- Literal values infer primitive types: `1 -> int`, `"x" -> str`, `True -> bool`, `None -> NoneType`.
- List literal: infer `list[T]` if homogeneous; else `list[Any]`.
- Dict literal nested: infer nested `TypedDict` / nested dataclass / nested BaseModel **only if** user chooses “recursive generation” (default on for TypedDict/dataclass; optional for pydantic).
- Call expressions: if resolvable to a type (constructor call), use that type; else `Any`.

Required vs optional:
- If dict literal appears in a context with known expected type (e.g., assigned to a `TypedDict`), follow that.
- Otherwise:
  - all keys present in the literal are `Required`.
  - if the dict is formed by `{**base, "x": 1}` or conditional merges, treat as ambiguous → do not offer in v1.

#### Phase 2: Refine using annotations / expected type
Use IntelliJ/PyCharm type eval where possible:
- If assignment target has annotation: `payload: dict[str, int] = {...}`, prefer `int` for all values when consistent.
- If function parameter expected type is available, intersect/merge.
- If a value is `None` and expected type is `T | None`, set field to `T | None`.

Union inference heuristics:
- If the same key appears across multiple literals in a local list (e.g., `items = [{"id": 1}, {"id": "x"}]`), in v1 don’t attempt; keep inference local to the caret expression.

---

### Code generation rules
#### 1) dict → TypedDict
Generated skeleton:
```python
from typing import TypedDict

class Payload(TypedDict):
    id: int
    name: str
```

- Field names must be valid identifiers; if key is not a valid identifier (`"user-id"`), options:
  - v1: don’t offer
  - or: offer but convert key to valid name and add key mapping (TypedDict can’t map keys; it must match keys). So better: **don’t offer**.

- Optional fields:
  - If optionality is detected (e.g., existing TypedDict says `total=False` or `NotRequired`), generate:
    - `from typing import NotRequired` and `field: NotRequired[T]`
  - Otherwise omit.

Update usages:
- If there is a variable annotation, update to `payload: Payload`.
- If used as an argument to a parameter typed as `Payload`, no changes needed.
- If it is an untyped literal, either:
  - just introduce the `TypedDict` type and annotate the receiving variable, or
  - wrap with a cast `cast(Payload, {...})`.

Recommendation:
- Prefer **annotating the target** when there is a named target (assignment), else use `cast`.

#### 2) TypedDict → dataclass
For:
```python
class Payload(TypedDict):
    id: int
    name: str
```
Generate:
```python
from dataclasses import dataclass

@dataclass
class Payload:
    id: int
    name: str
```

And update uses:
- Convert dict literals to constructor calls: `{ "id": 1, "name": "x" }` → `Payload(id=1, name="x")`
- If the code relies on dict semantics (`payload["id"]`), offer a secondary fix or leave unchanged (but then the conversion is not safe).

Recommendation (safety gating):
- Only offer TypedDict→dataclass when usage within the same file (or within search scope) indicates **attribute-style access** is acceptable, or when user explicitly selects “convert all subscripts to attributes”.

Subscript migration:
- `payload["id"]` → `payload.id`
- If key not string literal or not a field: do not auto-convert.

#### 3) dataclass → pydantic BaseModel
For:
```python
@dataclass
class Payload:
    id: int
    name: str
```
Generate:
```python
from pydantic import BaseModel

class Payload(BaseModel):
    id: int
    name: str
```

Preserve defaults:
- dataclass default values become BaseModel defaults.
- dataclass `field(default_factory=...)` becomes `Field(default_factory=...)`.

Keep validation behaviors explicit:
- Do not auto-add `model_config` unless needed.

---

### Safety / applicability conditions
Offer the intention only when:
- The dict keys are **string literals** and are valid Python identifiers.
- The dict literal is “simple”: no `**` unpacking, no comprehensions, no conditional expression for keys.
- Inference yields at least one field.

For conversions that require refactoring (TypedDict→dataclass, dataclass→BaseModel):
- Provide a preview and apply changes across the file (or project) with `Psi` refactor operations.

---

### Settings (optional, later)
- `Generate nested types recursively` (default: on for TypedDict/dataclass; off for pydantic)
- `Prefer typing_extensions` (off for Python 3.11)
- `Pydantic version target` (v2 default)
- `When no assignment target exists, use cast(...)` (default on)

---

### Test plan (proposed test cases)
Below are concrete tests following your repo’s conventions (`fixtures.TestBase`, `myFixture.configureByText`, `<caret>`, `myFixture.launchAction(...)`, `checkResult`). The tests are grouped by upgrade step.

#### A. dict → TypedDict
1) **Basic literal assignment**
- Input:
```python
payload = {<caret>"id": 1, "name": "x"}
```
- Expected:
  - A `TypedDict` named `Payload` is generated above (or near top).
  - Variable is annotated: `payload: Payload = {...}` OR `payload = cast(Payload, {...})` (pick one consistent behavior).
  - Imports include `TypedDict` (and possibly `cast`).

2) **Already annotated dict target**
- Input:
```python
payload: dict[str, object] = {<caret>"id": 1, "name": "x"}
```
- Expected:
  - Replace annotation with `Payload`.
  - Generate `class Payload(TypedDict): ...`.

3) **Infer Optional from None with expected type**
- Input:
```python
from typing import TypedDict

class Payload(TypedDict):
    name: str | None

payload: Payload = {<caret>"name": None}
```
- Expected:
  - Intention offered? (This is already TypedDict-typed; for dict→TypedDict it may not be offered.)

Alternative better test:
```python
payload: dict[str, str | None] = {<caret>"name": None}
```
- Expected:
  - Generated field type `str | None`.

4) **List value inference**
- Input:
```python
payload = {<caret>"ids": [1, 2, 3]}
```
- Expected field type: `list[int]`.

5) **Heterogeneous list → Any**
- Input:
```python
payload = {<caret>"ids": [1, "x"]}
```
- Expected field type: `list[object]` or `list[Any]` (choose and standardize; `Any` is more typical).

6) **Nested dict generates nested TypedDict (recursive ON)**
- Input:
```python
payload = {<caret>"user": {"id": 1, "name": "x"}}
```
- Expected:
  - `class PayloadUser(TypedDict): id: int; name: str`
  - `class Payload(TypedDict): user: PayloadUser`

7) **Reject non-literal key**
- Input:
```python
k = "id"
payload = {<caret>k: 1}
```
- Expected: intention **not available**.

8) **Reject invalid identifier key**
- Input:
```python
payload = {<caret>"user-id": 1}
```
- Expected: intention **not available** (v1 rule).

9) **No assignment target → use cast**
- Input:
```python
def f(x):
    return g({<caret>"id": 1})
```
- Expected:
  - Generate `TypedDict`.
  - Wrap literal: `g(cast(Payload, {"id": 1}))`.

10) **Preserve formatting and trailing commas**
- Input:
```python
payload = {
    <caret>"id": 1,
    "name": "x",
}
```
- Expected: output keeps multi-line formatting and commas.

#### B. TypedDict → dataclass
11) **Convert TypedDict declaration to dataclass**
- Input:
```python
from typing import TypedDict

class Payload(TypedDict):
    id: int
    name: str
# <caret>
```
- Action: “Convert TypedDict to dataclass”
- Expected:
  - Replace with dataclass definition.
  - Import changes: remove `TypedDict`, add `dataclass`.

12) **Convert dict literal usage to constructor**
- Input:
```python
from typing import TypedDict

class Payload(TypedDict):
    id: int

payload: Payload = {<caret>"id": 1}
```
- Expected:
  - After conversion to dataclass: `payload = Payload(id=1)` (and annotation updated to `Payload` if necessary).

13) **Convert subscript access to attribute**
- Input:
```python
from typing import TypedDict

class Payload(TypedDict):
    id: int

def f(p: Payload):
    return p[<caret>"id"]
```
- Expected:
  - After conversion: `return p.id`

14) **Reject if keys not valid identifiers (safety)**
- Input:
```python
from typing import TypedDict

class Payload(TypedDict):
    "user-id": int  # or via total/annotations pattern
```
- Expected: conversion not offered (or offered with warning but v1: not offered).

15) **Optional/NotRequired handling**
- Input:
```python
from typing import TypedDict, NotRequired

class Payload(TypedDict):
    id: int
    name: NotRequired[str]
```
- Expected dataclass:
  - `name: str | None = None` (or `name: str = ""` is wrong).
  - Prefer `name: str | None = None` to represent “optional presence”.

#### C. dataclass → pydantic BaseModel
16) **Basic dataclass conversion**
- Input:
```python
from dataclasses import dataclass

@dataclass
class Payload:
    id: int
    name: str
# <caret>
```
- Expected:
  - `class Payload(BaseModel): ...`
  - Imports updated.

17) **Preserve defaults**
- Input:
```python
from dataclasses import dataclass

@dataclass
class Payload:
    id: int = 1
# <caret>
```
- Expected:
  - `id: int = 1` remains.

18) **Preserve default_factory**
- Input:
```python
from dataclasses import dataclass, field

@dataclass
class Payload:
    tags: list[str] = field(default_factory=list)
# <caret>
```
- Expected:
  - `from pydantic import BaseModel, Field`
  - `tags: list[str] = Field(default_factory=list)`

19) **Nested dataclasses**
- Input:
```python
from dataclasses import dataclass

@dataclass
class Inner:
    id: int

@dataclass
class Outer:
    inner: Inner
# <caret>
```
- Expected:
  - Convert both (if “convert recursively” is on) or only the selected.
  - Ensure `Outer.inner: Inner` remains valid.

20) **Reject dataclass with unsupported features (MVP)**
- Input:
```python
from dataclasses import dataclass

@dataclass(init=False)
class Payload:
    id: int
# <caret>
```
- Expected: intention not available (or available with limited support; v1: not available).

---

### Edge cases to decide up front (so tests can be stable)
1) **Where to insert generated types**: module top vs nearest scope. Pick one rule and test it.
2) **Naming of nested types**: `PayloadUser` vs `User` vs `Payload_User`. Choose one deterministic convention.
3) **`Any` vs `object`**: in Python typing, `Any` is usually friendlier for “unknown”. Decide and standardize.
4) **How to represent optional presence from dicts**:
   - TypedDict supports `NotRequired[T]` (presence optional) which is different from `T | None` (value can be None). Be explicit:
     - Missing key → `NotRequired[T]`
     - Present but value may be None → `T | None`
5) **Pydantic version**: recommend targeting pydantic v2 in generation (`BaseModel`, `Field`). Keep v1 support as a later enhancement.

---

### Suggested incremental implementation plan
1) Implement **dict → TypedDict** for simplest cases (string-literal keys + identifier keys + flat values) with insertion + annotation update.
2) Add recursive nested dict support.
3) Implement **TypedDict → dataclass** conversion (definition rewrite + update dict literals + subscript-to-attribute in same file).
4) Implement **dataclass → BaseModel** conversion with defaults + `default_factory`.
5) Add safety checks + preview.

---

### What I need from you to tailor the proposal to your plugin’s existing infra
Answering these will let the tests match your current intentions’ patterns:
1) Should generated code prefer `typing.Any` or `object` for unknowns?
2) For dict literals used as arguments (no assignment target), do you prefer `cast(NewType, {...})` or introducing a temporary variable with annotation?
3) Should nested types be generated by default, and what naming convention do you prefer?
4) For TypedDict→dataclass, should the intention refactor only the current file or search usages project-wide?
