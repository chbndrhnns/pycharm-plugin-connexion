# Inbox

## Pending Tasks

- [ ] ignore_testParameterAnnotationUpdateWrapsCallSites
- [ ] feat: Support export of attributes

- [ ] fix: No wrap with Self

```python
class C:
    def do(self, val: str):
        ...

    def other(self):
        self.do("abc")

```

- [ ] feat: Parse pytest identifier in search all dialog
- [ ] feat: Copy selection with imports
- [ ] Wrap default/default_factory after introducing custom type in dataclass/pydantic
- [ ] Refactor document for exports
- [ ] Make private/public
- [ ] Render dataclass fields in preview
- [ ] feat: Introduce parameter object
- [ ] feat: Convert fn to lambda
- [ ] feat: Inspect dataclass subclasses for @dataclass decorator
- [ ] Configure default base classes
- [ ] Block list: Do not allow names from typing or collections.abc.

## In Progress Tasks


## Completed Tasks (newest first)

- [x] fix: Missing description for PyPrivateModuleImportInspection
- [x] fix: Messed up imports

```python

# missing/__init__.py
class PublicClass:
    pass


__all__ = ['PublicClass']

# missing/_mob.py
def public_<caret>function():
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