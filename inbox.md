# Inbox

## Pending Tasks

- [ ] Introduce type puts it on LHS

```py
def do():
    return 1


v < caret > al = do()
```

-
- [ ] Disable introduce type intention at function call result

```py
def do():
    return 1


val = d < caret > o()
```

- [ ] Introduce wrap hierachies for unions: builtin, stdlib, custom -> do not offer if from same group
- [ ] How to handle library code? Disable introduce type for library code?
- [ ] Wrap: on type inspection error OR on union type with different groups

## In Progress Tasks

## Completed Tasks

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
