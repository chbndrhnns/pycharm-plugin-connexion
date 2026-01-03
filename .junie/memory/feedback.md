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

[2026-01-01 10:54] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dialog testing approach",
    "EXPECTATION": "Do not add custom or special test logic for the dialog; use a fake/mock consistent with IntelliJ sources.",
    "NEW INSTRUCTION": "WHEN tests need to simulate dialog choices THEN use IntelliJ-style fake dialog from intellij-community"
}

[2026-01-01 11:00] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI in write action",
    "EXPECTATION": "Do not show dialogs or trigger AWT events inside a write action; run UI first, then perform PSI edits within a write command.",
    "NEW INSTRUCTION": "WHEN showing a DialogWrapper or UI THEN show outside write action and run edits afterward"
}

[2026-01-01 11:13] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI in write action",
    "EXPECTATION": "Show the dialog outside any write (or write-intent) action and only perform PSI edits within a write command after the UI completes.",
    "NEW INSTRUCTION": "WHEN showing a dialog from intention or handler THEN show it outside write action and run edits afterward"
}

[2026-01-01 11:17] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI in write action",
    "EXPECTATION": "Do not show the dialog while a write or write-intent action is running or pending; show the UI first and perform PSI edits afterward within a write command.",
    "NEW INSTRUCTION": "WHEN showing DialogWrapper from intention or handler THEN call showAndGet outside WriteIntentReadAction using invokeLater"
}

[2026-01-01 11:21] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI in write action",
    "EXPECTATION": "Do not show DialogWrapper inside a write or write-intent action; show the dialog first, then perform PSI edits within a write command.",
    "NEW INSTRUCTION": "WHEN intention needs dialog THEN show via invokeLater, then run edits in WriteCommandAction"
}

[2026-01-01 11:41] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI in write action",
    "EXPECTATION": "When invoked via 'Refactor This', the dialog must be shown outside any write or write-intent action; only PSI edits should run within a write command.",
    "NEW INSTRUCTION": "WHEN invoked from 'Refactor This' refactoring handler THEN show dialog via invokeLater, then run edits in WriteCommandAction"
}

[2026-01-01 11:48] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "test-specific logic removal",
    "EXPECTATION": "Do not include test-only branches or hooks in the wrap-in-class refactoring; adopt the standard approach instead.",
    "NEW INSTRUCTION": "WHEN production code contains test-only flags or paths THEN remove them and rely on test doubles"
}

[2026-01-01 12:11] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "test-specific logic removal",
    "EXPECTATION": "Remove any test-only flags, hooks, or branches from the wrap-in-class refactoring and adopt the standard approach with test doubles.",
    "NEW INSTRUCTION": "WHEN production code includes test-only branches or hooks THEN remove them and rely on test doubles"
}

[2026-01-02 14:28] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dialog testing approach",
    "EXPECTATION": "Use TestDialogManager.setTestDialog() to simulate dialog responses instead of checking for headless mode.",
    "NEW INSTRUCTION": "WHEN tests must control dialog outcome THEN use TestDialogManager.setTestDialog to supply response"
}

[2026-01-02 14:38] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dialog testing approach",
    "EXPECTATION": "Use TestDialogManager.setTestDialog() to simulate dialog responses instead of checking headless mode.",
    "NEW INSTRUCTION": "WHEN simulating dialog responses in tests THEN use TestDialogManager.setTestDialog without headless checks"
}

[2026-01-02 14:45] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dialog testing approach",
    "EXPECTATION": "Use TestDialogManager.setTestDialog() to simulate dialog responses in tests instead of UiInterceptors or headless checks.",
    "NEW INSTRUCTION": "WHEN simulating dialog responses in intention tests THEN set TestDialogManager.setTestDialog and reset afterward"
}

[2026-01-02 14:49] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "intention menu placement",
    "EXPECTATION": "The feature must not appear in Alt+Enter; it should only be available via 'Refactor This'.",
    "NEW INSTRUCTION": "WHEN \"Wrap test in class\" shows in intention menu THEN remove intention extension and register refactoring action"
}

[2026-01-02 15:20] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "patch target semantics",
    "EXPECTATION": "patch() target must resolve to the usage site module path, not the declaration site.",
    "NEW INSTRUCTION": "WHEN adding tests for patch target resolution THEN assert resolution matches usage site path, not declaration"
}

[2026-01-02 15:25] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "non-source-root resolution",
    "EXPECTATION": "A patch target like 'tests.test_mymock.ExternalService' should not be flagged unresolved even when 'tests' is not a marked source root.",
    "NEW INSTRUCTION": "WHEN resolving top-level patch module in project THEN allow regular content roots, not only source roots"
}

[2026-01-02 15:30] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "regression test gap",
    "EXPECTATION": "Add a test that would fail before the fix and pass after, proving the resolution works for non-source-root folders.",
    "NEW INSTRUCTION": "WHEN adding a test for a bug fix THEN make it fail pre-fix and pass post-fix"
}

