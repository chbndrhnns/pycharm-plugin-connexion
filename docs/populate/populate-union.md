### Goal
When the intention populates arguments and hits a `Union[...]` (including `X | Y`), it should pick a sensible default, let the user change it quickly, and be predictable across runs.

### Recommended behavior
1. Detect unions uniformly
   - Treat both `typing.Union[A, B]` and `A | B` as unions, and flatten nested unions: `Union[int, Union[str, None]] → {int, str, None}`.

2. Prefer non-`None` candidates by default
   - For `Optional[T]` (`Union[T, None]`), choose `T` and synthesize a value for `T`.
   - If the parameter default value is `None` (e.g., dataclass field `f: Optional[T] = None` or function default `f: T | None = None`), prefer `None` unless the user explicitly asks to populate non-`None` values (e.g., via an intention option like “Prefer non‑None for Optional”).

3. Rank union members and auto‑pick when unambiguous
   - Ranking (higher first):
     1) Dataclass / Pydantic model / `attrs` class types
     2) Concrete collection types with known element types (`list[T]`, `dict[K, V]`, `set[T]`, `tuple[... ]`)
     3) Plain classes (including enums)
     4) Primitive builtins (`str`, `int`, `float`, `bool`, `bytes`)
     5) `Literal[...]` values
     6) `typing.Any`, `typing.Never`, `typing.NoReturn`
     7) `NoneType`
   - If exactly one non‑`None` candidate remains after filtering and it’s rank‑dominant, populate it without showing a chooser.

4. Offer a type chooser when multiple viable candidates exist
   - Show a lightweight popup listing union members with icons and short hints (e.g., fields for dataclasses, base class for others).
   - Remember last choice per “parameter type signature” in the current project session (e.g., key by `file:fqn:param:union-fingerprint`) to avoid repeated prompts.
   - Provide quick toggle actions in the popup: “Prefer dataclass‑like”, “Prefer primitive”, “Insert None”.

5. Respect recursion limits and existing settings
   - Reuse the same recursion limit you already apply elsewhere (see your existing recursive tests). If the chosen union member is recursive, expand until the limit and end with `...` placeholders.

6. Preserve call style
   - Keep keyword names, kw‑only positioning, and comma formatting. Do not force positional arguments.

7. Fallbacks
   - If every candidate is hard to synthesize (e.g., abstract, protocol), insert `...` (or `None` for optionals when that matches the default) and add an end‑of‑line comment `# TODO: pick union variant`.

### Examples
- Dataclass with optional nested model
```python
from dataclasses import dataclass
from typing import Union

@dataclass
class D:
    v: int

@dataclass
class E:
    d: Union[D, None]

e = E(d=D(v=...))  # prefer non‑None unless default is None
```

- Mixed union with primitives
```python
class U: ...

def f(x: int | U | None):
    pass

f(x=U(...))  # rank prefers class over primitive, unless user previously chose int
```

- Collection member
```python
from typing import Union

def g(xs: Union[list[int], set[int]]):
    pass

g(xs=[...])  # choose first according to ranking or show chooser if user hasn’t set a preference
```

### Implementation sketch (Kotlin / PyCharm PSI)
#### Detect and normalize unions
```kotlin
private fun PyType.flattenUnion(): List<PyType> = when (this) {
    is PyUnionType -> this.members.flatMap { it.flattenUnion() }
    else -> listOf(this)
}

private fun PyType.isNoneType(): Boolean = PyNoneType.equals(this)
```

#### Ranking
```kotlin
private enum class Kind { DATA_MODEL, COLLECTION, CLASS, PRIMITIVE, LITERAL, ANYLIKE, NONE }

private fun kindOf(t: PyType): Kind = when {
    t.isNoneType() -> Kind.NONE
    t is PyClassType && t.pyClass.isDataclassLike() -> Kind.DATA_MODEL
    t.isTypedCollection() -> Kind.COLLECTION
    t is PyClassType -> Kind.CLASS
    t.isPrimitiveBuiltin() -> Kind.PRIMITIVE
    t.isLiteral() -> Kind.LITERAL
    t.isAnyLike() -> Kind.ANYLIKE
    else -> Kind.CLASS
}

private val kindOrder = listOf(
    Kind.DATA_MODEL, Kind.COLLECTION, Kind.CLASS, Kind.PRIMITIVE, Kind.LITERAL, Kind.ANYLIKE, Kind.NONE
)

private fun rank(types: List<PyType>): List<PyType> =
    types.sortedWith(compareBy({ kindOrder.indexOf(kindOf(it)) }, { renderTypeName(it) }))
```

