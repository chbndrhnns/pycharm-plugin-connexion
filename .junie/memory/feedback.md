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

[2025-12-09 11:41] - Updated by Junie
{
    "TYPE": "negative",
    "CATEGORY": "tests regression",
    "EXPECTATION": "New populate-arguments feature must not break existing tests; PopulateArgumentsIntentionTest and RequiredArgumentsIntentionTest should pass",
    "NEW INSTRUCTION": "WHEN modifying PopulateArguments behavior or options THEN run related tests and fix regressions"
}

[2025-12-09 12:36] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "reference contributors",
    "EXPECTATION": "Separate PSI reference contributors per feature instead of combining them in one class",
    "NEW INSTRUCTION": "WHEN implementing a new PSI reference feature THEN create a separate PsiReferenceContributor and register it"
}

[2025-12-09 12:42] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "completion and rename",
    "EXPECTATION": "Offer code completion in string targets (e.g., mock.patch, filterwarnings) and preserve fully qualified names when renaming referenced classes, changing only the last segment.",
    "NEW INSTRUCTION": "WHEN string PsiReference represents dotted name THEN preserve qualifier on rename and provide completion variants"
}

[2025-12-09 12:50] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "dotted name resolution",
    "EXPECTATION": "Do not include .py file extensions in dotted references; use package.module.Class form",
    "NEW INSTRUCTION": "WHEN building or suggesting dotted names from files THEN drop '.py' suffix from module segment"
}

[2025-12-09 15:34] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "test tree action availability",
    "EXPECTATION": "Copy Special should be available on every test tree node and enabled when applicable, not only on the root or greyed out.",
    "NEW INSTRUCTION": "WHEN a test tree node is selected THEN show and enable Copy Special actions"
}

[2025-12-09 21:36] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest node id format",
    "EXPECTATION": "Node id must start with the file path using '/' and ending with '.py', followed by '::Class::test' (e.g., tests/test_this/test_fqn.py::TestOuter::TestInner::test_foo), not split directories with '::'.",
    "NEW INSTRUCTION": "WHEN building node id from proxy without PSI THEN derive relative file path and append symbols via '::'"
}

[2025-12-09 22:29] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "in-memory files support",
    "EXPECTATION": "Implementation should work with in-memory PSI files from myFixture.configureByText and not rely on LocalFileSystem or real files.",
    "NEW INSTRUCTION": "WHEN deriving file path for node id THEN use PSI containingFile.virtualFile.path"
}

[2025-12-09 23:42] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "copy node ids root",
    "EXPECTATION": "Triggering copy from the 'Test Results' root should include all tests' node ids.",
    "NEW INSTRUCTION": "WHEN Copy Node Ids invoked on root test results node THEN collect and copy all leaf test node ids joined by newlines"
}

[2025-12-09 23:49] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "pytest copy formats",
    "EXPECTATION": "Copying Fully Qualified Names from pytest tree should not match pytest node id format; include full test name and parameters.",
    "NEW INSTRUCTION": "WHEN copying FQN from test tree THEN output dotted qualname and include parameters"
}

[2025-12-10 00:03] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "FQN copy root",
    "EXPECTATION": "CopyFQNAction should work when invoked on the test results root and include FQNs for all test nodes, sorted.",
    "NEW INSTRUCTION": "WHEN CopyFQNAction is invoked on root node THEN collect all leaf FQNs, include parameters, sort, and copy joined by newlines"
}

[2025-12-10 00:06] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "FQN format",
    "EXPECTATION": "Fully Qualified Names must exclude pytest parameters; no bracketed [] suffix after the test name.",
    "NEW INSTRUCTION": "WHEN test name contains '[' in FQN generation THEN strip bracketed parameter suffix"
}

[2025-12-10 00:11] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "FQN deduplication",
    "EXPECTATION": "A parameterized test should appear only once in FQN output, not once per parameter.",
    "NEW INSTRUCTION": "WHEN collecting FQNs from parameterized tests THEN emit one entry without parameters and deduplicate"
}

[2025-12-10 08:21] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "action presentation warning",
    "EXPECTATION": "Do not instantiate action Presentation in constructors; supply text via plugin.xml or a PresentationSupplier.",
    "NEW INSTRUCTION": "WHEN creating or refactoring AnAction subclasses THEN avoid super text/icon; use plugin.xml"
}

[2025-12-10 15:24] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "plugin.xml menu placement",
    "EXPECTATION": "CopyBlockWithDependenciesAction should appear in the editor's Copy Special menu, not in the test tree",
    "NEW INSTRUCTION": "WHEN registering CopyBlockWithDependenciesAction THEN add only to EditorPopupMenu Copy Special group, not TestTreePopupMenu"
}

[2025-12-10 16:40] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "call site fix-up",
    "EXPECTATION": "When proceeding despite missing arguments, update each call site by adding a keyword placeholder for the now-mandatory parameter.",
    "NEW INSTRUCTION": "WHEN user confirms despite missing call arguments THEN insert 'parameter_name=...' at each affected call site"
}

[2025-12-10 20:45] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "call site fix-up messaging",
    "EXPECTATION": "The confirmation message should inform the user that call sites will be updated with an ellipsis placeholder when proceeding.",
    "NEW INSTRUCTION": "WHEN showing missing-arguments confirmation dialog THEN state call sites will use '...'' placeholders"
}

[2025-12-10 21:59] - Updated by Junie
{
    "TYPE": "preference",
    "CATEGORY": "completion prioritization",
    "EXPECTATION": "Annotated return types should appear first in completion suggestions at a return site",
    "NEW INSTRUCTION": "WHEN offering completion inside a return statement THEN prioritize annotated return-type variants above others"
}

[2025-12-11 09:14] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "refactoring availability",
    "EXPECTATION": "Introduce Parameter Object should be available when the caret is on a function/method name.",
    "NEW INSTRUCTION": "WHEN caret on function or method name identifier THEN treat as valid target for availability"
}

[2025-12-11 09:28] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "parametrize intention",
    "EXPECTATION": "Fix the ParametrizePytestTest intention implementation to satisfy the existing tests; do not modify the tests.",
    "NEW INSTRUCTION": "WHEN working on ParametrizePytestTest intention THEN fix implementation and leave tests unchanged"
}

[2025-12-11 10:31] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "non-exported symbols inspection",
    "EXPECTATION": "Inspection should trigger only for private modules; public modules must not be flagged, and a test should verify this behavior.",
    "NEW INSTRUCTION": "WHEN running non-exported symbol inspection THEN only flag symbols in private modules"
}

