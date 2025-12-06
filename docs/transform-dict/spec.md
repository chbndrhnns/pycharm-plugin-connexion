### Goal
Provide a PyCharm intention action that toggles a dictionary access between bracket form `d[key]` and safe `d.get(key, default)` (and vice‑versa) with minimal surprises.

### High‑level design / plan
1. **Identify applicable PSI nodes**
   - `PySubscriptionExpression` where operand is dict‑like and index is present (e.g., `d["k"]`).
   - `PyCallExpression` shaped like `d.get(key)` or `d.get(key, default)`.
   - Exclude cases inside assignment to subscription (e.g., `d["k"] = v`) when converting to `get` because semantics differ.
2. **Type/semantics checks**
   - Use type inference: left expression must be `dict` or `Mapping` (or `TypedDict`) for bracket→get; must resolve to `dict.get` for get→bracket.
   - Skip if `get` is shadowed (attribute resolved to non‑builtin) or `__getitem__` overloaded on non‑mapping.
3. **Transformation rules**
   - **Bracket → get**: `d[key]` → `d.get(key)`.
   - If surrounding code expects default `KeyError`, keep bracket (offer intention but warn in preview about behavior change?). Ideally only offer when safe context (e.g., used in conditional that checks `is not None`?) — or provide option in settings.
   - **Get → bracket**: `d.get(k)` → `d[k]`; `d.get(k, default)` → `d[k] if d.__contains__(k) else default` is not semantics‑preserving; thus only offer when no default argument present.
   - Preserve comments and formatting via `PyElementGenerator` and `CodeStyleManager`.
4. **Availability check**
   - Must not be inside f‑string expressions that disallow statements.
   - Skip when index has side effects and converting to `get` would change evaluation count.
   - Disable inside type‑checking blocks where `TypedDict` required keys exist (optionally).
5. **User‑visible text**
   - Intention names: "Replace `dict[key]` with `dict.get(key)`" and "Replace `dict.get(key)` with `dict[key]`".
6. **Preview**
   - Provide preview text showing before/after; if semantics may change (KeyError vs None), include warning in description.
7. **Testing strategy**
   - Implement with `PyIntentionTestCase` using testData files.

### Test cases (positive)
- Simple bracket → get: `d["a"]` → `d.get("a")`.
- Variable key: `d[key]` → `d.get(key)`.
- Get → bracket without default: `d.get("a")` → `d["a"]`.
- Chained: `(foo().bar)[x]` converts if `bar` inferred as dict.
- TypedDict optional key: `movie["rating"]` converts (allowed) vs required key (perhaps suppressed if configured).
- Inside expression: `len(d["a"])` → `len(d.get("a"))`.
- Maintains comments: `d[/*c1*/k/*c2*/]` preserves inline comments.
- Formatting preserved with multiline keys.

### Negative / edge cases to skip
- Assignment target: `d["a"] = 1` (no intention).
- Augmented assign: `d["a"] += 1`.
- Delete: `del d["a"]`.
- `get` with default: `d.get("a", 0)` should **not** convert to bracket (different semantics).
- `get` on shadowed method: class `Foo: get = lambda self, k: 1`; avoid offer.
- Non‑dict `__getitem__`: list/tuple/indexing with int; ensure type check prevents.
- Side‑effect key: `d[f()]` shouldn’t convert (would run twice if user later rewrites? optional safety).
- Nonexistent receiver: `None["a"]` not applicable.
- Custom mapping overriding `get` with different semantics; resolved target not builtin mapping – skip.
- In `try/except KeyError` blocks where behavior change is likely unintended (optional heuristic).

### Additional corner cases
- `d.get(*args)` or `**kwargs` – avoid conversion.
- Unicode/bytes keys; no special handling but add test.
- Nested conversions: `d1[d2["k"]]` – only convert selected node.
- Subclassed dict with `@property def get`: ensure resolve detects.
- Pattern matching / walrus inside index expressions (`d[(k := foo())]`) – ensure PSI regenerated correctly.

### Test data layout suggestion
```
testData/intention/dictAccess/
  BracketToGet.py
  GetToBracket.py
  NoQuickFixOnAssignment.py
  SkipOnDefaultArg.py
  PreserveComments.py
  TypedDictOptional.py
  ShadowedGet.py
```

### Implementation notes
- Use `PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel, text)` to build new calls/subscripts.
- For availability, resolve `PySubscriptionExpression.getOperand().getType()` with `TypeEvalContext.codeAnalysis`. Check `isinstance(type, PyDictType) or PyCollectionType && type.isMappingType()`.
- For `get` side, resolve `callee` reference; ensure `qualifier` exists and function name `get` and no keyword args.
- Register under `MetaProgrammingIntentions` or similar group.
