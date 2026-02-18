# Actions

Beyond intentions and inspections, the plugin provides several global and context-specific actions.

## Copy Special Menu

Found under **Edit > Copy Special** or in context menus.

- **BetterPy: Copy with Dependencies**: Copies selected code along with its local dependencies and imports.
- **Copy Package Content**: Copies the content of an entire Python package.
- **Copy Build Number**: Copies the current IDE build number (useful for bug reports).
- **Copy Fully Qualified Names (FQN)**: Copies the full import path of the selected symbol(s).

## Pytest Tree Actions

Available when right-clicking nodes in the Pytest results tool window.

- **Copy Pytest Node IDs**: Useful for running specific tests from the command line.
- **Copy Stacktrace**: Provides a cleaned-up version of the failure stacktrace.
- **Toggle Pytest Skip**: Skips or unskips the selected test(s).

## Navigation Actions

- **Jump to Pytest Node**: Navigates from a test function in the editor to its result in the test tree.
