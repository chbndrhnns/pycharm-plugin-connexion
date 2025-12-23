# Pytest Helpers

Specialized tools for working with pytest suites.

## Intentions

### Toggle Pytest Skip
Quickly add or remove `@pytest.mark.skip` or `@pytest.mark.skipif` decorators from tests.

### Parametrize Pytest Test
Converts a regular test function into a parametrized one, or helps you add more parameters to an existing `@pytest.mark.parametrize` decorator.

### Convert to Pytest Param
Wraps a value in a `pytest.param(...)` call, allowing you to add specific metadata like `id` or `marks` to individual test cases in a parametrized test.

### Convert to Plain Parametrize Values
The inverse of "Convert to Pytest Param": unwraps `pytest.param(...)` back to plain values.

### Replace Expected with Actual
When a test fails due to an assertion error, this intention allows you to quickly update the "expected" value in your test code with the "actual" value from the failure message.

## Test Tree Actions

The plugin adds a **BetterPy: Copy Special** menu to the pytest results tree:

- **Copy Pytest Node IDs**: Copies the full pytest node ID (e.g., `test_file.py::TestClass::test_method[param]`) for the selected tests.
- **Copy FQNs**: Copies the fully qualified names of the selected test nodes.
- **Copy Stacktrace**: Copies the clean stacktrace from a failed test.

### Other Actions

- **Toggle Pytest Skip from Test Tree**: Allows skipping/unskipping tests directly from the results view.
- **Use Actual Test Outcome**: A quick-fix action available in the test tree for failed assertions.
- **Jump to Test Tree Node**: From any test function in the editor, quickly navigate to its corresponding node in the active test results tree.
