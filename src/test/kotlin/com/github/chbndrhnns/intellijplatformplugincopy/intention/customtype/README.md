# Custom Type refactoring tests

All tests in this package cover `IntroduceCustomTypeRefactoringAction`.

**Core behavior**

- `BasicTest` – broad behavior including many scenarios (ints, unions, etc.).
- `DataclassTest` – dataclass-specific behavior.
- `PydanticTest` – Pydantic model-related behavior.
- `StdLibTypesTest` – standard library types behavior.
- `ContainerTypesTest` – container and nested-container scenarios.
- `LiteralsTest` – string literal wrapping behavior.
- `AnnotationTest`, `ParameterTest`, `ReturnAnnotationTest`, `ArgumentListTest` – specific context behavior.

**Advanced / Cross-file**

- `HeavyTest` – cross-module or project-level setups (uses `HeavyTestBase`).
- `ForwardRefTest` – string annotations and forward refs.
- `LibraryTest` – library-specific availability checks.
- `IgnoreRulesTest` – suppression/ignore rules.

**Settings**

- `SettingsToggleTest` – settings behavior.
