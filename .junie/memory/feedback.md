[2025-12-31 18:28] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "failing tests",
    "EXPECTATION": "Deliver a complete implementation that makes all related tests pass.",
    "NEW INSTRUCTION": "WHEN tests fail or remain red THEN investigate failures and fix code until green"
}

[2025-12-31 20:41] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "failing tests",
    "EXPECTATION": "Deliver a complete implementation that makes all related tests pass.",
    "NEW INSTRUCTION": "WHEN tests fail or remain red THEN investigate failures and fix code until green"
}

[2026-01-01 09:55] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "availability and menu placement",
    "EXPECTATION": "The action should be enabled on a module-level pytest test and appear in the 'Refactor This' context menu.",
    "NEW INSTRUCTION": "WHEN user reports action greyed out or missing from menu THEN review isAvailable logic and menu registration"
}

[2026-01-01 09:58] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "PSI write action",
    "EXPECTATION": "PSI modifications must be performed within a write command (WriteCommandAction/CommandProcessor).",
    "NEW INSTRUCTION": "WHEN changing PSI in handlers or intentions THEN wrap edits in WriteCommandAction.runWriteCommandAction"
}

[2026-01-01 10:00] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "intention menu placement",
    "EXPECTATION": "Do not expose this feature via the intention (Alt+Enter) menu; it should be accessible via 'Refactor This'.",
    "NEW INSTRUCTION": "WHEN user requests removal from intention menu THEN unregister intentionAction and register under Refactor This"
}

[2026-01-01 10:28] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "method insertion nesting",
    "EXPECTATION": "When adding to an existing class, the new test must be a top-level method (not nested inside another method) with correct indentation and spacing.",
    "NEW INSTRUCTION": "WHEN adding to existing class THEN addAfter last top-level statement in statementList"
}

[2026-01-01 10:39] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "empty body handling",
    "EXPECTATION": "Preserve placeholder statements ('...' or 'pass') so transformed tests remain syntactically valid.",
    "NEW INSTRUCTION": "WHEN converting a function to a method and body becomes empty THEN preserve '...' or insert 'pass'"
}

