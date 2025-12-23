# Settings

The Python BetterPy plugin is highly configurable. You can find its settings under:
**Settings/Preferences** > **Languages & Frameworks** > **Python BetterPy**.

## Feature Toggles

Almost every intention, inspection, and action can be enabled or disabled individually. This allows you to tailor the plugin to your specific workflow and avoid clutter if you don't use certain features.

## Type Wrapping Settings

- **Prefer project types**: When wrapping a value that could match multiple types (e.g., in a Union), should the plugin prioritize types defined in your project over standard library types?
- **Include stdlib types**: Should standard library types (like `Path`) be suggested as wrapping candidates?

## Parameter Object Settings

- **Default base type**: Choose the default class type used when creating a new Parameter Object. Options typically include:
    - `dataclass` (Default)
    - `pydantic.BaseModel`
    - `NamedTuple`
    - `TypedDict`

## Import Settings

- **Use relative imports**: Toggle the preference for relative imports when the plugin generates new code or performs refactorings.
- **Restore source root prefix**: Control whether the plugin should attempt to preserve/restore source root prefixes in import paths.
