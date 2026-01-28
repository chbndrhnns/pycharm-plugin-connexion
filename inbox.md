# Inbox

## Pending Issues

- [ ] fix: Introduce custom type needs to wrap usage sites
- [ ] fix: Logger should distinguish between packages and classes
- [ ] fix: Add test which reads all logging-related classes and marks invalid
- [ ] fix: copy package content should only copy files visible in the tree (not pyc)
- [ ] fix: copy package content seems to be missing recent changes, uncommitted?
- [ ] fix: Introduce parameter object: Only add Any import if required
- [ ] fix: Unsupported python version popup: Include BetterPy prefix
- [ ] fix: Unsupported python version popup: Only show for python projects
- [ ] fix: Only make parameter object available via refactoring menu
- [ ] fix: Move to class should keep async/sync statement
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
- [ ] fix: Making one symbol public via import place quickfix makes all other symbols unimported
- [ ] fix: When prefering relative import inside same package, do not add candidate if relative import exists already
- [ ] fix: implement abstract method is put above __init__
- [ ] fix: cancel introduce type rename dialog still keeps it -> platform issue
- [ ] fix: Import `import bla as xyz` triggers warning about not exported in all -> repro?
- [ ] fix: How to handle ProcessCanceledException correctly?

## Pending Features

- [ ] feat: pytest fixtures: Add rename option to rename not in hierarchy
- [ ] feat: Copy special should offer copy single node id/reference (no children)
- [ ] feat: In __all__, offer to delete item + import
- [ ] feat: Copy annotations from parent
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-66243/Refactor-Inline-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-60973/Detect-invalid-fixture-names
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-66245/Does-not-render-documentation-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-53176/Renaming-fixture-does-not-work-from-inside-of-test-method
- [ ] 
  feat: https://youtrack.jetbrains.com/issue/PY-60483/Differentiate-between-tests-and-fixtures-in-file-structure-view
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-56186/Support-Push-Down-Up-for-pytest-fixtures
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-55931/Add-quickfix-Convert-between-fixture-injection-variants
- [ ] feat: https://youtrack.jetbrains.com/issue/PY-56268/Go-to-type-declaration-does-not-work-for-fixture-arguments
- [ ] feat: Exclude testing usages in code vision,
  cf. https://www.reddit.com/r/pycharm/comments/1qjo39g/how_to_exclude_test_usages_from_pycharms_code/
