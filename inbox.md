# Inbox

## Pending Tasks

- [ ] Do not offer wrap if unwrap does the job:
    ```
    class CustomInt(int):
        pass

    val: list[int] = [
    CustomInt(1),
    ]
  ```
- [x] Refactor unwrap:
    - Refactor `analyzeAtCaret` in `UnwrapToExpectedTypeIntention`
    - Unify Type Checking Mechanisms
    - Consolidate `CONTAINERS` exclusion
    - Standardize `isAvailable` Boilerplate
- [ ] Refactor wrap:
  - 
- [ ] Refactor custom type:
  - [ ]]
- [x] Bug: Offers wrap with int: `va<caret>l: dict[str, int] = dict(a=1, b=2, c=3)`
- [x] Assert on complete text: testDict_GeneratesGenericCustomTypeAndKeepsArguments
- [x] Assert on complete text: testList_GeneratesCustomType
- [x] Move heavy test base out of wrap
- [ ] Unwrap if inner type is expected type
- Container types: list items

```python
from typing import NewType

CloudId = NewType("CloudId", str)

val: list[CloudId] = [ < caret > "abc"]
```

- Container types: list function argument

```python
from typing import NewType

CloudId = NewType("CloudId", str)


def do(arg: list[CloudId]) -> None:
    print(arg)


do(["abc"])
```

## In Progress Tasks

## Completed Tasks

- [x] Extract helpers between intentions
- [x] Use actual values in preview
- [x] Perform imports
- [x] Wrap correctly if function argument has unnecessary parentheses
