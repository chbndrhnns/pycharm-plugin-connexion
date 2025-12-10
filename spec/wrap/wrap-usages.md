### Understanding the requirement

You have an intention *Introduce custom type from stdlib* that already:

- Creates a `class CustomInt(int): ...`-style wrapper
- Replaces the **annotation** or **expression** at the caret
- Optionally wraps some usages (as seen in the existing tests).

For this dataclass case, you want to ensure that when a custom type is introduced at **either**:

1. The dataclass field annotation, or
2. A call site (e.g. `D(product_id=123)`),

…all relevant usages are wrapped so that there are **no type errors** (e.g. `D(product_id=int)` while
`product_id: CustomInt`).

The concrete example:

```python
import dataclasses


@dataclasses.dataclass
class D:
    product_id: i < caret > nt


def do():
    D(product_id=123)
```

Expected behavior when triggering the intention on `int`:

- Introduce a custom type based on the field name (e.g. `Productid`) or some naming scheme you decide.
- Change the field annotation to that custom type.
- Wrap all `D` constructor usages where `product_id` is passed, e.g.
    - `D(product_id=123)` → `D(product_id=Productid(123))`.

Similarly, if we trigger the intention on `123` at the call site, you want the plugin to:

- Introduce the custom type
- Update the dataclass field annotation accordingly
- And still wrap all other usages.

---

### High-level plan

1. **Extend applicability for dataclass fields**
    - Detect the caret on a builtin type annotation (`int`, `str`, `float`, etc.) inside a class decorated with
      `@dataclasses.dataclass`.
    - Reuse existing logic for simple annotations (already present in your tests) but specialize the naming based on the
      **field name**.

2. **Extend applicability for dataclass constructor calls**
    - Detect the caret on a literal (or expression) that is passed as keyword/positional argument to a dataclass
      constructor.
    - Resolve:
        - The target class (`D`)
        - The corresponding field (`product_id`) for the keyword argument (or positional index mapping).
    - Use that field name as the basis for the new custom type name.

3. **Introduce the custom type class**
    - Decide insertion point:
        - Usually at module top-level, before the dataclass, consistent with your existing tests (
          `class Customint(int): ...`).
    - Class name choice:
        - For this feature we probably want to preserve the behavior in existing tests: `Productid` derived from
          `product_id`.

4. **Update the field annotation**
    - For the dataclass field `product_id: int`, replace `int` with `Productid` (or your final name).
    - If intention was triggered at the call-site, still go back to the dataclass and update the annotation.

5. **Wrap all relevant usages**
    - For each call `D(...)` in the same module (or in the applicable search scope):
        - Locate arguments that target `product_id` (keyword or positional).
        - Wrap them with the new custom type: `D(product_id=Productid(existing_expr))`.
    - Ensure idempotency: don’t double-wrap if the argument is already `Productid(...)`.

6. **Avoid introducing type mismatches in signatures**
    - If you also have functions accepting the field type directly (e.g. `def foo(product_id: int)`), consider **not**
      changing those automatically for now, unless you explicitly decide otherwise.
    - The core requirement is: no mismatches between `D.product_id`’s type and how `D` is called.

7. **Preserve formatting and comments**
    - Ensure that wrapping preserves whitespace and inline comments around arguments.

8. **Add configuration & toggles only if needed**
    - If your plugin has settings (like in `SettingsToggleTest`), verify whether dataclass behavior should respect any
      toggles (e.g. automatic wrapping on/off).

---

### Concrete test cases to add

You already have:

- `testDataclassField_WithSnakeCaseName_UsesFieldNameForClass`
- `testDataclassCall_KeywordArgValue_UsesIntAndWrapsArgument` (from the part of file you showed)

You now want tests that cover **bidirectional** behavior plus more shapes of calls.

#### 1. Dataclass field → wrap all callsites (keyword)

**Goal:** When intention is invoked on the field annotation, all keyword calls are updated.

```kotlin
fun testDataclassField_WrapsKeywordArgumentUsages() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        @dataclasses.dataclass
        class D:
            product_id: i<caret>nt


        def do():
            D(product_id=123)
            D(product_id=456)
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    assertTrue(result.contains("class Productid(int):"))
    assertTrue(result.contains("product_id: Productid"))
    assertTrue(result.contains("D(product_id=Productid(123))"))
    assertTrue(result.contains("D(product_id=Productid(456))"))
}
```

