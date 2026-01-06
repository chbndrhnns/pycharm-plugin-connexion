# Inbox

## Pending Issues

- [ ] fix: Add self parameter should not be offered for classmethods and staticmethods
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

- [ ] feat: Iterate by warning type through IDE errors
- [ ] feat: Search in changed files only
- [ ] feat: Prefer relative imports also when using move refactoring, blocked
  by https://youtrack.jetbrains.com/issue/PY-86616/PyMoveModuleMembersProcessor-does-not-emit-events
- [ ] refactor: Use `PythonDependenciesManager` if public
- [ ] refactor: Do not use reflection to filter import suggestions
- [ ] feat: Discover if pydantic v2 is installed and show parameter object option only in this case
- [ ] feat: If callable is expected, complete without parens
- [ ] feat: Transform between lambda, function, partial
- [ ] feat: Render quick doc for pydantic/dataclass, showing inherited members, as well
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

- [-] feat: Move: Warn if test exists in target class already
- [ ] feat: Block list: Do not allow names from typing or collections.abc.
- [ ] refactor: Performance (spec/performance.md)

## Completed Tasks (newest first)

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
