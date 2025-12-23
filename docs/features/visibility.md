# Visibility

Toggles Python naming conventions to change the visibility of functions, methods, and variables.

## Change Visibility

This intention toggles between public, protected/private (single underscore), and name-mangled (double underscore) prefixes.

=== "Public to Private"
    ```python
    def my_function():
        pass
    ```
    becomes
    ```python
    def _my_function():
        pass
    ```

=== "Private to Mangled"
    ```python
    def _my_method(self):
        pass
    ```
    becomes
    ```python
    def __my_method(self):
        pass
    ```

**Supported Elements**:
- Functions
- Methods
- Variables/Attributes

## Toggle Visibility

Similar to Change Visibility but typically toggles between the two most common states (Public and Private).
