# Type Mismatch Intentions

These intentions help you quickly fix type mismatches by wrapping or unwrapping expressions with the expected type constructor.

## Wrap with Expected Type

Automatically wraps an expression that causes a type mismatch with the expected type constructor.

**Trigger**: Type mismatch between an expression and the expected type (determined by type hints).

=== "Before"
    ```python
    def get_user(user_id: UserId) -> User:
        pass

    get_user(123)  # (1)
    ```

    1. Caret here, invoke intention (++alt+enter++)

=== "After"
    ```python
    get_user(UserId(123))
    ```

### Supported Scenarios
- **Single types**: `value` → `ExpectedType(value)`
- **Union types**: Shows a popup to choose which type from the union to use for wrapping.
- **Container elements**: Wrap specific items in list, set, or tuple literals.

## Wrap Items with Expected Type

Performs batch wrapping for all items in a container literal.

=== "Before"
    ```python
    def process_users(ids: list[UserId]) -> None:
        pass

    process_users([1, 2, 3])
    ```

=== "After"
    ```python
    process_users([UserId(1), UserId(2), UserId(3)])
    ```

**Supported Containers**: Lists, Sets, Tuples.

## Unwrap to Expected Type

Removes unnecessary wrappers when the inner value matches the expected type.

=== "Before"
    ```python
    def save_to_db(user_id: int) -> None:
        pass

    save_to_db(UserId(123))
    ```

=== "After"
    ```python
    save_to_db(123)
    ```

## Unwrap Items to Expected Type

Performs batch unwrapping for all items in a container literal.

=== "Before"
    ```python
    def process_raw_ids(ids: list[int]) -> None:
        pass

    process_raw_ids([UserId(1), UserId(2)])
    ```

=== "After"
    ```python
    process_raw_ids([1, 2])
    ```
