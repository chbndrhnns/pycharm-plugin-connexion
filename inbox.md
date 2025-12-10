# Inbox

## Pending Issues

- [ ] fix: Add pytest nodes to separate view in search everywhere (currently, `pytest` seems not respected)
- [ ] fix: cancel introduce type rename dialog still keeps it
- [ ] refactor: How to find if the caret is inside parentheses? `[PopulateArgumentsService.findCallExpression`

## Pending Features

- [ ] feat: search everywhere: search for partial matches in test names
- [ ] feat: copy element with dependencies should generate a preview about the direct copy (not including dependencies)
- [ ] When populating arguments, highlight the target function (area) when the popup appears
- [ ] feat: quick fix to remove unresolved import from __all__
- [ ] feat: Turn into type checking import
- [ ] roots: adopt when moving files
- [ ] roots: mark as unresolved reference without
- [ ] feat: populate union (docs/populate/populate-union.md)
- [ ] feat: Generate dataclass, basemodel
- [ ] feat: Create run config using package mode
- [ ] Copy pydantic mock?
- [ ] feat: Wrap default/default_factory after introducing custom type in dataclass/pydantic
- [ ] feat: Render dataclass fields in preview
- [ ] feat: Convert fn to lambda
- [ ] feat: Block list: Do not allow names from typing or collections.abc.
- [ ] feat: Configure default base classes
- [ ] commit: if it fails for hooks, add option keep selection of files (docs/keep-commit-selection/spec.md, stash)

## In Progress Tasks

- [ ] feat: Move "copy with dependencies" intention to copy special
- [-] feat: Configure dunder all inspection to be only available if __all__ is present
- [ ] feat: Replace expected with actual test outcome
- [ ] feat: Jump from test to test node

## Completed Tasks (newest first)

- [x] feat: Add intention: make parameter optional (`arg: str` -> `arg: str | None = None`)
- [x] fix: do not offer "use exported" if in private child package: `from .._bases import BaseBlaModel`
- [x] fix: do not offer make private/public on test methods and test classes
- [x] feat: Use "change visibility" intention for private/public
- [x] feat: Copy names of failed tests
  - [x] Validate FQN
  - [x] Validate pytest node ids
  - [x] Copy with stacktrace
- [x] feat: Treat references as PSI elements, pytest.mark.filterwarnings, patch()
- [x] feat: Copy selection with imports
- [x] feat: populate with argument names that exist in the current scope
- [x] feat: filter structure view by private/public
- [x] feat: Add local variable from unresolved reference
- [x] Unsupported python version appears too early, when no interpreter is set up
- [x] fix: Remove deprecated usage: `FilenameIndex.getFilesByName`
- [x] feat: exception: if using err/exc/error, offer `except bla as X`
- [x] feat: exception: wrap list with parentheses to resolve syntax error
- [x] feat: Parse pytest node ids in the terminal as links
- [x] feat: Parse pytest identifier in search all dialog
- [x] feat: Support export of attributes
- [x] Offer unwrap for redundant cast

```python
def test_():
  val: int = 1
  return int(val)
```

- [x] wrap: Do not offer t.Final -> Wrap with Typevar
- [x] unwrap: do not offer here `access_level: str = group_access.get("access_level")`
- [x] feat: Transform between dict access .get and []
  - [x] refactor `PyDictGetToTryExceptIntention`,
- [x] fix: do not offer introduce custom type on string annotation
- [x] feat: Guard required python version, maybe with settings note?
- [x] fix: ignore conftest when suggesting __all__
- [x] fix: introduce paramter object should only be available when caret on: function, function args, call site the same
- [N] fix: do not offer introduce type if unresolved reference on statement: `MY_CONSTANT: Final[str] = "VALUE"`]
- [x] fix: introduce type fails for `MY_CONSTANT: Final[str] = "VALUE"`]
- [x] fix: populate arguments creates extra spaces when used on decorator
- [x] fix: do not offer introduce type on import statement (constant,eg)
- [x] fix: Update annotation when introducing custom type

```python
d: dict[str, int] = {}
val = d["<caret>abc"]
```

- [x] fix: Make visibility intention only available on names
- [x] feat: Copy build number
- [x] feat: Introduce parameter object (docs/parameter-object/state.md)
  - [x] refactor: String and imports handling in PyIntroduceParameterObjectProcessor
  - [x] check if there is a better way for `addDataclassImport`
  - [x] refactor: updateCallSites
  - [x] Add to Refactoring this context menu
  - [x] fix: investigate strange `.trimIndent() + "\n\n",` in testMethodIntroduceParameterObject
  - [x] Use kwargs by default for dataclass
  - [x] Refactor tests to include the interceptor
  - [x] Deal with union types and annotated and forward refs
  - [x] Deal with *args, **kwargs, *, / correctly
  - [x] fix: no name conflicts if same class name exists
  - [x] Add to Refactoring menu
- [x] fix: Populate should perform imports for leaf node types
- [x] refactor: PopulateArgumentsService
- [x] feat: When populating parameters, offer the quickfix only when inside the parentheses
- [x] feat: When populating parameters, ignore any starting with an underscore
- [N] feat: Do not show popup when populating required args only
- [x] test normal classes when introducing types
- [x] Make private/public
- [x] fix: make quick fix appear on whole import statement if single one? `from domain._other import MyStr`
- [x] refactor: More PSI-based helpers
- [x] fix: FP export: `from prance import BaseParse<caret>r`
- [x] refactor:
  `com.github.chbndrhnns.intellijplatformplugincopy.inspections.PyPrivateModuleImportInspection.buildVisitor`
    - Use either node.fromImports, node.importBlock or node.importtargets
