# Inbox

## Pending Tasks

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


do([ < caret > "abc"])
```

## In Progress Tasks

## Completed Tasks

- [x] Extract helpers between intentions
- [x] Use actual values in preview
- [x] Perform imports
- [x] Wrap correctly if function argument has unnecessary parentheses
