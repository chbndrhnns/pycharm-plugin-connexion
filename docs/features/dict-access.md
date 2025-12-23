# Dictionary Access

Tools to switch between different ways of accessing dictionary keys.

## Dict Access Intention

Toggles between bracket access `d[key]` and safe `d.get(key)`.

=== "Bracket to Get"
    ```python
    data["key"]
    ```
    becomes
    ```python
    data.get("key")
    ```

=== "Get to Bracket"
    ```python
    data.get("key")
    ```
    becomes
    ```python
    data["key"]
    ```

## Try/Except to Dict Get

Converts a verbose `try/except KeyError` block into a concise `dict.get()` call with a default value.

=== "Before"
    ```python
    try:
        value = data["key"]
    except KeyError:
        value = default
    ```

=== "After"
    ```python
    value = data.get("key", default)
    ```

## Dict Get to Try/Except

The reverse operation: converts a `dict.get(key, default)` call into an explicit `try/except KeyError` block. Useful when you want to add more logic to the error handling.
