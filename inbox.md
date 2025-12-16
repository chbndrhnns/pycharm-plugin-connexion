# Inbox

## Pending Issues

- [ ] fix: When prefering relative import inside same package, do not add candidate if relative import exists already
- [ ] fix: implement abstract method is put above __init__
- [ ] feat: Consider if this should only work for dataclass/classes, for others, there is change signature
- [ ] fix: cancel introduce type rename dialog still keeps it
- [ ] refactor: How to find if the caret is inside parentheses? `[PopulateArgumentsService.findCallExpression`
- [ ] fix: Import `import bla as xyz` triggers warning about not exported in all -> repro?

## Pending Features

- [ ] feat: Suppress rename to self and add "Add self"
- [ ] feat: Change visibility: make private (we only have two options and one is always true)
- [ ] feat: Convert parametrized test to multiple unparametrized tests
- [ ] feat: Add field to dataclass/model in case of unexpected argument
- [ ] feat: After return, suggest `list[T()]` if return type is a container type (?)
- [ ] feat: Suggest expected type for container types, like `vals: set[MyType] = set(<caret>)`
- [ ] feat: Support skip toggle for single parameters
- [ ] feat: pytest param: treat text as ref
- [ ] feat: Move test inside of class
- [ ] feat: When suggesting the expected type and it is an inner class, reference it using outer_class.inner_class
  syntax, recursively
- [ ] feat: "Choose implementation" should search in class names, as well.
- [ ] feat: Ignore third-party packages in import suggestions
- [ ] feat: Annotate constant with `Final[T]`
- [ ] feat: Treat unresolved references as errors
- [ ] feat: Wrap with pytest.raises()
- [ ] feat: Move symbol to top level (or one scope up?)
- [ ] feat: Add pytest nodes to separate view in search everywhere (currently, `pytest` seems not respected)
- [ ] feat: When implementing abstract method, present dialog to choose or show edits
- [ ] feat: Move instance method
- [ ] feat: Move to inner class
- [ ] feat: Move symbol to any of the currently imported packages/modules, change references
- [ ] feat: Inspect duplicates in __all__
- [ ] feat: introduce custom type as newtype, as well, or other custom constructs
- [ ] feat: Introduce type alias for union type
- [ ] feat: Turn dict into dataclass/pydantic model
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
- [ ] feat: Show red bubble if introduce parameter object is not available

## In Progress Tasks

- [x] fix: Wrap inconsistencies
  - [ ] _testNestedConstructor_InsideDict_WrapsInnerArgument
  - [x] Wrap case which failed initially

```python
import dataclasses


@dataclasses.dataclass
class Inner:
    id: int


@dataclasses.dataclass
class Outer:
    inner: Inner


val2: list[Outer] = [
    Outer(
        inner=Inner(id=1),
    )
]
```

## Completed Tasks (newest first)

- [x] fix: Should not offer custom type on keywords
- [x] fix: Wrap suggests set instead of type: `vals: set[str] = {1}`
- [x] fix: Wrap should use set literal instead of set call, `vals: set[T] = T`
- [x] feat: Enable parameter object action also if only one parameter
- [x] fix: Wrap should pick inner problem first, offers `Prefix` here

```python
self._prefixes: dict[PrefixId, Prefix] = {
    PrefixId(1): Prefix(
        id=PrefixId(1),
        prefix=IPv4Interface("10.10.10.0/24"),
        vrf=Vrf(id=1, name=VrfName("5girs_flat_int_prod")),
    )
}
```