#### 2. Dataclass field → wrap positional usages

**Goal:** Map positional arguments to dataclass fields and wrap correctly.

```kotlin
fun testDataclassField_WrapsPositionalArgumentUsages() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        @dataclasses.dataclass
        class D:
            product_id: i<caret>nt
            other: str


        def do():
            D(123, "x")
            D(456, other="y")
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    assertTrue(result.contains("class Productid(int):"))
    assertTrue(result.contains("product_id: Productid"))
    // positional
    assertTrue(result.contains("D(Productid(123), \"x\")"))
    // keyword
    assertTrue(result.contains("D(Productid(456), other=\"y\")"))
}
```

#### 3. Call site (keyword) → change dataclass field + wrap all usages

**Goal:** Invoking the intention on the literal inside `D(product_id=123)` updates both the field annotation and all
call sites.

```kotlin
fun testDataclassCall_IntroduceFromKeywordValue_UpdatesFieldAndAllUsages() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        @dataclasses.dataclass
        class D:
            product_id: int


        def do():
            D(product_id=12<caret>3)
            D(product_id=456)
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    assertTrue(result.contains("class Productid(int):"))
    assertTrue(result.contains("product_id: Productid"))
    assertTrue(result.contains("D(product_id=Productid(123))"))
    assertTrue(result.contains("D(product_id=Productid(456))"))
}
```

#### 4. Call site (positional) → change dataclass field + wrap usages

```kotlin
fun testDataclassCall_IntroduceFromPositionalValue_UpdatesFieldAndUsages() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        @dataclasses.dataclass
        class D:
            product_id: int
            other: str


        def do():
            D(12<caret>3, "a")
            D(456, "b")
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    assertTrue(result.contains("class Productid(int):"))
    assertTrue(result.contains("product_id: Productid"))
    assertTrue(result.contains("D(Productid(123), \"a\")"))
    assertTrue(result.contains("D(Productid(456), \"b\")"))
}
```

#### 5. Avoid double-wrapping if already wrapped

```kotlin
fun testDataclassCall_DoesNotDoubleWrapIfAlreadyCustom() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        class Productid(int):
            pass


        @dataclasses.dataclass
        class D:
            product_id: int


        def do():
            D(product_id=Productid(12<caret>3))
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    // depending on desired behavior: either no-op or smart merge
    assertFalse(result.contains("Productid(Productid(123))"))
}
```

#### 6. Default value field (optional, nice-to-have)

Ensure defaults are respected and calls are still wrapped correctly.

```kotlin
fun testDataclassField_WithDefault_WrapsUsagesOnly() {
    myFixture.configureByText(
        "a.py",
        """
        import dataclasses


        @dataclasses.dataclass
        class D:
            product_id: i<caret>nt = 0


        def do():
            D()
            D(product_id=123)
        """.trimIndent()
    )

    myFixture.doHighlighting()
    val intention = myFixture.findSingleIntention("Introduce custom type from int")
    myFixture.launchAction(intention)

    val result = myFixture.file.text
    assertTrue(result.contains("class Productid(int):"))
    assertTrue(result.contains("product_id: Productid = Productid(0)"))
    assertTrue(result.contains("D()"))
    assertTrue(result.contains("D(product_id=Productid(123))"))
}
```

---

### Summary

- **Plan**: extend the intention to understand dataclass fields and constructor calls in both directions (from field and
  from call-site). Always synchronize the dataclass field annotation with the new custom type and **wrap all
  corresponding arguments** in `D(...)` calls.
- **Tests**: add targeted tests that:
    - Verify the new custom type name is derived from the field/keyword name (`product_id` → `Productid`).
    - Ensure both keyword and positional calls are wrapped.
    - Ensure that starting from either the field or a call-site literal results in consistent transformations.
    - Cover edge cases like multiple calls, defaults, and avoiding double wrapping.

These tests will enforce that “when adding a type at either place, we perform wrapping at all usages so that we do not
see a type error there.”