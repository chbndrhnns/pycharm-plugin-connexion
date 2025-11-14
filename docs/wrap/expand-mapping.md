### What you want to support

You have a dataclass and a call like this:

```python
import dataclasses


@dataclasses.dataclass
class Data:
    val: int | None


data: Data = Data({
    "val": "3"
})
```

There are two useful transformations users expect from a “Wrap with expected type” intention in such a case:

1) Convert the mapping to a dataclass constructor call with proper keyword arguments.
    - From `Data({"val": "3"})` to `Data(val="3")` or `Data(**{"val": "3"})`.

2) Then, for each field value whose type is mismatched, offer (or apply) wrapping with the field’s expected type.
    - From `Data(val="3")` to `Data(val=int("3"))` because `val` is `int | None`.

Below is how to detect these cases and how to implement the wrapping neatly in your intention.

---

### Heuristics and decision tree

When the caret is on an expression E inside a call `C(...)` and the expected type for the overall argument is a
dataclass `D`:

- If E is (or is inside) a `dict` literal or a mapping expression whose keys look like dataclass field names:
    - Prefer rewriting the call to pass keyword args instead of a single mapping argument.
    - If all keys are static strings valid as identifiers and all exist in `D`’s fields → expand to explicit keywords:
      `D(f1=v1, f2=v2, ...)`.
    - Else (dynamic keys, unpacking present, or an unknown key) → keep a mapping form but make it clear that it is used
      as keyword-args: `D(**mapping)`.

- After the call is in a keyword-argument form, compute the expected type per field and enable your usual wrap logic on
  each value expression (including unions, `NewType`, builtins, etc.). In this example, offer `int("3")` for the `val`
  field, or if the field were `One | Two`, show the same chooser you already have for unions.

This results in one of the two outcomes below (both acceptable, the first is generally clearer):

```python
# Preferred explicit keywords
Data(val=int("3"))

# Fallback when keywords cannot be expanded cleanly
Data(**{"val": int("3")})
```

---

### How to recognize a dataclass and map dict keys to field types

- Resolve the callee of the call (`PyCallExpression.callee`). If the resolved element is a `PyClass` decorated with
  `dataclasses.dataclass`, treat it as a dataclass.
- Extract the effective `__init__` signature (PyCharm’s type system already knows this). Alternatively, read the
  dataclass fields from the class body and respect `init=False` exclusions when building the keyword list.
- If the argument in question is a `PyDictLiteralExpression`, walk entries:
    - Each key must be a string literal or an identifier-like string matching a field name.
    - Build a list of `(fieldName, valueExpression, expectedFieldType)`.

For non-literal mappings (a variable name, a call, or something containing `**other`), you can still offer the
`**mapping` wrapping and then drop down to value-level intentions only when a value is syntactically inspectable.

---

### Integration points in your existing intention

You already have machinery for:

- Detecting expected type vs. actual (`PyTypeIntentions.computeTypeNames`).
- Deriving a constructor name (`expectedCtorName`).
- Handling unions via a chooser.

Extend the intention with a pre-step that triggers when the expected type is a class decorated with `@dataclass` and the
problematic expression is a mapping.

#### 1) Detect the “dict passed to dataclass” scenario

```kotlin
// Pseudocode inside `invoke` (or a helper used by it)
val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
val calleeClass = call?.callee?.reference?.resolve() as? PyClass
if (calleeClass != null && isDataclass(calleeClass)) {
    val argList = call.argumentList
    val arg = element as? PyExpression
    if (arg != null && isMappingLiteral(arg)) {
        val fields = dataclassInitFields(calleeClass) // name -> expected type
        val mapping = parseDictLiteral(arg)          // list of (key:String?, value:PyExpression)
        if (mapping.keysAllIn(fields)) {
            if (mapping.allKeysAreIdentifiers()) {
                // Expand to explicit keywords
                replaceCallWithKeywords(project, call, mapping, fields)
            } else {
                // Keep mapping but turn it into **mapping
                replaceArgWithDoubleStar(project, arg)
            }
            return // after this, your normal wrapping will apply to each field value
        }
    }
}
```

Utility sketches:

```kotlin
fun isDataclass(pyClass: PyClass): Boolean =
    pyClass.decoratorList?.decorators?.any {
        it.qualifiedName in setOf(
            "dataclasses.dataclass", "dataclass" // guarded by imports/qualnames
        )
    } == true

fun isMappingLiteral(expr: PyExpression): Boolean = expr is PyDictLiteralExpression

data class FieldInfo(val type: PyType?, val name: String)

fun dataclassInitFields(c: PyClass): Map<String, FieldInfo> {
    // Either via type system’s synthesized __init__ or by inspecting assignments/annotations.
    // Respect init=False, defaults, and field order as needed.
}

fun parseDictLiteral(d: PyDictLiteralExpression): List<Pair<String?, PyExpression>> { /* ... */
}
```

