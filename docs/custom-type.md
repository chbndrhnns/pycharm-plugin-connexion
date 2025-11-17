### 1. What you’re asking for

You want a quick fix/intention in the PyCharm Python plugin that, given a **stdlib/builtin type** (e.g. `int`, `str`,
`datetime.datetime`, `UUID`, `Path`, etc.), offers to **introduce a custom domain type** based on it, with options like:

- `class` that subclasses the stdlib type
- `typing.NewType`
- `@dataclass` wrapper
- `pydantic.BaseModel` wrapper

and (ideally) updates the current site to use the new type.

### 2. High‑level feasibility

**Feasibility: good, with a few scoping decisions.**

The IntelliJ/PyCharm infrastructure already supports:

- Context‑sensitive intentions/quick fixes on type annotations and expressions
- Language‑level awareness (so you can gate `NewType`, `dataclasses` on Python version)
- Code generation (imports, class definitions, dataclasses, etc.) via PSI factories and templates
- Project SDK introspection and library presence checks (for pydantic availability, although you might choose to skip
  that and just generate code + import)

So implementing this as:

- an **intention action** (no inspection required) or
- a **quick fix attached to a light inspection** (e.g. "Suspicious primitive type in domain boundary"),

is technically straightforward. The complexity is mostly **UX and scope**, not platform limitations.

### 3. Recommended UX

#### 3.1 Trigger surface

I’d recommend starting as an **intention** available when the caret is on a type annotation that is:

- a builtin type (e.g. `int`, `str`, `float`, `bool`, `bytes`), or
- a recognized stdlib type (via qualified name resolution), or
- a container over them (`list[int]`, `dict[str, int]`, `datetime.datetime`, etc. — you can start with the simplest
  forms).

Concrete places:

- `def create_user(id: int): …` → caret on `int`
- `age: int` → caret on `int`
- `price: Decimal` (if `Decimal` from `decimal` module is resolved) → caret on `Decimal`

Intention text examples:

- "Introduce custom type from `int`…"
- or more generic: "Introduce custom domain type…"

#### 3.2 Small dialog rather than many separate intentions

When invoked, show a **small dialog** (or JB popup) with options:

1. **Name** of the new type: text field (`UserId`, `Age`, `MoneyAmount`, …)
2. **Kind** (radio buttons or combo):
    - `Subclass (class Age(int))`
    - `NewType`
    - `@dataclass` wrapper
    - `Pydantic model`
3. **Target module** (where to create it):
    - This file (top‑level)
    - Another module (text field with file chooser / completion, defaulting to `types.py` or `models.py` in the same
      package)
4. Checkbox: **Replace this occurrence with the new type** (on by default)
    - Optional enhancement: "Replace all similar annotations in this file".

This keeps the editor free of clutter (one intention entry) and moves combinatorics into a small, focused UI.

### 4. Generated code shapes

You can implement all four variants simply and consistently.

#### 4.1 Subclassing

From `int`:

```python
class UserId(int):
    pass
```

Pros:

- Usable as an `int` where needed
- Zero runtime cost, minimal extra semantics

Cons:

- Type checkers treat subclassing differently from `NewType` (less strict);
- Semantically weaker guarantee.

Implementation details:

- In target file, create a `PyClass` with the base being the resolved stdlib type (`int`, `datetime.datetime`, etc.).
- Ensure import exists when base is not builtin (e.g. `from datetime import datetime`).

#### 4.2 `typing.NewType`

From `int`:

```python
from typing import NewType

UserId = NewType("UserId", int)
```

Pros:

- Very common in typed codebases
- Zero runtime overhead (but more restrictive for type checkers, which is often desirable)

Cons:

- Available only for Python ≥ 3.5 (or needs `typing_extensions` in older code). You already have language level
  detection to gate this or adjust import.

Implementation details:

- Use project language level and existing helpers the plugin already uses (there is already a `PyNewTypeInspection`; you
  can reuse some utilities and tests).
- Inject appropriate import (`typing` vs `typing_extensions`) based on language level and maybe project settings.

#### 4.3 Dataclass wrapper

From `int`:

```python
from dataclasses import dataclass

@dataclass
class UserId:
    value: int
```

Pros:

- Explicit `value` attribute; extensible later (more fields, methods)
- Plays well with many codebases that already use dataclasses for domain models

Cons:

