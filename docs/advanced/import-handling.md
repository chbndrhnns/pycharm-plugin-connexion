# Import Handling

The plugin provides advanced features for managing Python imports, especially useful in large DDD projects with complex folder structures.

## Relative vs Absolute Imports

The plugin can be configured to prefer relative imports when moving code or performing refactorings within the same package.

## Restore Source Root Prefix

When copying FQNs or performing refactorings, the plugin can automatically handle source root prefixes to ensure that imports remain valid regardless of how your IDE is configured (e.g., whether the `src` folder is marked as a source root).

## Export Symbol to Target

This feature helps you move a symbol and update its imports, or "export" it through a public API module (like `__init__.py`).
