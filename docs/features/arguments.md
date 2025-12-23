# Populate Arguments

The **Populate Arguments** intention automatically fills in function or method call arguments based on the function signature.

## Usage

Place the caret inside the parentheses of a function call and invoke the intention (++alt+enter++).

=== "Before"
    ```python
    def create_user(name: str, email: str, age: int, active: bool = True):
        pass

    create_user()  # (1)
    ```

    1. Invoke "Populate arguments" here.

=== "After"
    ```python
    create_user(name=, email=, age=)
    ```

## Supported Types

- **Regular Functions and Methods**: Uses keyword arguments for all mandatory parameters.
- **Dataclasses**: Perfectly handles `@dataclass` constructors.
- **Pydantic Models**: Supports Pydantic v1 and v2 models.
- **Named Tuples**: Works with `collections.namedtuple` and `typing.NamedTuple`.

## Smart Completion

The plugin also provides enhanced completion for arguments. If a variable with a matching name and type exists in the current scope, it will be suggested with higher priority.
