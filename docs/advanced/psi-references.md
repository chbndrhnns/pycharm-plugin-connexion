# PSI References & Completion

Enhanced code intelligence for specific Python libraries and patterns.

## Mock Patching

The plugin provides reference resolution and completion for `unittest.mock.patch` calls. It can resolve the target string to actual Python symbols in your project.

## Filter Warnings

Support for `warnings.filterwarnings`, providing completion and validation for warning categories and module names.

## Return Completion

Enhanced completion for `return` statements. It suggests constructor calls for the expected return type of the function.

=== "Before"
    ```python
    def get_user_id() -> UserId:
        return # (1)
    ```

    1. Completion here suggests `UserId(...)`

=== "After"
    ```python
    def get_user_id() -> UserId:
        return UserId(|)
    ```