[2026-01-02 15:33] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "test scope",
    "EXPECTATION": "Keep the test minimal and only verify that the patch target resolves.",
    "NEW INSTRUCTION": "WHEN testing patch target resolution THEN assert reference resolves without intentions or inspections"
}

[2026-01-02 15:38] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest import suggestions",
    "EXPECTATION": "Do not alter pytest import suggestions; remove that behavior and keep only the intention to replace a symbol with a usage-site FQN.",
    "NEW INSTRUCTION": "WHEN feature suggests imports in pytest tests THEN disable it and keep replace-with-FQN intention"
}

[2026-01-02 15:47] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "import suggestions regression",
    "EXPECTATION": "Standard import suggestions (e.g., typing.Literal) should still appear in regular modules.",
    "NEW INSTRUCTION": "WHEN file is not a pytest test THEN leave import suggestion provider behavior unchanged"
}

[2026-01-02 15:48] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "import suggestions regression",
    "EXPECTATION": "In regular modules, typing.Literal should still get a standard import suggestion from typing.",
    "NEW INSTRUCTION": "WHEN file is not a pytest test THEN do not disable import/completion providers"
}

[2026-01-02 15:52] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "relative import on move",
    "EXPECTATION": "When moving MyClass from package __init__.py to myclass.py, use a relative import (from .myclass import MyClass) and keep exports consistent.",
    "NEW INSTRUCTION": "WHEN moving symbol within same package THEN generate from .<module> import <name> and update __all__"
}

[2026-01-02 15:57] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "relative import on move",
    "EXPECTATION": "When moving MyClass from __init__.py to myclass.py, the import in __init__.py must be 'from .myclass import MyClass', not an absolute path.",
    "NEW INSTRUCTION": "WHEN moving symbol within same package THEN generate from .<module> import <name> and update __all__"
}

[2026-01-02 17:25] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "regression tests",
    "EXPECTATION": "Recent change broke RefinedReturnCompletionTest; fix it without blocking intended refined-return suggestions.",
    "NEW INSTRUCTION": "WHEN modifying expected-type filtering THEN run RefinedReturnCompletionTest and keep it green"
}

[2026-01-02 22:40] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "import filtering",
    "EXPECTATION": "Do not filter out symbols defined in the current project from import suggestions.",
    "NEW INSTRUCTION": "WHEN filtering import candidates THEN always keep symbols from the current project"
}

[2026-01-02 23:36] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "method name collision UX",
    "EXPECTATION": "Do not silently rename the method; allow the user to edit the method name and show a non-modal warning if the name is taken.",
    "NEW INSTRUCTION": "WHEN adding to existing class and method name exists THEN show editable method name and non-modal taken warning"
}

[2026-01-02 23:44] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "method name collision UX",
    "EXPECTATION": "Do not silently rename a test method when the name already exists in the target class; let the user edit the name and show a non-modal warning if the name is taken.",
    "NEW INSTRUCTION": "WHEN adding to existing class and method name exists THEN show editable method name and non-modal taken warning"
}

[2026-01-03 22:19] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "nested classes case",
    "EXPECTATION": "Investigate and fix the navigation bar behavior specifically when Python classes are nested.",
    "NEW INSTRUCTION": "WHEN issue mentions nested classes THEN add a test with an inner class and verify filtering"
}

[2026-01-03 22:38] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "navigation bar roots",
    "EXPECTATION": "Navigation bar should not show unexpected or empty root nodes; roots should be correct (project/file/class).",
    "NEW INSTRUCTION": "WHEN navigation bar shows strange root nodes THEN review getPresentableText and acceptParentFromModel to avoid null roots"
}

[2026-01-03 23:11] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "code duplication",
    "EXPECTATION": "Reduce repetitive similar checks by extracting common logic into reusable helpers.",
    "NEW INSTRUCTION": "WHEN the same condition appears in multiple places THEN extract a helper and reuse it"
}

[2026-01-03 23:37] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "toggle removal",
    "EXPECTATION": "Do not gate the strict source root behavior behind a setting; make it always on.",
    "NEW INSTRUCTION": "WHEN code checks enableStrictSourceRootPrefix THEN remove the setting and apply behavior unconditionally"
}

[2026-01-03 23:41] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "test sources coverage",
    "EXPECTATION": "Apply the inspection and quick-fix to both source roots and test source roots, not only regular sources.",
    "NEW INSTRUCTION": "WHEN determining required root for strict prefix THEN include both source and test source roots"
}

[2026-01-03 23:44] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "test sources coverage",
    "EXPECTATION": "In a test source root, importing a top-level module like 'conftest' without the root prefix should be flagged and offer a quick-fix to prepend the test root (e.g., 'tests').",
    "NEW INSTRUCTION": "WHEN importing top-level module from test source root without root prefix THEN register problem and offer prepend root quick-fix"
}