- [x] Generate TypeEvalContext once, reuse
- [x] fix: Do not rename assignment target when introducing custom type
- [x] fix: populate nothing if arguments there already: `map(str, range(5))`
- [x] fix: Do not offer populate... if only kwargs/args(?)
- [N] refactor: How do add class name to dataclass preview?
- [x] Suggest most specific import if parent package is same: Revisit test
- [x] refactor: Better way for `isEnumAssignment`?
- [x] refactor: PyDataclassMissingInspection
- [x] feat: Inspect dataclass subclasses for @dataclass decorator

```python
import dataclasses


@dataclasses.dataclass
class Base: ...


class Child(Base):
    field: int


def test_():
    Child()
```

- [x] fix: Do not offer custom type on docstring
- [x] feat: Filter usages by type annotations
- [x] fix: Export newtype
- [x] feat: Suggest most specific import if parent package is same
- [x] feat: Suggest import from package instead of module if exported
- [x] fix: Fixed depth of 10 when resolving references
- [x] Use PSI helpers to find isinstance checks (look for more opportunities)
- [x] feat: Wrap when Indirect reference
- [x] feat: Wrap for enum should use variant instead of calling
- [x] fix: Do not offer custom type in loop variable
- [x] fix: Do not offer custom type for isinstance checks
- [x] fix: Do not make available when parsing error in file
- [x] ignore_testParameterAnnotationUpdateWrapsCallSites
- [x] Restore roots prefix
- [x] fix: No wrap with Self

```python
class C:
    def do(self, val: str):
        ...

    def other(self):
        self.do("abc")

```

- [x] fix: Missing description for PyPrivateModuleImportInspection
- [x] fix: Messed up imports

```python

# missing/__init__.py
class PublicClass:
    pass


__all__ = ['PublicClass']


# missing/_mob.py
def public_<


caret > function():
pass
```

leads to:

```python
# missing/__init__.py
class PublicClass:
    pass


__all__ = ['PublicClass', 'public_function']

from ._mob import PublicClass, public_function

```

- [x] feat: Copy package as code snippet
- [x] Use exported symbol instead of private... should not be suggested in this case:

```python
# ./myp/__init__.py
__all__ = ['One']

from ._one import One


# ./myp/one.py
class One: ...


# ./myp/two.py
from ._one import One

_ = One
```

- [x] Export: Quick fix no longer available for public modules]
- [x] Export: Only add warning for export if module is private
- [x] Do not show custom type intent for docstrings

## Completed Tasks (oldest first)

- [x] Extract helpers between intentions
- [x] Use actual values in preview
- [x] Perform imports
- [x] Wrap correctly if function argument has unnecessary parentheses
- [x] introduce type should not remove function call

```
def foo(x: str):
    pass

foo(*[1])
```

- [x] Do not offer wrap if unwrap does the job:
    ```
    class CustomInt(int):
        pass

    val: list[int] = [
    CustomInt(1),
    ] 
  ```
- [x] Bug: Offers wrap with int: `va<caret>l: dict[str, int] = dict(a=1, b=2, c=3)`
- [x] Assert on complete text: testDict_GeneratesGenericCustomTypeAndKeepsArguments
- [x] Assert on complete text: testList_GeneratesCustomType
- [x] Move heavy test base out of wrap
- [x] Should not wrap content in f-string

```
abc: str = "abc"
s = f"{abc}"
```

- [x] Should wrap INSIDE of f-string

```
def do(a: str):
    return f"{a}"
```

- [x] FP: Suggests type of class variable instead of parameter name

```python
class Client:
    version: int

    def __init__(self, val: str) -> None:
        self.val = val


Client(val="abc")
```

- [x] How to deal with this: `print("a<caret>bc")`. It suggests to wrap with object()
- [x] Generate name correctly if caret is on default value:
  `def extract_saved_reels(self, output_dir: str = "saved_ree<caret>ls"):`
- [x] Disable introduce type intention at function call result

```py
def do():
    return 1


val = d < caret > o()
```

- [x] Introduce type puts it on LHS

```py
def do():
    return 1


v < caret > al = do()
```

- [x] FP: Wrap `print("abc")` with object...
- [x] FP: Populate missing: `time.sleep(1)`
- [x] Custom Type: Use field name from dataclass
- [x] Wrap: Do not wrap ellipsis
- [x] Fix testWrapWithOptionalPicksInnerTypeNotNone
- [x] Fix testWrapWithPep604UnionChoosesFirstBranch
- [x] Fix testWrapWithUnionCustomFirstPicksCustom
- [x] Remove hard-coded: `if (calleeName == "print")`
- [x] Introduce wrap hierachies for unions: builtin, stdlib, custom -> do not offer if from same group
- [x] How to handle library code? Disable introduce type for library code?
- [x] Wrap: on type inspection error OR on union type with different groups
- [x] Update tests to match complete document
- [x] Loses annotation:

```python
class CustomWrapper(str): ...


def f(x: CustomWrapper | str) -> None:
    pass


f("abc")
```

- [x] Fix PluginException Caused by PyMissingInDunderAllInspection
- [x] Create __all__ if not exists
- [x] Can we turn builtinName into a type instead of a string? -> no, too heavy
- [x] Do not inspect libraries/dependencies