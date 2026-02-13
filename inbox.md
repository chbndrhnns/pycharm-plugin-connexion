# Inbox

## Pending Issues

- [ ] fix: Go to declaration fails

```
import pytest


@pytest.fixture
def othername2(ot<caret>hername2):
    return othername2

def test_new(othername2):
    pass
```

- [ ] fix: Copy annotations loses *args, **kwargs, /, *
- [ ] fix: Copy annotations should render the line on the function name, not arguments
- [ ] fix: Investigate `parse_data_fn=lambda <caret>x: x` in 261
- [ ] fix: No Python SDK configured should be displayed directly in pytest explorer, or greyed out?
- [ ] fix: Rendering of links for pytest, now blue and dotted.
  `tests/unit/test_integrity.py::test_no_skip_markers_on_adapter_fixture[<class 'src.adapters.outbound.HttpRepository'>] ✓                51% █████▏`
- [ ] fix: Introduce custom type needs to wrap usage sites
- [ ] fix: Logger should distinguish between packages and classes
- [ ] fix: Add test which reads all logging-related classes and marks invalid
- [ ] fix: In patch, we should allow references to declarations sites
- [ ] fix: When sorting navbar items, ignore the async keyword
- [ ] fix: Wrap with builtin type callable in these cases:

```
def do(data: list[int]): ...


do(frozenset({<caret>1, 2, 3}))
```

- [ ] fix: Only offer jump to test tree node if there is a jump target available, add gutter icon
- [ ] fix: FP: We are marking directories as missing root prefix when referenced from test. 
- [ ] fix: Should also offer Wrap: `do(Path("a<caret>bc"))` (253 vs 261?)
- [ ] refactor: PyUnresolvedReferenceAsErrorInspection, lots of strings (?)
- [ ] refactor: Use module name in PyPackageRunLineMarkerContributor, also strip __main__, other special cases?
- [ ] refactor: Is doing PyAllExportUtil too much string magic?
- [ ] refactor: why do we need a different check during tests? `PyWrapHeuristics.isProtocol`
- [ ] fix: When prefering relative import inside same package, do not add candidate if relative import exists already
- [ ] fix: implement abstract method is put above __init__
- [ ] fix: cancel introduce type rename dialog still keeps it -> platform issue
- [ ] fix: Import `import bla as xyz` triggers warning about not exported in all -> repro?
- [ ] fix: How to handle ProcessCanceledException correctly?

## Pending Features

- [ ] feat: Parse class names in errors from anyio.TaskGroup
- [ ] feat: Recognize methods like `assert_called_once_with` in mocks
- [ ] feat: Copy with inspection results in xml format
- [ ] feat: Attach run node context menu in pytest explorer
- [ ] feat: Only discover tests when SDK is set up
- [ ] feat: Also, render tests different based on their skip/skipif markers
- [ ] feat: Manage markers in new tab
- [ ] feat: Navigate to request fixture
- [ ] feat: Generate module-level logger
- [ ] feat: Do not move logger when moving code
- [ ] feat: Suggest to toggle `self` when moving a code block which is a function inside a class
- [ ] feat: Unwrap try/except block (consider finally!)
- [ ] feat: For unresolved logger, offer to add logging skeleton on top
- [ ] feat: pytest fixtures: Add rename option to rename not in hierarchy
- [ ] feat: Copy special should offer copy single node id/reference (no children)
- [ ] feat: In __all__, offer to delete item + import
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-66243/Refactor-Inline-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-60973/Detect-invalid-fixture-names
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-66245/Does-not-render-documentation-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-53176/Renaming-fixture-does-not-work-from-inside-of-test-method
- [ ] 
  feat: https://youtrack.jetbrains.com/issue/PY-60483/Differentiate-between-tests-and-fixtures-in-file-structure-view
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-56186/Support-Push-Down-Up-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-56268/Go-to-type-declaration-does-not-work-for-fixture-arguments
- [ ] feat: Qualify imports using last segment, available on import statements
- [ ] feat: Convert bunch of assert statements to dict and vice versa
- [ ] feat: Go to declaration in new tab
- [ ] feat: Find usages in current file
- [ ] feat: Add field to dataclass/typeddict/pydantic if missing
- [ ] feat: Iterate by warning type through IDE errors
- [ ] feat: Search in changed files only
- [ ] feat: Prefer relative imports also when using move refactoring, blocked
  by https://youtrack.jetbrains.com/issue/PY-86616/PyMoveModuleMembersProcessor-does-not-emit-events
- [ ] refactor: Use `PythonDependenciesManager` if
  public, https://youtrack.jetbrains.com/issue/PY-86636/Make-PythonDependenciesManager-availabe-for-plugins
- [ ] refactor: Do not use reflection to filter import suggestions
- [ ] feat: Discover if pydantic v2 is installed and show parameter object option only in this case
- [ ] feat: If callable is expected, complete without parens
- [ ] feat: Transform between lambda, function, partial
- [ ] feat: In case of unexpected type, offer cast to the expected type
- [S] feat: Turn dict into dataclass/pydantic model (spec/upgrade-dict.md)
- [ ] feat: Convert parametrized test to multiple unparametrized tests
- [ ] feat: Add field to dataclass/model in case of unexpected argument
- [ ] feat: Suggest expected type for container types, like `vals: set[MyType] = set(<caret>)`
- [ ] feat: When suggesting the expected type and it is an inner class, reference it using outer_class.inner_class
  syntax, recursively
- [ ] feat: Ignore third-party packages in import suggestions
- [ ] feat: Move symbol to top level (or one scope up?)
- [ ] feat: Add pytest nodes to separate view in search everywhere (currently, `pytest` seems not respected)
- [ ] feat: When implementing abstract method, present dialog to choose or show edits
- [ ] feat: Move instance method
- [ ] feat: Move to inner class
- [ ] feat: Move symbol to any of the currently imported packages/modules, change references
- [ ] feat: Inspect duplicates in __all__
- [ ] feat: copy element with dependencies should generate a preview about the direct copy (not including dependencies)
- [ ] When populating arguments, highlight the target function (area) when the popup appears
- [ ] feat: quick fix to remove unresolved import from __all__
- [ ] feat: Turn into type checking import
- [ ] roots: adopt when moving files
- [ ] feat: Wrap default/default_factory after introducing custom type in dataclass/pydantic
- [ ] feat: Convert fn to lambda
- [ ] commit: if it fails for hooks, add option keep selection of files (docs/keep-commit-selection/spec.md, stash)
- [ ] feat: Collect symbols to check against for stdlib clashes dynamically
- [ ] feat: Support what is tested in _testNestedConstructor_InsideDict_WrapsInnerArgument

## In Progress Tasks

- [-] feat: https://youtrack.jetbrains.com/issue/PY-55931/Add-quickfix-Convert-between-fixture-injection-variants
- [ ] feat: Block list: Do not allow names from typing or collections.abc.

## Completed Tasks (newest first)
