# Wrap intention tests

All tests in this package cover `WrapWithExpectedTypeIntention`.

- `WrapBasicTest` – assignments/arguments/returns without collections, unions, forward refs or dataclasses.
- `WrapCollectionsTest` – list/set/tuple/dict behavior (parameters and literals).
- `WrapUnionChoiceTest` – how the union chooser selects and displays options (no forward refs).
- `WrapForwardRefsTest` – behavior and UI for forward-referenced types.
- `WrapParenthesesTest` – wrapping when expressions are parenthesized or nested.
- `WrapGenericsTest` – wrapping driven by generic types.
- `WrapDataclassesTest` – dataclass-specific wrapping.
