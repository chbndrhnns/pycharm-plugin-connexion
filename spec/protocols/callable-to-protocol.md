### What to cover for `CallableToProtocolIntention`

Below is a checklist of **examples** and **edge cases** you should consider when deciding what the intention should
support, how it should behave, and what tests to add. I’m grouping them by: (1) *availability detection*, (2) *Callable
shape parsing*, (3) *type-feature coverage*, and (4) *code-generation / insertion behavior*.

---

### Availability / detection examples

#### Basic “should be available”

1. **Direct import**
   ```py
   from typing import Callable
   x: Call<caret>able[[int], str]
   ```
2. **Qualified name**
   ```py
   import typing
   x: typing.Call<caret>able[[int], str]
   ```
3. **`from collections.abc import Callable` (Python 3.9+)**
   ```py
   from collections.abc import Callable
   x: Call<caret>able[[int], str]
   ```
4. **PEP 563/PEP 649 style strings (common in real code)**
   ```py
   from __future__ import annotations
   from typing import Callable
   x: "Call<caret>able[[int], str]"
   ```
   Decision: the intention should work inside string annotations

#### Should NOT be available

5. **Not a subscription**
   ```py
   from typing import Callable
   x: Call<caret>able
   ```
6. **Different generic**
   ```py
   from typing import Sequence
   x: Se<caret>quence[int]
   ```
7. **Name collision (local `Callable`)**
   ```py
   Callable = 123
   x: Call<caret>able[[int], str]
   ```
   Decide: availability should verify it’s actually the typing/abc `Callable`, not merely the identifier.

8. **Aliased import**
   ```py
   from typing import Callable as C
   x: C[[int], str]
   ```
   Decide: intention should detect `C` as callable-type if it resolves to `typing.Callable`.

9. **Caret placement variability**
    - caret on `Callable`
    - caret in `[[...], ...]`
    - caret on return type

   In tests, ensure `isAvailable` works for “natural caret positions”, not only one exact token.

---

### Callable shape parsing examples

#### Canonical supported shape

10. **Simple args list**
    ```py
    from typing import Callable
    x: Callable[[int, str], bool]
    ```

#### Variants you should explicitly decide on

11. **Ellipsis args (`Callable[..., R]`)**
    ```py
    from typing import Callable
    x: Callable[..., int]
    ```
    Expected protocol options: `def __call__(self, *args: Any, **kwargs: Any) -> int: ...` (needs `Any` import)

12. **No args (`Callable[[], R]`)**
    ```py
    from typing import Callable
    x: Callable[[], str]
    ```
    Should generate `def __call__(self) -> str: ...`.

13. **Single arg**
    ```py
    x: Callable[[int], str]
    ```

14. **Malformed / incomplete** (should fail gracefully)
    ```py
    x: Callable[[int, str]]
    x: Callable[int, str]
    x: Callable[[int, str],]
    ```

15. **Return type omitted / `Any` implied (not legal typing, but appears during editing)**
    Consider behavior when PSI is present but incomplete.

---

### Type feature coverage (important edge cases)

These are the ones that frequently break naive “just use `.text`” approaches.

#### Type arguments that are not simple names

16. **Union / Optional**
    ```py
    from typing import Callable
    x: Callable[[int | None], str | None]
    ```

17. **Generics**
    ```py
    from typing import Callable
    x: Callable[[list[int], dict[str, int]], tuple[int, ...]]
    ```

18. **Forward refs inside Callable**
    ```py
    from typing import Callable
    x: Callable[["Node"], "Node"]
    ```

19. **Callable returning `None`**
    ```py
    x: Callable[[int], None]
    ```

20. **Nested Callable**
    ```py
    x: Callable[[int], Callable[[str], bool]]
    ```
    Result protocol should preserve nested text correctly.

#### ParamSpec / Concatenate (advanced but common in typing-heavy code)

21. **`ParamSpec` pass-through**
    ```py
    from typing import Callable, ParamSpec, TypeVar
    P = ParamSpec("P")
    R = TypeVar("R")
    x: Callable[P, R]
    ```
    This is a *different Callable form* than `Callable[[...], ...]`.
    Decision: Generate `def __call__(self, *args: P.args, **kwargs: P.kwargs) -> R: ...`.

22. **`Concatenate`**
    ```py
    from typing import Callable, Concatenate, ParamSpec, TypeVar
    P = ParamSpec("P")
    R = TypeVar("R")
    x: Callable[Concatenate[int, P], R]
    ```

#### TypeVar / Generic Protocol needs

23. **TypeVars appear in signature**
    ```py
    from typing import Callable, TypeVar
    T = TypeVar("T")
    x: Callable[[T], T]
    ```
    Decision: generated protocol itself should become `Protocol[T]` / `Generic[T]`.

---

### Code generation & insertion behavior (PSI / formatting edge cases)

#### Where the annotation occurs

24. **Function parameter annotation (your current test case)**
    ```py
    def foo(c: Callable[[int], str]): ...
    ```

25. **Variable annotation at module level**
    ```py
    c: Callable[[int], str]
    ```
    Where do you insert the protocol? (Top-level after imports is typical.)

26. **Inside a class body**
    ```py
    class C:
        x: Callable[[int], str]
    ```
    Insertion decision: before `class C` (top-level)

27. **Inside a nested function / nested class**
    ```py
    def outer():
        def inner(cb: Callable[[int], str]): ...
    ```
    Decide: insert protocol at top-level (simplest) vs nearest enclosing scope.

#### Import handling

28. **Protocol already imported**
    ```py
    from typing import Protocol, Callable
    ```
    Ensure no duplicate imports.

29. **`typing` imported as module**
    ```py
    import typing
    x: typing.Callable[[int], str]
    ```
    Rewrite to `typing.Protocol`.

30. **`Protocol` from `typing_extensions`** (depending on target Python)
    If you support older versions, you might need:
    ```py
    from typing_extensions import Protocol
    ```

#### Name generation / collisions

31. **`MyProtocol` already exists**
    ```py
    class MyProtocol(Protocol): ...
    x: Callable[[int], str]
    ```
    Should generate a unique name (e.g. `MyProtocol1`) or derive from context (`FooCallbackProtocol`).

32. **Multiple callables in the same file**
    - Converting two different `Callable[...]` should not reuse the same protocol name.

33. **Existing symbol named `Protocol` / local `Protocol`**
    Avoid breaking code if `Protocol` identifier is shadowed.

#### Formatting / newline stability

34. **Preserve import grouping and blank lines**
    - Make sure reformatting yields stable results in tests.

35. **Line endings & trailing newline**
    - Tests typically want a trailing `\n` (as your current test does).

---

### Behavior expectations (what each test should assert)

For each supported case, tests should assert on the shape of the file AFTER the generation via myFixture.checkResult().

For each unsupported case, tests should assert:

- Intention is not available, **or** invoking it produces no change (depending on your desired UX).

---

### Minimal “must-have” example set (pragmatic starter list)

If you want a compact but high-value set of tests to start with:

1. `Callable[[int, str], bool]` (already covered)
2. `Callable[[], R]`
3. `Callable[..., R]` (explicitly decide support)
4. `typing.Callable[[...], ...]`
5. Name-collision with existing `MyProtocol`
6. Callable in module-level variable annotation

These 6 cover most of the correctness surface and will quickly reveal parsing, insertion, and import issues.
