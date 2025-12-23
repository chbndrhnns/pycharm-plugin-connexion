# Custom Types

These features help you transition from primitive types to domain-specific Value Objects.

## Introduce Custom Type from Stdlib

Refactors primitive type usages into new custom domain types.

**Trigger**: Builtin types (`int`, `str`, `float`, `bool`, `bytes`) in annotations, default values, or variable assignments.

=== "Before"
    ```python
    def get_user(user_id: int):  # (1)
        pass
    ```

    1. Caret on `int`, invoke intention (++alt+enter++)

=== "After"
    ```python
    class UserId(int):
        pass

    def get_user(user_id: UserId):
        pass
    ```

### Default Value Support
If a parameter has a default value, it will also be wrapped with the new type.

=== "Before"
    ```python
    def create_order(quantity: int = 1):
        pass
    ```

=== "After"
    ```python
    class Quantity(int):
        pass

    def create_order(quantity: Quantity = Quantity(1)):
        pass
    ```

### Naming Strategy
The intention automatically suggests a name based on the context (parameter name, variable name, etc.):
- `user_id: int` → `UserId`
- `order_count: int` → `OrderCount`

## Toggle Type Alias

Allows you to quickly switch between a class-based Value Object and a simple Type Alias.

=== "Before (Class)"
    ```python
    class UserId(int):
        pass
    ```

=== "After (Alias)"
    ```python
    UserId = NewType("UserId", int)
    ```
    *(Or `UserId: TypeAlias = int` depending on Python version and settings)*
