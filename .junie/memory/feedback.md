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

[2026-01-04 00:04] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest test URL",
    "EXPECTATION": "getTestUrl must include the package-qualified module name (e.g., 'tests.test_single_fail'), not just the bare module.",
    "NEW INSTRUCTION": "WHEN generating PyTest test URL THEN include package-qualified module path including top-level package"
}

[2026-01-04 00:17] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest test URL",
    "EXPECTATION": "getTestUrl must use the test root in the protocol and include the package-qualified module name (e.g., 'tests.test_single_fail').",
    "NEW INSTRUCTION": "WHEN building python<...> test URL THEN use test root path and package-qualified module name"
}

[2026-01-04 08:54] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "failed line parsing",
    "EXPECTATION": "Compute the failing line from pytest output when TestStateStorage.failedLine is -1.",
    "NEW INSTRUCTION": "WHEN failedLine equals -1 in TestStateStorage record THEN derive failing line using custom PyTest stacktrace parser"
}

[2026-01-04 09:01] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "failed line parsing",
    "EXPECTATION": "Compute and store the failing line for Python/pytests instead of leaving TestStateStorage.failedLine at -1.",
    "NEW INSTRUCTION": "WHEN python test run reports failure THEN parse pytest traceback and set failedLine from 'file:line'"
}

[2026-01-04 19:30] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "failedLine propagation",
    "EXPECTATION": "After updating TestStateStorage with the new failedLine, getTestInfo should read the updated value instead of -1.",
    "NEW INSTRUCTION": "WHEN updating failed line via listener THEN ensure locationUrl equals getTestUrl used by getTestInfo"
}

[2026-01-04 19:46] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "failed line still -1",
    "EXPECTATION": "Parsing and storage must result in a non -1 failedLine, written under the exact URL key that getTestInfo later reads.",
    "NEW INSTRUCTION": "WHEN writing failedLine to TestStateStorage THEN compute URL via PytestLocationUrlFactory matching getTestInfo"
}

[2026-01-04 21:00] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "partial match scope",
    "EXPECTATION": "Support partial matches for pytest node ids rather than generic function/class names.",
    "NEW INSTRUCTION": "WHEN adding partial search for pytest contributor THEN match segments of node ids (file::class::test)"
}

[2026-01-04 22:53] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "from/relative imports support",
    "EXPECTATION": "The qualify-undefined quickfix must work when modules are brought in via 'from ... import ...' and relative imports (e.g., 'from . import domain').",
    "NEW INSTRUCTION": "WHEN imported module is via from-import or relative import THEN include it as a qualifier candidate and generate correct qualifier"
}

[2026-01-04 23:06] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "absolute import qualifying",
    "EXPECTATION": "The quickfix must work when the module is imported via an absolute import (e.g., 'import domain') so the test passes.",
    "NEW INSTRUCTION": "WHEN unresolved reference and absolute import exists THEN offer qualify with that module"
}

[2026-01-05 08:25] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "quickfix UI popup",
    "EXPECTATION": "When multiple qualifying options exist, show a single action that opens a chooser popup instead of adding multiple items to the context menu.",
    "NEW INSTRUCTION": "WHEN multiple qualifier candidates are found THEN show a chooser popup from a single quickfix"
}

[2026-01-05 09:11] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "multiresolve handling",
    "EXPECTATION": "Do not pick the first resolve result; consider all candidates and only show an error if none match.",
    "NEW INSTRUCTION": "WHEN multiple resolve candidates are found THEN iterate candidates before reporting an error"
}

[2026-01-05 11:27] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "failed line update",
    "EXPECTATION": "The failing line should update to the assertion line based on the editor/pytest outcome.",
    "NEW INSTRUCTION": "WHEN pytest assertion fails in open editor THEN set failedLine to assertion line via PytestLocationUrlFactory"
}

[2026-01-05 17:30] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "refactoring dialog",
    "EXPECTATION": "Use the standard refactoring confirmation/preview dialog instead of a custom yes/no question when usages are found.",
    "NEW INSTRUCTION": "WHEN intention finds usages requiring confirmation THEN show standard refactoring preview dialog instead of custom prompt"
}

