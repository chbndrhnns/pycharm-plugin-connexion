# Refactoring Features

Advanced refactorings to keep your DDD codebase clean and manageable.

## Introduce Parameter Object

Refactors multiple function parameters into a single parameter object (usually a dataclass).

**Usage**:
- Place caret on a function name or within the parameter list.
- Invoke via ++alt+enter++ ("Introduce Parameter Object") or through the **Refactor** menu (++ctrl+t++ or ++ctrl+alt+shift+t++).

=== "Before"
    ```python
    def create_order(
        customer_id: int,
        product_id: int,
        quantity: int,
        price: Decimal,
    ):
        pass
    ```

=== "After"
    ```python
    @dataclass
    class CreateOrderParams:
        customer_id: int
        product_id: int
        quantity: int
        price: Decimal

    def create_order(params: CreateOrderParams):
        pass
    ```

### Inline Parameter Object
The reverse operation is also available. It takes a parameter object and expands it back into individual parameters in the function signature and all its call sites.

## Copy with Dependencies

Copies a code block (like a function or a class) along with all its necessary dependencies (imports, referenced functions, other classes in the same file).

**Usage**:
- Select the code block you want to copy.
- Go to **Edit** > **Copy Special** > **BetterPy: Copy with Dependencies**.

This is extremely useful when moving code between modules, as it automatically brings along everything required for the code to run in its new location.

## Add Self Parameter

Quickly adds a `self` parameter to a function that should be a method, or fixes a method missing its `self` argument.

## Export Symbol to Target

Helps you move a symbol to another module and updates all references, or exports it through a specific module (e.g., adding it to an `__init__.py` and updating `__all__`).