- A different runtime representation (`UserId` objects) that must be used consistently

Implementation details:

- Insert `@dataclass` decorator and import `dataclass`.
- For now, generate a single field named `value` (or derive something from context, but that’s extra complexity).

#### 4.4 Pydantic model wrapper

From `int`:

```python
from pydantic import BaseModel

class UserId(BaseModel):
    value: int
```

Pros:

- Attractive for service/validation heavy codebases
- Provides validation and serialization hooks

Cons:

- Requires pydantic dependency; version differences (v1 vs v2) could matter

Implementation details:

- You can start by not checking whether `pydantic` is actually installed; just generate the code and import.
- Optional refinement: if project interpreter is available, check that `pydantic` can be imported and only show this
  option when it is.

### 5. How it integrates technically (PyCharm side)

#### 5.1 Where to put it

- Module: `python/python-psi-impl` is the likely home for intentions that manipulate Python PSI.
- Create something like `IntroduceCustomTypeFromStdlibIntention`.

#### 5.2 PSI patterns and applicability

Rough applicability logic:

1. Caret on a `PyExpression` or `PyTypeHintExpression` that resolves to a **known stdlib/builtin type**.
2. Use existing resolve/type helpers to obtain the fully qualified name of the type.
3. Apply simple filters:
    - `builtins.int`, `builtins.str`, etc.
    - `datetime.datetime`, `uuid.UUID`, `pathlib.Path`, etc. (start small, you can grow the set later).

You can initially restrict this to **annotations** (`PyAnnotationOwner`) rather than arbitrary expressions to keep the
scope clear.

#### 5.3 Code generation helpers

Implementation steps in the intention:

1. Detect the **current module/file** and package.
2. After the user chooses name/kind/target, open the target file as a `PsiFile`/`PyFile`.
3. Generate the appropriate stub (class, NewType assignment, dataclass, pydantic model) using `PyElementGenerator`.
4. Insert it at a sensible location:
    - Near top of file, after imports and module docstring, or
    - At end of file, depending on local conventions (you can reuse existing patterns used by other generation actions).
5. Ensure imports exist (use Python import helpers for adding or reusing imports).
6. Replace the original annotation node with a reference to the new type:
    - Insert import for the new type in the original file if it’s defined elsewhere.

Optional later tweaks:

- Offer to search and update other identical annotations in the file/module.
- Honour code style (qualified vs unqualified imports, import ordering) — reuse existing code style utilities.

### 6. Edge cases and limitations

Things to decide up‑front (to keep v1 small):

- **Supported contexts**: start with type annotations only (function params, return types, annotated assignments). Skip
  expression contexts (`user_id = 0`) for now.
- **Containers**: either disallow `list[int]`/`dict[str, int]` at first or treat the innermost primitive (`int`) as the
  base.
- **Python version support**:
    - `NewType`: respect language level; fall back to `typing_extensions` or hide the option when not valid.
    - `dataclasses`: Python 3.7+ (or backport) — you can simply show it for 3.7+.
- **Pydantic version**: pydantic v1 vs v2 might need different base or config; for v1 a plain `BaseModel` is OK; for v2,
  same but you might later add config options. For initial iteration, just `BaseModel`.
- **Where to define types**: v1 can default to "current file"; later you can add a project‑level option or remember last
  choice.

### 7. Proposal summary

**Feature name (working):** "Introduce custom type from stdlib type".

**v1 scope:**

- Implement an **intention** (no inspection) available on stdlib/builtin types in **annotations**.
- Support 4 generation strategies via a dialog:
    - subclass of builtin/stdlib type
    - `NewType`
    - `@dataclass` wrapper
    - `pydantic.BaseModel` wrapper
- Generate code in current file, maintain imports, and replace the current annotation with the new type.
- Respect Python language level for `NewType` and dataclasses; pydantic option always visible initially.

**Why this is a good first cut:**

- Highly feasible with existing APIs; code generation patterns already exist in the Python plugin.
- Keeps UX simple (one intention, small dialog) and non‑intrusive.
- Provides real value for users trying to move away from primitive types toward domain‑specific ones, especially in
  typed and pydantic‑heavy projects.

If you’d like, I can next sketch:

- A concrete Kotlin skeleton for the intention class
- Example PSI pattern code to detect applicable contexts
- The exact dialog structure and how it plugs into the intention