[2026-01-06 09:50] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest URL root",
    "EXPECTATION": "The angle-bracket part of the URL must be the module's content/test root (e.g., .../tests), not the project base path.",
    "NEW INSTRUCTION": "WHEN generating pytest location URL THEN use content/test root path inside angle brackets"
}

[2026-01-06 10:37] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "gutter icon behavior",
    "EXPECTATION": "Display the BetterPy gutter icon at the normal size, and clicking it should trigger the intended action.",
    "NEW INSTRUCTION": "WHEN adding or updating gutter icon THEN use betterpy 16px icon and wire GutterIconNavigationHandler"
}

[2026-01-06 10:48] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "gutter icon click",
    "EXPECTATION": "Clicking the BetterPy gutter icon should trigger the Introduce Parameter Object refactoring.",
    "NEW INSTRUCTION": "WHEN user clicks parameter object gutter icon THEN invoke refactoring handler with project, editor, file"
}

[2026-01-06 10:51] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "gutter icon condition",
    "EXPECTATION": "Hide the gutter icon when a parameter object already exists, but keep the context menu action available.",
    "NEW INSTRUCTION": "WHEN function already uses a parameter object THEN suppress gutter icon but keep action enabled"
}

[2026-01-06 10:55] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "availability range",
    "EXPECTATION": "Make 'Inline Parameter Object' available not only on the argument name but anywhere in the parameter list and on its annotation.",
    "NEW INSTRUCTION": "WHEN caret is in parameter list or its annotation THEN enable Inline Parameter Object action"
}

[2026-01-06 10:58] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "availability range",
    "EXPECTATION": "The action should be available when the caret is on the parameter’s type annotation token (e.g., within the class name in the annotation).",
    "NEW INSTRUCTION": "WHEN caret on parameter type annotation THEN enable Inline Parameter Object action"
}

[2026-01-06 11:00] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "availability range",
    "EXPECTATION": "Make the Inline Parameter Object action available in the whole parameter list and on the parameter’s type annotation token.",
    "NEW INSTRUCTION": "WHEN caret in parameter list or its annotation THEN enable Inline Parameter Object action"
}

[2026-01-06 12:31] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "usage counting semantics",
    "EXPECTATION": "Show the dialog when the parameter object class is used in more than one function; the count should reflect the number of functions using that class as a parameter.",
    "NEW INSTRUCTION": "WHEN computing usage count for inline parameter object THEN count functions accepting the parameter-object type as parameter"
}

[2026-01-06 12:32] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "usage counting logic",
    "EXPECTATION": "Count how many functions accept the parameter-object class as a parameter type; in the example, the count should be 2.",
    "NEW INSTRUCTION": "WHEN counting usages for inline parameter object THEN count functions accepting that class as parameter"
}

[2026-01-06 12:38] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "EDT violation",
    "EXPECTATION": "Do not run blocking searches or SDK/package checks on the EDT; compute usage count off-EDT and only show the dialog on the UI thread afterward.",
    "NEW INSTRUCTION": "WHEN counting usages in refactoring handler THEN run in background and then show dialog on EDT"
}

[2026-01-06 12:40] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dialog checkbox enablement",
    "EXPECTATION": "When choosing to inline only one occurrence, the 'Remove class' checkbox should be disabled (greyed out).",
    "NEW INSTRUCTION": "WHEN user selects \"Inline this occurrence\" THEN disable and uncheck the remove-class checkbox"
}

[2026-01-06 12:42] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "EDT violation",
    "EXPECTATION": "Do not perform blocking usage counting or SDK/package checks on the EDT; run them off-EDT and only show the dialog afterward on the UI thread.",
    "NEW INSTRUCTION": "WHEN counting usages or resolving SDK in handler THEN run in background and show dialog on EDT"
}

[2026-01-06 13:57] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "RECORD fallback parsing",
    "EXPECTATION": "When top_level.txt is absent, derive top-level modules by parsing RECORD entries and collecting unique first path segments, ignoring .dist-info entries.",
    "NEW INSTRUCTION": "WHEN top_level.txt missing THEN parse RECORD, ignore *.dist-info, collect first path segments"
}

