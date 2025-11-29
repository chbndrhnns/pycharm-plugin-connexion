# Wrap intention tests

All tests in this package cover `WrapWithExpectedTypeIntention`.

**Behavioral tests**

- `WrapBasicTest` – assignments/arguments/returns without collections, unions, forward refs or dataclasses.
- `WrapCollectionsTest` – list/set/tuple/dict behavior (parameters and literals).
- `WrapUnionChoiceTest` – how the union chooser selects and applies options (no forward refs).
- `WrapForwardRefsTest` – behavior for forward-referenced types (which candidate is applied, availability).
- `WrapParenthesesTest` – wrapping when expressions are parenthesized or nested.
- `GenericTest` – wrapping driven by generic types.
- `WrapDataclassesTest` – dataclass-specific wrapping.

**UI / presentation tests**

- `WrapPresentationTest` – intention preview text, intention label text, and union chooser labels for non-forward-ref
  cases.
- `WrapForwardRefsPresentationTest` – union chooser labels and related UI for forward-referenced types.
