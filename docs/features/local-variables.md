# Local Variables

Tools for extracting expressions into local variables.

## Create Local Variable

Extracts the selected expression into a local variable.

**Features**:
- Automatically suggests variable names based on the expression.
- Includes type inference and adds type annotations where appropriate.

=== "Before"
    ```python
    print(calculate_complex_value() * 2)
    ```

=== "After"
    ```python
    result = calculate_complex_value()
    print(result * 2)
    ```

### With Type Annotation

=== "Before"
    ```python
    users = get_users()
    print(users[0].name)
    ```

=== "After"
    ```python
    user: User = users[0]
    print(user.name)
    ```
