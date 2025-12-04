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

[2025-12-02 22:34] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "refactor",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN working on inspections using TypeEvalContext THEN initialize once early and pass through call graph"
}

[2025-12-02 23:43] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "refactor scope",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN refactor item \"Creating Import Statements from Text\" is in scope THEN skip it and leave import creation code unchanged"
}

[2025-12-03 11:58] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "UI popup",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN implementing populate options UI THEN use JbPopupHost list popup pattern"
}

[2025-12-03 12:49] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "write actions",
    "ERROR": "PSI modified outside write command/action",
    "NEW INSTRUCTION": "WHEN performing PSI modifications in intentions or popup callbacks THEN wrap in WriteCommandAction.runWriteCommandAction(project)"
}

[2025-12-03 19:23] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "UI popup",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN populating only required arguments THEN skip popup and perform action immediately"
}

[2025-12-03 21:07] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "populate recursion",
    "ERROR": "Default set to non-recursive for direct required population",
    "NEW INSTRUCTION": "WHEN executing PopulateRequiredArgumentsIntention THEN enable recursive population by default"
}

[2025-12-03 21:26] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention targeting",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN caret inside PyArgumentList during plan building THEN set targetElement to the argument list for blocking inspection checks"
}

[2025-12-03 21:56] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "parameter object refactor",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN implementing parameter object refactoring THEN implement defaults, keyword args, cross-file imports, and class/static method handling"
}

[2025-12-04 11:18] - Updated by Junie
{
"TYPE": "correction",
"CATEGORY": "parameter object refactor",
"ERROR": "Missing comma in generated argument list",
"NEW INSTRUCTION": "WHEN updating call sites during parameter object refactor THEN build PyArgumentList via PSI ensuring
proper commas"
}

