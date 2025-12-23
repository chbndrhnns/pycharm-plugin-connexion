# Type Hint Helpers

Additional tools to manage and improve your Python type hints.

## Make Parameter Optional

Converts a required parameter to an optional one by adding `| None` (or `Optional[...]`) and setting the default value to `None`.

=== "Before"
    ```python
    def get_user(user_id: int):
        pass
    ```

=== "After"
    ```python
    def get_user(user_id: int | None = None):
        pass
    ```

## Make Parameter Mandatory

The inverse of "Make Parameter Optional". It removes the `| None` from the type hint and deletes the `None` default value.

## Strip Signature Type Annotations

Removes all type hints from a function signature. Useful when you want to quickly clean up or restart the typing of a complex function.

## Callable to Protocol

Converts a `Callable[[...], ...]` type hint into a structural `Protocol`. This is useful when the callable becomes complex and deserves a named, documented interface.

=== "Before"
    ```python
    def process(handler: Callable[[int, str], bool]):
        pass
    ```

=== "After"
    ```python
    class Handler(Protocol):
        def __call__(self, arg1: int, arg2: str) -> bool: ...

    def process(handler: Handler):
        pass
    ```
