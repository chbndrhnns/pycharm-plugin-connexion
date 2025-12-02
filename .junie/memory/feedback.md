[2025-12-01 20:28] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "auto-import behavior",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN relative import path does not include parent traversal THEN prefer and add relative import candidate"
}

[2025-12-01 20:29] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "auto-import behavior",
    "ERROR": "Relative import guarded by same-parent check is too strict",
    "NEW INSTRUCTION": "WHEN import can be expressed without parent traversal THEN add and prioritize relative import candidate over absolute options"
}

[2025-12-01 21:42] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN implementing a feature or fix THEN add meaningful automated tests covering core behaviors"
}

[2025-12-01 21:43] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests execution",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN tests are created or modified THEN run the full test suite and summarize results"
}

[2025-12-01 21:50] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests execution",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN user requests to run tests THEN run full test suite and summarize results"
}

[2025-12-01 23:16] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "exports __all__",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN defining typing.NewType alias THEN add alias name to module __all__ list"
}

[2025-12-02 08:32] - Updated by Junie
{
"TYPE": "new instructions",
"CATEGORY": "tests",
"ERROR": "-",
"NEW INSTRUCTION": "WHEN tests fail after runtime-oriented changes THEN update tests to align with current behavior"
}

