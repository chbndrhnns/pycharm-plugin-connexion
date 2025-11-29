# Populate intention tests

Tests in this package cover intentions for populating arguments.

**Required arguments**

- `RequiredArgumentsIntentionTest` – core behavior (only required fields).
- `RequiredArgumentsSettingsToggleTest` – settings behavior.

**Keyword-only arguments**

- `KwOnlyArgumentsIntentionTest` – behavior for keyword-only arguments.
- `KwOnlyArgumentsSettingsToggleTest` – settings behavior.

**Recursive arguments**

- `RecursiveArgumentsIntentionTest` – recursive population behavior.
- `RecursiveArgumentsPydanticTest` – Pydantic-specific recursive behavior.
- `RecursiveArgumentsImportTest` – imports added during recursive population.