- [ ] feat: Add quick enable/disable to status bar menu
- [ ] feat: Qualify imports using last segment, available on import statements
- [ ] feat: Convert bunch of assert statements to dict and vice versa
- [ ] feat: Go to declaration in new tab
- [ ] feat: Re-run test with debug logging (add --log-cli-level=DEBUG in temporary run config)
- [ ] feat: Render links to classes in output, like `<class 'src.core.mycore.domain.MyRoute'>
- [ ] feat: Find usages in current file
- [ ] feat: Add surround with... pytest.raises()
- [ ] feat: Add field to dataclass/typeddict/pydantic if missing
- [ ] feat: Iterate by warning type through IDE errors
- [ ] feat: Search in changed files only
- [ ] feat: Prefer relative imports also when using move refactoring, blocked
  by https://youtrack.jetbrains.com/issue/PY-86616/PyMoveModuleMembersProcessor-does-not-emit-events
- [ ] refactor: Use `PythonDependenciesManager` if public
- [ ] refactor: Do not use reflection to filter import suggestions
- [ ] feat: Discover if pydantic v2 is installed and show parameter object option only in this case
- [ ] feat: If callable is expected, complete without parens
- [ ] feat: Transform between lambda, function, partial
- [ ] feat: In case of unexpected type, offer cast to the expected type
- [S] feat: Turn dict into dataclass/pydantic model (spec/upgrade-dict.md)
- [ ] feat: Convert parametrized test to multiple unparametrized tests
- [ ] feat: Add field to dataclass/model in case of unexpected argument
- [ ] feat: Suggest expected type for container types, like `vals: set[MyType] = set(<caret>)`
- [ ] feat: pytest param: treat text as ref
- [ ] feat: When suggesting the expected type and it is an inner class, reference it using outer_class.inner_class
  syntax, recursively
- [ ] feat: "Choose implementation" should search in class names, as well.
- [ ] feat: Ignore third-party packages in import suggestions
- [ ] feat: Treat unresolved references as errors
- [ ] feat: Wrap with pytest.raises()
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
- [ ] feat: populate union (docs/populate/populate-union.md)
- [ ] Copy pydantic mock?
- [ ] feat: Wrap default/default_factory after introducing custom type in dataclass/pydantic
- [ ] feat: Convert fn to lambda
- [ ] commit: if it fails for hooks, add option keep selection of files (docs/keep-commit-selection/spec.md, stash)
- [ ] feat: Collect symbols to check against for stdlib clashes dynamically
- [ ] feat: Support what is tested in _testNestedConstructor_InsideDict_WrapsInnerArgument

## In Progress Tasks

- [ ] feat: Block list: Do not allow names from typing or collections.abc.
- [ ] refactor: Performance (spec/performance.md)

## Completed Tasks (newest first)

- [x] fix: make private does not consider class hierarchy
- [x] feat: Copy annotations from parent
- [x] fix: Rename fixture should rename all edges, not only upwards
- [x] fix: Should not delegate to normal override dialog if outside of class scope
- [x] fix: Override fixture should be put UNDER import block
- [x] feat: Add new -> fixture command
- [x] feat: Add new -> test command
- [x] feat: Class-like hierachy (based on directory structure) for pytest fixtures, check types
- [x] feat: Render quick doc for pydantic/dataclass, showing inherited members, as well
- [x] feat: Move: Warn if test exists in target class already
- [x] fix: replace FQN popup should also include declaration site suggestions
- [x] fix: replace FQN popup should not display items from test code
- [x] fix: replace FQN seems to pick random parts of the dotted reference
- [x] fix: Hide overloaded methods from navbar
- [x] fix: Parse these pytest lines:

```
=============================================================================================== short test summary info ================================================================================================
FAILED tests/mytest.py - AttributeError: module 'src.abc' has no attribute 'MyAttr'
```

- [x] fix: Add self parameter should not be offered for classmethods and staticmethods
- [x] fix: Do not inspect patch methods that do not come from mock module or `MockerFixture` class
- [x] feat: Inline parameter object should ask to remove/other invocations
- [x] feat: Unexport from `__all__` if no usages in project, otherwise show usages for confirmation
- [x] feat: Change visibility: make private (we only have two options and one is always true)
- [x] fix: Do not offer any feature in third-party code, library
- [N] feat: Limit go to usages search in current (sub) directories, PY-56862, seems useless to me
- [x] fix: Create local variable adds second line `MyClass`
- [x] fix: Do not inspect relative Imports for missing root prefix
- [x] feat: Narrow down search for Go to implementation by containing class name, PY-82520, specs/go-to-impl.md
- [x] feat: search everywhere: search for partial matches in pytest node ids
- [N] feat: highlight failed line in pytest test, spec/pytest/highlight-failed-line.md -> Blocked, not possible
- [x] feat: Add strict version of source roots and mark as unresolved references if missing + quickfix
- [x] fix: Navigate symbols in file should hide dunder methods
- [x] fix: Make actual outcome available via intention for failed test
- [x] feat: introduce custom type as newtype, as well, or other custom constructs
- [x] feat: Complete to FQN in string for mock.patch("")
- [x] feat: Create run config using package mode
- [x] feat: Generate dataclass, basemodel
- [x] fix: Do not hide import suggestions for symbols in the current own project (!)
- [x] fix: Do not offer builtins as expected type callables

```python
def do(arg: str | int):
    ...


def test_():
    do()
    do("")

```

- [x] fix: Tests for wrap in class are breaking -> moved to refactoring
- [x] feat: Jump to attribute for patch.object() reference
- [x] feat: Navigate symbols via footer
- [x] feat: Typing support for Mock, MagicMock, AsyncMock