[2026-01-06 13:59] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "RECORD parsing method",
    "EXPECTATION": "Treat RECORD entries as file paths and derive the top-level module from the first path segment, not as CSV fields.",
    "NEW INSTRUCTION": "WHEN top_level.txt missing and parsing RECORD entries THEN use first segment before '/'"
}

[2026-01-06 17:22] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest URL module qualification",
    "EXPECTATION": "Generate pytest location URLs whose node id includes the package-qualified module name (e.g., 'tests.test_.test_'), so they match stored diff keys.",
    "NEW INSTRUCTION": "WHEN generating pytest function location URL THEN prefix node id with package-qualified module name"
}

[2026-01-06 17:35] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest URL module qualification",
    "EXPECTATION": "Generate pytest location URLs whose node id includes the package-qualified module name (e.g., 'tests.test_.test_') so they match stored diff keys.",
    "NEW INSTRUCTION": "WHEN generating pytest function location URL THEN include package-qualified module in node id"
}

[2026-01-06 17:52] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest URL module qualification",
    "EXPECTATION": "Location URLs must use a node id with the package-qualified module name (e.g., 'tests.test_.test_') so it matches stored diff keys.",
    "NEW INSTRUCTION": "WHEN generating pytest function location URL THEN include package-qualified module name in node id"
}

[2026-01-06 20:36] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "context menu gating",
    "EXPECTATION": "Only show the context menu item when diff data is available from the test panel tree’s actual outcome.",
    "NEW INSTRUCTION": "WHEN opening test panel context menu THEN show item only if selected node has diff data"
}

[2026-01-06 21:01] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "availability checks",
    "EXPECTATION": "Show the context menu action only when a test diff is available and the actual value differs from the asserted value, matching the intention’s conditions.",
    "NEW INSTRUCTION": "WHEN deciding visibility for 'Use actual outcome' action THEN require diff present and value differs from assert"
}

[2026-01-06 22:21] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "settings toggle",
    "EXPECTATION": "Provide a settings toggle for the inspection enforcing source root prefixes on imports.",
    "NEW INSTRUCTION": "WHEN modifying strict source root import inspection THEN add settings toggle to enable or disable strict prefix inspection"
}

[2026-01-06 22:55] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "gutter icon behavior",
    "EXPECTATION": "Display the BetterPy gutter icon at the normal size, and clicking it should trigger the intended action.",
    "NEW INSTRUCTION": "WHEN adding or updating gutter icon THEN use 16px BetterPy icon and wire navigation handler"
}

[2026-01-07 07:39] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "gutter icon behavior",
    "EXPECTATION": "Display the BetterPy gutter icon at normal size and wire click to the intended action.",
    "NEW INSTRUCTION": "WHEN adding or updating gutter icon THEN use BetterPy 16px icon and GutterIconNavigationHandler"
}

[2026-01-07 08:30] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "failed line source",
    "EXPECTATION": "Use the line number from TestOutcomeDiffService.kt when updating highlight-failed-line.md.",
    "NEW INSTRUCTION": "WHEN updating highlight-failed-line.md THEN read line number from TestOutcomeDiffService.kt"
}

[2026-01-07 14:21] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "IntelliJ API misuse",
    "EXPECTATION": "Use the actual LogLevelConfigurationManager API from this SDK version instead of guessing members like 'categories', 'addCategories', or 'setCategories', and ensure the code compiles.",
    "NEW INSTRUCTION": "WHEN LogLevelConfigurationManager members are unresolved THEN inspect SDK class and adapt to available API"
}

[2026-01-07 14:28] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "SDK API usage",
    "EXPECTATION": "Implement FeatureLoggingService using the actual LogLevelConfigurationManager API instead of guessed members.",
    "NEW INSTRUCTION": "WHEN compiler flags unresolved IntelliJ API members THEN inspect SDK classes and adapt to available methods"
}

[2026-01-07 14:33] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "logging API mismatch",
    "EXPECTATION": "Implement FeatureLoggingService using the actual LogLevelConfigurationManager API so it compiles and manages categories correctly.",
    "NEW INSTRUCTION": "WHEN manipulating debug log categories THEN use state.categories and addCategories/setCategories"
}