#### 2) Expanding to keyword arguments

```kotlin
fun replaceCallWithKeywords(
    project: Project,
    call: PyCallExpression,
    mapping: List<Pair<String, PyExpression>>, // assume validated
    fields: Map<String, FieldInfo>
) {
    val gen = PyElementGenerator.getInstance(project)
    val level = LanguageLevel.getLatest()

    // Build `D(f1=v1, f2=v2, ...)` text
    val calleeText = call.callee?.text ?: return
    val argsText = mapping.joinToString(", ") { (k, v) -> "$k=${v.text}" }
    val newCall = gen.createExpressionFromText(level, "$calleeText($argsText)")
    call.replace(newCall)
}
```

If you encounter nested dicts that match nested dataclasses, you can recurse (nice-to-have) and immediately enable your
union/builtin wrappers for each nested value too.

#### 3) Switching to `**mapping` when expansion is unsafe

```kotlin
fun replaceArgWithDoubleStar(project: Project, arg: PyExpression) {
    val gen = PyElementGenerator.getInstance(project)
    val level = LanguageLevel.getLatest()
    val newExpr = gen.createExpressionFromText(level, "**(${arg.text})")
    arg.replace(newExpr)
}
```

Note: only do this when `arg` is an argument inside `Data(...)`; don’t wrap a bare dict with `**` outside a call.

---

### After expansion: field-level wrapping

Once `Data({...})` becomes `Data(val=...)`, your existing intention logic can run on the value of `val` the same way it
currently runs for function parameters. That gives you for this example:

- If caret on `"3"` and expected field type is `int | None`, show union choices (if it’s a union of non-builtin types)
  or directly wrap with the canonical constructor when unambiguous (`int("3")`).
- If the union contains builtins only (e.g., `int | None`), pick the non-`None` member as the default wrap, but allow
  “Keep as is” via Alt-Enter submenu if you provide one.

This reuses your `expectedCtorName` + `collectUnionCtorCandidates` flow:

- For `int | None`, `expectedCtorName` should return `int`.
- For `One | Two` (both `NewType`), you already show a chooser.

---

### Edge cases to cover

- Extra keys in the dict: don’t auto-expand; fall back to `**mapping` or do nothing. Optionally show a warning in the
  preview text.
- Missing required fields: expanding to keywords is still fine; user will see PyCharm’s inspection for the missing ones.
- Fields with `init=False`: omit them when generating keyword args.
- Default and default-factory fields: OK to omit if not present in mapping.
- TypedDict input: if `arg` has a statically-known `TypedDict` type compatible with the dataclass fields, prefer
  expansion to keywords (preserves types and quick-fixes).
- Already-in-keyword form: don’t offer the mapping-to-keywords action.
- Caret inside nested dicts: compute the path of keys from this dict up to the dataclass call and obtain the nested
  field type to power value-level wrapping.

---

### User-facing examples

- Simple case

```python
@dataclass
class Data:
    val: int | None


Data({"val": "3"})  # Alt-Enter → Expand mapping → Data(val="3")
# Alt-Enter on the value → Wrap with int() → Data(val=int("3"))
```

- Multiple fields

```python
@dataclass
class Data:
    a: int
    b: str


Data({"a": "1", "b": 2})  # → Data(a=int("1"), b=str(2))
```

- Dynamic mapping

```python
fields = {"val": "3"}
Data(fields)  # → Data(**fields)
```

- Nested dataclasses (optional enhancement)

```python
@dataclass
class Inner:
    i: int


@dataclass
class Outer:
    inner: Inner


Outer({"inner": {"i": "5"}})
# → Outer(inner=Inner(i=int("5")))
```

---

### Why this fits your current design

- You already compute expected types for arguments (`computeTypeNames`) and you already have per-value wrapping,
  including unions and `NewType` chooser.
- The only new logic you need is a small PSI-aware transformation layer that recognizes a dict passed to a dataclass
  constructor and rewrites the call into a shape where your existing intention becomes applicable at each field value.
- This mirrors how PyCharm itself renders dataclass `__init__` signatures, so resolution/imports keep working as they do
  in your current implementation.

---

### Minimal implementation plan

1) In `WrapWithExpectedTypeIntention.invoke`, before calling the generic wrap path, call a helper like
   `maybeRewriteDataclassMappingCall(...)`.
2) If it rewrites the call, return early; the user can invoke the intention again on each field value (or you can
   trigger value-wrapping immediately for the caret-targeted field if desired).
3) Add tests:
    - `Data({"val": "3"})` → `Data(val=int("3"))`.
    - `Data({"a": "1", "b": 2})` → `Data(a=int("1"), b=str(2))`.
    - Unknown/extra key keeps `**mapping`.
    - Nested dataclass optional test if you implement recursion.

This will make your intention handle the dataclass + mapping pattern cleanly and integrate with your current
union/chooser behavior.