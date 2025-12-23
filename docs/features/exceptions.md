# Exceptions

Helpful intentions for working with Python exceptions.

## Add Exception Capture

Adds an `as` clause to an `except` block to capture the exception instance.

=== "Before"
    ```python
    try:
        do_something()
    except ValueError:
        pass
    ```

=== "After"
    ```python
    try:
        do_something()
    except ValueError as e:
        pass
    ```

## Wrap Exceptions with Parentheses

Wraps multiple exception types in parentheses, which is required in Python 3. This can also fix old Python 2 style multi-exception catching.

=== "Before"
    ```python
    try:
        do_something()
    except ValueError, TypeError:
        pass
    ```

=== "After"
    ```python
    try:
        do_something()
    except (ValueError, TypeError):
        pass
    ```