Helper predicates (sketch):
```kotlin
private fun PyClass.isDataclassLike(): Boolean =
    PyDataclassUtils.hasDataclassDecorator(this) || isPydanticModel() || hasAttrsDecorator()

private fun PyType.isTypedCollection(): Boolean = this is PyCollectionType && this.elementTypes.isNotEmpty()
private fun PyType.isPrimitiveBuiltin(): Boolean =
    this.isBuiltin("str") || this.isBuiltin("int") || this.isBuiltin("float") || this.isBuiltin("bool") || this.isBuiltin("bytes")

private fun PyType.isAnyLike(): Boolean = this.name in setOf("Any", "Never", "NoReturn")
```

#### Choosing the member
```kotlin
fun chooseUnionMember(param: ParamContext, union: PyType, defaultsToNone: Boolean, settings: PopulateSettings): PyType? {
    val members = union.flattenUnion()
    val nonNone = members.filterNot { it.isNoneType() }

    if (nonNone.isEmpty()) return if (defaultsToNone) PyNoneType.INSTANCE else null

    // Optional handling
    if (!defaultsToNone && nonNone.size == 1) return nonNone.first()

    val ranked = rank(nonNone)

    // Respect stored preference
    settings.lastChoiceFor(param)?.let { preferred ->
        ranked.firstOrNull { renderTypeName(it) == preferred }?.let { return it }
    }

    // Auto‑pick dominant kind or show chooser
    val topKind = kindOf(ranked.first())
    val topOnly = ranked.takeWhile { kindOf(it) == topKind }
    return if (topOnly.size == 1 && !settings.alwaysAskForUnions) ranked.first() else null
}
```

If `chooseUnionMember(...)` returns `null`, open a popup to ask the user and persist the selection for the session.

#### Synthesizing the placeholder
Reuse your existing constructors for non‑union types:
```kotlin
fun synthesizeFor(type: PyType, ctx: SynthesisContext): String = when {
    type.isNoneType() -> "None"
    type.isPrimitiveBuiltin() -> defaultPrimitivePlaceholder(type) // e.g., "..." or quoted "..."
    type is PyCollectionType -> synthesizeCollection(type, ctx)
    type is PyClassType && type.pyClass.isDataclassLike() -> synthesizeDataModelCall(type, ctx)
    type is PyClassType -> "${'$'}{type.name}(... )"
    else -> "..."
}
```

### Edge cases to cover in tests
- Optional dataclass field with and without default `None`.
- Union of two models → chooser shown, remembered choice, subsequent runs auto‑pick.
- Union containing `Literal[...]` and a model → pick the model.
- Union of primitives → pick the first by stable sort, allow user override.
- Nested unions and `X | (Y | None)` flattening.
- Recursive unions respecting recursion limit.
- Kw‑only parameters preserved.

### Why this fits your existing tests
- Your current test `d: Union[D, None]` expects `d=D(v=...)`. The above rules prioritize non‑`None` for optionals and rank dataclass‑like highest, matching the expected output.
- The recursion policy aligns with your existing recursion limit test; the union handling simply feeds into the same synthesizer.

### Minimal user‑visible options
- Settings checkbox: “Always ask for union types (otherwise infer)”.
- Quick actions in the chooser: “Prefer dataclass/attrs/pydantic”, “Prefer primitive”, “Insert None”.
- Per‑parameter last choice remembered for the session, with a project‑level reset action.

This approach keeps the feature fast and predictable while giving users control when there isn’t a single obvious choice.