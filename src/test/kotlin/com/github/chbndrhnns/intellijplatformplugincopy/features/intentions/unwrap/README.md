# Unwrap intention tests

All tests in this package cover `UnwrapToExpectedTypeIntention`.

**Behavioral tests**

- `UnwrapBasicTest` – basic unwrapping behavior for NewType-like wrappers in
  assignments, arguments, returns, and simple container cases.
- `UnwrapContextBreadthTest` – unwrapping in various contexts (yield, comprehension, returns).
- `UnwrapItemsTest` – unwrapping items inside collections.
- `UnwrapSettingsToggleTest` – settings behavior.

**UI / presentation tests**

- (Add e.g. `UnwrapPresentationTest` later if you need tests for intention
  text, previews, or chooser labels.)
