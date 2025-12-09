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
    "NEW INSTRUCTION": "WHEN updating call sites during parameter object refactor THEN build PyArgumentList via PSI ensuring proper commas"
}

[2025-12-04 12:27] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests execution",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN modifying parameter object refactor logic THEN run PyIntroduceParameterObjectHighPrioTest#testClassMethod and summarize results"
}

[2025-12-04 14:06] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "action threading",
    "ERROR": "PSI/injected data requested on EDT during update",
    "NEW INSTRUCTION": "WHEN AnAction.update reads PSI_FILE or PSI elements THEN override getActionUpdateThread to return BGT"
}

[2025-12-04 14:19] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "UI action registration",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN registering a refactoring action THEN also add it to RefactoringPopupGroup to show in Refactor This"
}

[2025-12-04 14:53] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention availability",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN function has @property or @overload decorator THEN do not exclude it from intention availability checks"
}

[2025-12-04 15:34] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "test mode handling",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN processor references ApplicationManager.isUnitTestMode THEN remove branching and require explicit configuration input"
}

[2025-12-04 15:44] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN creating or updating IntroduceParameterObject tests THEN use myFixture.checkResult to assert generated code"
}

[2025-12-04 21:11] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "tests refactor",
    "ERROR": "Missed refactoring in two test classes",
    "NEW INSTRUCTION": "WHEN adding shared dialog interceptor helper THEN replace duplicated interceptors in all parameter object tests"
}

[2025-12-06 08:19] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention description",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN adding or registering an intention action THEN add intentionDescriptions resources with description.html and before/after example files"
}

[2025-12-06 11:14] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "spec maintenance",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN user requests spec checklist update THEN add - [ ] boxes and mark completed items"
}

[2025-12-06 11:32] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "tests debugging",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN test run reports failing tests THEN investigate failures and fix code or test data"
}

[2025-12-06 11:52] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "code formatting",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN performing PSI code transformations in intentions THEN reformat changed elements via CodeStyleManager"
}

[2025-12-08 10:05] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention description",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN description files missing for PyTryExceptToDictGetIntention THEN create intentionDescriptions with description.html and before/after example Python files"
}

[2025-12-08 12:15] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "visibility intentions",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN updating Make Public intention THEN restrict availability to identifier and add negative body test"
}

[2025-12-08 17:38] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention preview",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN generating intention preview THEN perform PSI edits via IntentionPreviewUtils.write and avoid WriteCommandAction"
}

[2025-12-08 17:47] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention availability",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN checking unresolved references for availability THEN only block on unresolved typing.Final"
}

[2025-12-08 18:12] - Updated by Junie
{
    "TYPE": "new instructions",
    "CATEGORY": "intention behavior",
    "ERROR": "-",
    "NEW INSTRUCTION": "WHEN encountering dict.get(key, default) call THEN offer conversion to try/except KeyError form"
}

[2025-12-08 20:07] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "exports __all__",
    "EXPECTATION": "Do not offer adding symbols to __all__ in conftest.py files",
    "NEW INSTRUCTION": "WHEN current file name is conftest.py THEN do not suggest adding to __all__"
}

[2025-12-09 06:48] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "commit restore banner",
    "EXPECTATION": "Also display the restore bar when the commit dialog is cancelled, since selection is lost then too",
    "NEW INSTRUCTION": "WHEN commit dialog is cancelled THEN show restore banner offering selection restoration"
}

[2025-12-09 07:33] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "commit workflow integration",
    "EXPECTATION": "Hook into the modal commit dialog lifecycle; executionEnded on CommitWorkflowListener is not invoked there",
    "NEW INSTRUCTION": "WHEN implementing selection restore for modal dialog THEN use CheckinHandlerFactory and CheckinHandler callbacks"
}

[2025-12-09 08:13] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "modal commit dialog banner",
    "EXPECTATION": "Restore banner must work in modal commit dialog; do not rely on panel being Disposable",
    "NEW INSTRUCTION": "WHEN panel is not Disposable in modal dialog THEN obtain dialog Disposable and attach banner"
}

[2025-12-09 09:49] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "intention behavior",
    "EXPECTATION": "Use whatever identifier appears after 'from' in the raise statement and apply it; only one test is needed",
    "NEW INSTRUCTION": "WHEN raise statement has 'from <identifier>' THEN offer intention using that identifier"
}

[2025-12-09 10:00] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "exception capture intention",
    "EXPECTATION": "When adding capture to an except clause with a tuple, keep the tuple intact and add 'as <identifier>' after the closing parenthesis, using the identifier from 'raise ... from <identifier>'.",
    "NEW INSTRUCTION": "WHEN except clause catches a tuple AND adding capture THEN append ' as <identifier>' after the tuple"
}

[2025-12-09 11:16] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "shortcut conflict",
    "EXPECTATION": "Remove the keyboard shortcut for 'Show Private Members' in the File Structure view to avoid conflict with Cmd+F12",
    "NEW INSTRUCTION": "WHEN adding the Structure View 'Show Private Members' filter THEN do not register or assign any keyboard shortcut"
}

