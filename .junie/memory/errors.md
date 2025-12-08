[2025-12-03 14:57] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "No value passed for newly added parameters",
    "ROOT CAUSE": "Method signatures were changed to include paramUsages/functionUsages but call sites were not updated accordingly.",
    "PROJECT NOTE": "When refactoring PyIntroduceParameterObjectProcessor to two phases (read/search then write), ensure run() computes usages in readAction and passes them to updateFunctionBody and updateCallSites.",
    "NEW INSTRUCTION": "WHEN search_replace reports 'No value passed for parameter' THEN update all call sites to supply required arguments"
}

[2025-12-03 22:23] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch: passed PyClass where String expected",
    "ROOT CAUSE": "Call site was updated to pass a PyClass but the method signature still expects a String.",
    "PROJECT NOTE": "In PyIntroduceParameterObjectProcessor.kt, change updateCallSites to accept PyClass and update all invocations accordingly.",
    "NEW INSTRUCTION": "WHEN argument type mismatch appears after refactor THEN update method signatures and all call sites"
}

[2025-12-04 11:49] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "run_test",
    "ERROR": "Intention not offered for variadic/separator signatures",
    "ROOT CAUSE": "The Introduce Parameter Object processor excludes *args/**kwargs/*/ parameters, so the intention is not available and expected transformations fail.",
    "PROJECT NOTE": "In PyIntroduceParameterObjectProcessor.collectParameters and signature rewriting, variadic params and separators are filtered out; keep them intact while grouping only selected normal parameters into the dataclass.",
    "NEW INSTRUCTION": "WHEN function has *args, **kwargs, * or / THEN preserve them and still offer the intention"
}

[2025-12-04 12:30] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "run_test",
    "ERROR": "Classmethod call arguments mapped to 'cls'",
    "ROOT CAUSE": "updateCallSites maps the first call-site argument to the first formal parameter, not skipping the implicit receiver ('cls' for classmethod), so arguments shift and params/dataclass get misplaced.",
    "PROJECT NOTE": "When reconstructing call arguments, treat 'self' and 'cls' as implicit for bound calls and exclude them from positional mapping and from the extracted set.",
    "NEW INSTRUCTION": "WHEN mapping call-site arguments for a method with first param self/cls THEN skip that parameter in positional mapping"
}

[2025-12-04 12:34] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected/actual text mismatch in test result",
    "ROOT CAUSE": "The processor doesn't detect dataclass name collisions and fails to suffix an index, producing a conflicting name.",
    "PROJECT NOTE": "Implement collision-safe naming in PyIntroduceParameterObjectProcessor.kt (e.g., generateDataclassName): check current file scope and imports for the target name; if taken, append 1, 2, ... until unique.",
    "NEW INSTRUCTION": "WHEN existing class with generated dataclass name in scope THEN append increasing index until the name is unique"
}

[2025-12-04 12:35] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected/actual text mismatch",
    "ROOT CAUSE": "The expected 'after' text includes extra trailing newline(s), causing strict comparison failure.",
    "PROJECT NOTE": "myFixture.checkResult compares text exactly; avoid appending extra blank lines in expected strings for doIntentionTest.",
    "NEW INSTRUCTION": "WHEN preparing expected 'after' text THEN remove trailing blank lines and final newline additions"
}

[2025-12-04 12:59] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected and actual text do not match",
    "ROOT CAUSE": "PopulateArguments still includes underscore-prefixed parameters, but tests expect them ignored.",
    "PROJECT NOTE": "In PopulateArgumentsService (e.g., getMissingParameters/population pipeline), filter out parameters/fields whose names start with '_' (also for dataclass constructor fields) while preserving self/cls logic.",
    "NEW INSTRUCTION": "WHEN generating argument list THEN skip parameters with names starting with \"_\""
}

[2025-12-04 13:06] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention not offered inside parentheses",
    "ROOT CAUSE": "findCallExpression does not resolve the PyCallExpression when caret is within PyArgumentList, so the intention list is empty.",
    "PROJECT NOTE": "Update PopulateArgumentsService.findCallExpression to locate the PSI element at caret, climb to PyArgumentList, then its parent PyCallExpression; when caret is in PyArgumentList set targetElement to the argument list to satisfy blocking inspection checks.",
    "NEW INSTRUCTION": "WHEN caret is inside PyArgumentList and no intention found THEN resolve call from PyArgumentList and return it"
}

[2025-12-04 13:15] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "create",
    "ERROR": "File already exists at path",
    "ROOT CAUSE": "Attempted to create an existing file instead of updating its contents.",
    "PROJECT NOTE": "docs/parameter-object/state.md already exists; update it using apply_patch or search_replace.",
    "NEW INSTRUCTION": "WHEN create reports file already exists THEN update the file using apply_patch instead"
}

[2025-12-04 14:08] - Updated by Junie - Error analysis
{
    "TYPE": "invalid context",
    "TOOL": "PyIntroduceParameterObjectAction.update",
    "ERROR": "PSI requested on EDT in update()",
    "ROOT CAUSE": "The action's update() queries injected PSI from DataContext on the EDT, violating ActionUpdateThread rules.",
    "PROJECT NOTE": "In PyIntroduceParameterObjectAction, override getActionUpdateThread() to return ActionUpdateThread.BGT and ensure PSI/data lookups happen in update() under BGT only; avoid injected PSI requests on EDT.",
    "NEW INSTRUCTION": "WHEN IDE logs '$injected$.psi.File is requested on EDT' THEN set getActionUpdateThread to BGT and move PSI lookups off EDT"
}

[2025-12-04 15:23] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Passed extra arguments to unchanged methods",
    "ROOT CAUSE": "Call sites were updated to include new parameters, but method definitions were not adjusted.",
    "PROJECT NOTE": "In PyIntroduceParameterObjectProcessor.kt, update createDataclass, updateFunctionBody, and replaceFunctionSignature to accept frozen, slots, and parameterName as added at call sites.",
    "NEW INSTRUCTION": "WHEN call site arity exceeds method parameters THEN update method signatures and implementations accordingly"
}

[2025-12-04 21:18] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "FileComparisonFailedError text mismatch",
    "ROOT CAUSE": "PopulateArguments did not import the NewType alias used in the generated value.",
    "PROJECT NOTE": "In PopulateArgumentsService.generateValue/population flow, when valStr uses an alias (e.g., MyStr(...)), also add that alias PsiNamedElement to requiredImports so PyImportService.ensureImportedIfNeeded can import it.",
    "NEW INSTRUCTION": "WHEN generated value references typing.NewType alias THEN add alias symbol to requiredImports and import"
}

[2025-12-04 21:31] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "run_test",
    "ERROR": "Unresolved reference 'doIntentionTest'",
    "ROOT CAUSE": "The new test file did not import the fixtures.doIntentionTest helper function.",
    "PROJECT NOTE": "In this repo, doIntentionTest is a top-level function in the fixtures package; test classes must import fixtures.doIntentionTest explicitly.",
    "NEW INSTRUCTION": "WHEN test code uses doIntentionTest THEN add import fixtures.doIntentionTest"
}

[2025-12-04 21:33] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "run_test",
    "ERROR": "Expected/actual text mismatch in test",
    "ROOT CAUSE": "Value generator does not handle collection types, so list[NewType] yields ellipsis instead of [Alias(...)].",
    "PROJECT NOTE": "Extend PopulateArgumentsService.generateValue to handle PyCollectionType (list/set/tuple). For list, generate an element using the inner type (including NewType alias handling/imports) and produce a singleton list like [Element(...)].",
    "NEW INSTRUCTION": "WHEN type is PyCollectionType with one element type THEN generate container with one sample element"
}

[2025-12-04 21:36] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected/actual text mismatch in test result",
    "ROOT CAUSE": "generateCollectionValue produced [MyStr(...)] but test expects [MyStr(...),] with trailing comma.",
    "PROJECT NOTE": "PopulateArgumentsService.generateCollectionValue should emit a singleton container with a trailing comma (e.g., [value,] or {value,}) to match fixture expectations and exact myFixture.checkResult comparison.",
    "NEW INSTRUCTION": "WHEN generating container with one sample element THEN append a trailing comma after the element"
}

[2025-12-04 21:41] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "rename_element",
    "ERROR": "Rename conflicts: class shadowed by constructor",
    "ROOT CAUSE": "Tried renaming the class to the same name at its declaration, which is unnecessary and triggers conflict detection.",
    "PROJECT NOTE": "To add a new setting (e.g., generateKwOnly) update IntroduceParameterObjectSettings data class content directly; no rename is required.",
    "NEW INSTRUCTION": "WHEN attempting to change class members THEN modify file content via apply_patch instead of rename_element"
}

[2025-12-06 08:01] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "search_project",
    "ERROR": "Searched in non-existent directory outside project root",
    "ROOT CAUSE": "The search path used '/Users/jo/src/test' which is not inside the repository root and does not exist.",
    "PROJECT NOTE": "Use the repository root '/Users/jo/src/pycharm-ddd-toolkit'; test sources are under src/test/kotlin and testData under src/test/testData.",
    "NEW INSTRUCTION": "WHEN search_project reports directory does not exist THEN retry using a path under the project root"
}

[2025-12-07 10:29] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "Multi-line bash command not allowed by tool",
    "ROOT CAUSE": "The bash tool rejects newline-separated commands; a multi-line heredoc script was submitted.",
    "PROJECT NOTE": "Test data for intentions must be created under src/test/testData/intention/<feature>/ within the repo root.",
    "NEW INSTRUCTION": "WHEN bash command contains newlines THEN split into multiple calls, one command per call"
}

[2025-12-07 10:32] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "create",
    "ERROR": "Post-create linter flags '<caret>' as syntax error",
    "ROOT CAUSE": "The file validator parses Python and does not recognize IntelliJ test caret markers.",
    "PROJECT NOTE": "IntelliJ intention testData files legitimately contain the literal <caret> token; these files are not meant to be valid Python for external linters.",
    "NEW INSTRUCTION": "WHEN post-create report shows Unresolved reference 'caret' THEN ignore validation and continue with tests"
}

[2025-12-08 10:31] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_project",
    "ERROR": "Search returned too many results; output truncated",
    "ROOT CAUSE": "The query term 'Action' is too broad, exceeding the tool's result limit.",
    "PROJECT NOTE": "In this repo, IntelliJ actions live under src/main/kotlin/.../actions; include package/name fragments (e.g., 'IntroduceParameterObjectAction' or 'intellijplatformplugincopy/actions').",
    "NEW INSTRUCTION": "WHEN search_project warns more than 100 results THEN refine query with specific class/package keywords"
}

[2025-12-08 12:13] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle",
    "ERROR": "Intention was available in method body; assertion expected not available",
    "ROOT CAUSE": "Target resolution treats any position inside a function/class as valid, not just the name identifier.",
    "PROJECT NOTE": "In PyToggleVisibilityIntention.findTargetSymbol/isAvailable, require the caret to be on PyFunction.getNameIdentifier or PyClass.getNameIdentifier; otherwise return false.",
    "NEW INSTRUCTION": "WHEN caret is inside function/class but not on name identifier THEN return intention not available"
}

[2025-12-08 12:30] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle",
    "ERROR": "FileComparisonFailedError: expected annotation change not applied",
    "ROOT CAUSE": "The custom type introduction wrapped the literal but did not update dict key type annotation.",
    "PROJECT NOTE": "Extend CustomTypeApplier/UsageRewriter to propagate replacements into generic annotations (e.g., dict[key, val]) when usage is a subscript key; add/update logic in customtype/UsageRewriter.kt and PlanBuilder to locate and rewrite the corresponding annotation node.",
    "NEW INSTRUCTION": "WHEN caret inside string literal used as dict key THEN update dict key annotation to custom type and wrap the literal"
}

[2025-12-08 12:32] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle (:test)",
    "ERROR": "FileComparisonFailedError: expected/actual text mismatch",
    "ROOT CAUSE": "The intention updated the literal usage but did not update the dict key type in the annotation, so the produced file differed from the expected 'after' text.",
    "PROJECT NOTE": "myFixture.doIntentionTest compares file text exactly; both usage and related annotations must be updated to match expected output.",
    "NEW INSTRUCTION": "WHEN caret is on a dict key in a subscript THEN resolve container annotation key type and update it"
}

[2025-12-08 12:33] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle",
    "ERROR": "FileComparisonFailedError in ReproductionTest",
    "ROOT CAUSE": "The intention failed to update the dict key type annotation because target detection did not map a subscription key usage to the key type in the annotated dict.",
    "PROJECT NOTE": "Update TargetDetector (src/main/kotlin/.../customtype/TargetDetector.kt) to detect when the caret is on a dict key (PySubscriptionExpression.indexExpression) and link to the key type in the variable's annotation (dict[K, V] or Mapping[K, V]).",
    "NEW INSTRUCTION": "WHEN expression is index of PySubscriptionExpression THEN resolve container annotation and select key type reference"
}

[2025-12-08 12:33] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle :test",
    "ERROR": "FileComparisonFailedError in ReproductionTest",
    "ROOT CAUSE": "The actual file content after intention did not match the expected text.",
    "PROJECT NOTE": "Open build/reports/tests/test/index.html to view the exact diff; doIntentionTest compares text exactly, including whitespace.",
    "NEW INSTRUCTION": "WHEN Gradle reports FileComparisonFailedError THEN open HTML test report to inspect diff"
}

[2025-12-08 12:36] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "get_file_structure",
    "ERROR": "File structure parsing failed for Kotlin file",
    "ROOT CAUSE": "The file structure tool cannot parse Kotlin files in this context; use direct open instead.",
    "PROJECT NOTE": "Kotlin sources under src/main/kotlin are reliably readable via the open tool; get_file_structure may not support them.",
    "NEW INSTRUCTION": "WHEN get_file_structure reports 'not possible to display' THEN open the file using open to inspect content"
}

[2025-12-08 13:06] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle (:test)",
    "ERROR": "Intention offered on from-import; expected not available",
    "ROOT CAUSE": "IntroduceCustomTypeFromStdlibIntention.isAvailable does not exclude Python import statements.",
    "PROJECT NOTE": "Add an early guard in IntroduceCustomTypeFromStdlibIntention.kt to return false when the caret is within PyImportStatement or PyFromImportStatement (PyImportStatementBase).",
    "NEW INSTRUCTION": "WHEN caret is inside PyImportStatement or PyFromImportStatement THEN return intention not available"
}

[2025-12-08 13:15] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle (:test)",
    "ERROR": "FileComparisonFailedError in decorator population test",
    "ROOT CAUSE": "Argument insertion for decorator calls leaves extra spaces, producing text mismatch.",
    "PROJECT NOTE": "In PopulateArgumentsService.populateArguments, after editing the PyArgumentList of a decorator call, invoke CodeStyleManager.getInstance(project).reformat(argumentList) (or the decorator node) to normalize spacing.",
    "NEW INSTRUCTION": "WHEN target is a decorator call PyCallExpression THEN reformat its argument list after insertion"
}

[2025-12-08 13:16] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "Gradle test",
    "ERROR": "Test diff hidden due to truncated output",
    "ROOT CAUSE": "The test failed with FileComparisonFailedError, but actual file content was only printed to stdout and the console output was truncated.",
    "PROJECT NOTE": "Gradle stores full test stdout/stderr in .output.txt as indicated by the truncation note; myFixture.checkResult compares exact text including whitespace.",
    "NEW INSTRUCTION": "WHEN test output shows truncation notice THEN grep .output.txt for ACTUAL_TEXT_START..END to get actual"
}

[2025-12-08 13:17] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "Gradle (:test) -> PopulateArgumentsService.populateArguments",
    "ERROR": "Decorator call arguments not populated; empty parentheses produced",
    "ROOT CAUSE": "The intention does not handle PyDecorator/its argument list, so no parameters are generated for @Decorator(...).",
    "PROJECT NOTE": "In PopulateArgumentsService.findCallExpression/target resolution, also support PyDecorator and its PyArgumentList; treat it like a call by resolving the decorator’s callable (__init__/__call__).",
    "NEW INSTRUCTION": "WHEN caret is inside PyDecorator or its argument list THEN resolve decorator as call and populate arguments"
}

[2025-12-08 13:24] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "undo_edit",
    "ERROR": "Undo failed; tool cannot revert the last edit",
    "ROOT CAUSE": "The undo_edit utility is not supported here and cannot roll back file changes.",
    "PROJECT NOTE": "-",
    "NEW INSTRUCTION": "WHEN undo_edit fails to revert changes THEN restore content manually using search_replace or create"
}

[2025-12-08 13:27] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "undo_edit",
    "ERROR": "Undo failed; no automatic revert available",
    "ROOT CAUSE": "The undo tool cannot revert after subsequent edits or lacks a prior snapshot.",
    "PROJECT NOTE": "-",
    "NEW INSTRUCTION": "WHEN undo_edit returns an error THEN manually revert using search_replace or reapply original content"
}

[2025-12-08 14:02] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle (:test)",
    "ERROR": "AssertionFailedError: TargetDetector returned null",
    "ROOT CAUSE": "TargetDetector.find failed to detect ExpressionTarget when RHS is annotated with Final[str] and caret on string literal.",
    "PROJECT NOTE": "Update TargetDetector.tryFromExpression (and related expected type extraction) to unwrap typing.Final[...] and use the inner builtin name (e.g., str) for matching; see PyTypeIntentions/ExpectedTypeInfo for expected-type resolution.",
    "NEW INSTRUCTION": "WHEN expected type annotation uses typing.Final[...] THEN unwrap Final and match using the inner type"
}

[2025-12-08 14:04] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "Gradle test",
    "ERROR": "AssertionFailedError: TargetDetector.find returned null for Final[str] literal",
    "ROOT CAUSE": "TargetDetector.tryFromExpression does not unwrap typing.Final[...] to the inner builtin type, so matching 'str' fails.",
    "PROJECT NOTE": "Where expected types are derived (PyTypeIntentions/ExpectedTypeInfo and TargetDetector.determineBuiltinType/findExpressionMatchingBuiltin), ensure wrappers like Final/Optional/Annotated are unwrapped to their arguments before builtin matching.",
    "NEW INSTRUCTION": "WHEN expected type annotation is typing.Final[...] THEN unwrap to inner type before matching"
}

[2025-12-08 14:04] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle :test",
    "ERROR": "TargetDetector returned null for Final[str] constant",
    "ROOT CAUSE": "TargetDetector.tryFromExpression fails to unwrap typing.Final and thus cannot match builtin 'str' at the string literal caret.",
    "PROJECT NOTE": "In target detection/expected type derivation, ensure typing.Final[...] is treated as transparent so inner type drives builtin matching.",
    "NEW INSTRUCTION": "WHEN expected type is typing.Final[...] THEN unwrap to the inner type before matching"
}

[2025-12-08 15:46] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle :test",
    "ERROR": "FileComparisonFailedError: expected/actual text mismatch",
    "ROOT CAUSE": "The intention produced code that differs from the test's expected 'after' text (likely formatting or annotation update specifics).",
    "PROJECT NOTE": "IntelliJ fixture myFixture.checkResult compares text exactly, including whitespace and newlines; inspect the Gradle test HTML report for the unified diff.",
    "NEW INSTRUCTION": "WHEN tests fail with FileComparisonFailedError THEN open build/reports/tests/test/index.html and inspect diff"
}

[2025-12-08 15:48] - Updated by Junie - Error analysis
{
    "TYPE": "run_test failure",
    "TOOL": "Gradle :test",
    "ERROR": "Could not write XML test results file",
    "ROOT CAUSE": "The failing test produced output that broke Gradle’s XML report serialization, masking the original assertion mismatch.",
    "PROJECT NOTE": "myFixture.checkResult compares text exactly; debugging by embedding full file text in exception or logs can make the Gradle XML report unwritable. Persist large debug output to a file and keep exception messages small.",
    "NEW INSTRUCTION": "WHEN Gradle says 'Could not write XML test results' THEN rerun with --stacktrace and avoid large/unsanitized exception messages"
}

[2025-12-08 15:49] - Updated by Junie - Error analysis
{
    "TYPE": "run_test failure",
    "TOOL": "Gradle (:test)",
    "ERROR": "Could not write XML test results file",
    "ROOT CAUSE": "The failing test produced a large/raw diff message that broke the JUnit XML writer.",
    "PROJECT NOTE": "myFixture.checkResult compares exact text; prefer inspecting build/reports/tests/test/index.html for diffs instead of embedding raw file contents in exceptions.",
    "NEW INSTRUCTION": "WHEN needing actual output from a failing test THEN write it to a file and log path, not exception"
}

[2025-12-08 15:50] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "Gradle :test",
    "ERROR": "Could not write XML test results file",
    "ROOT CAUSE": "The failing test produced a failure message/content that was not XML-safe for the JUnit report writer.",
    "PROJECT NOTE": "FileComparisonFailedError may include raw file text; long or unescaped content in failure messages can break Gradle’s JUnit XML writer.",
    "NEW INSTRUCTION": "WHEN Gradle reports 'Could not write XML test results' THEN rerun with --stacktrace and minimize failure message payload"
}

[2025-12-08 15:51] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "Gradle (:test)",
    "ERROR": "Could not write XML test results report",
    "ROOT CAUSE": "A custom failure message with raw file content caused the XML test reporter to choke.",
    "PROJECT NOTE": "In this repo, rely on myFixture.checkResult for diffs; for debugging, write actual content to a side file and keep exceptions minimal to avoid breaking Gradle’s XML report writer.",
    "NEW INSTRUCTION": "WHEN needing to inspect actual result text THEN write it to a file and rethrow original exception"
}

[2025-12-08 15:52] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "Gradle (:test reporter)",
    "ERROR": "Could not write XML test results",
    "ROOT CAUSE": "The failing test produced an exception message/content that the JUnit XML writer could not serialize.",
    "PROJECT NOTE": "myFixture.checkResult failures can include large/raw text diffs; embedding full file content in exception messages can break Gradle’s XML report writer.",
    "NEW INSTRUCTION": "WHEN needing to expose large actual file content THEN write it to a file and log its path"
}

[2025-12-08 17:23] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "get_file_structure",
    "ERROR": "Cannot display file structure; parsing failed",
    "ROOT CAUSE": "The file structure tool couldn't parse the Kotlin file and returned an unsupported/failed status.",
    "PROJECT NOTE": "For Kotlin sources in this repo, directly open files to inspect content when structure parsing fails.",
    "NEW INSTRUCTION": "WHEN get_file_structure reports cannot display or parsing failed THEN open the file using open and scroll"
}

[2025-12-08 17:39] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "IntroduceCustomTypeFromStdlibIntention.generatePreview/CustomTypeApplier.apply",
    "ERROR": "Reused cached plan in preview, writing to original file",
    "ROOT CAUSE": "The cached CustomTypePlan from isAvailable points to the real PyFile and is reused in generatePreview, so insertClass modifies the original file instead of the preview copy.",
    "PROJECT NOTE": "In getPlan(), do not read PLAN_KEY during preview; always rebuild the plan using the preview file/editor so plan.sourceFile and PSI anchors belong to the preview PSI.",
    "NEW INSTRUCTION": "WHEN generating intention preview THEN rebuild plan from preview editor and file, ignore cache"
}

[2025-12-08 17:41] - Updated by Junie - Error analysis
{
    "TYPE": "invalid context",
    "TOOL": "IntroduceCustomTypeFromStdlibIntention.generatePreview/CustomTypeApplier.insertClass",
    "ERROR": "CompletionHandlerException during BackgroundHighlighter cancellation",
    "ROOT CAUSE": "PSI insertion during intention preview triggered document change; cancellation handler threw while the background highlighter was being cancelled.",
    "PROJECT NOTE": "CustomTypeGenerator.insertClass uses PsiFile.addAfter; in preview, all PSI writes must be fully enclosed by IntentionPreviewUtils.write and avoid any async/coroutine or Alarm usage during the write.",
    "NEW INSTRUCTION": "WHEN generating an intention preview performs PSI writes THEN wrap all edits in IntentionPreviewUtils.write and avoid async callbacks"
}

[2025-12-08 17:42] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "CustomTypeGenerator.insertClass",
    "ERROR": "Anchor element from different parent passed to addAfter",
    "ROOT CAUSE": "The preview insertion used an anchor not directly under the target PyFile, causing a parent mismatch during PsiFile.addAfter.",
    "PROJECT NOTE": "When inserting a top-level class, pick an anchor that is a direct child of the PyFile (e.g., first top-level statement) or null to append; do not use elements from inside statements/classes.",
    "NEW INSTRUCTION": "WHEN inserting PSI into a file with addBefore/addAfter THEN choose an anchor that is a direct child of that file"
}

[2025-12-08 17:58] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Attempted to open a directory as a file",
    "ROOT CAUSE": "The provided path points to a directory (docs/dict-access) but open_entire_file requires a file.",
    "PROJECT NOTE": "docs/dict-access is a folder; open specific files within it (e.g., search and then open).",
    "NEW INSTRUCTION": "WHEN target path is a directory THEN search for files inside and open a specific file"
}

[2025-12-08 18:00] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "open_entire_file",
    "ERROR": "Tried to open a directory as a file",
    "ROOT CAUSE": "The path docs/dict-access points to a directory, but open_entire_file expects a file.",
    "PROJECT NOTE": "docs/dict-access is a docs folder; use project search or list functions to inspect its contents.",
    "NEW INSTRUCTION": "WHEN open_entire_file target is a directory THEN list contents via get_file_structure or search_project"
}

[2025-12-08 18:00] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "open_entire_file",
    "ERROR": "Attempted to open a directory as a file",
    "ROOT CAUSE": "The path passed to open_entire_file pointed to a directory (docs/dict-access) rather than a file.",
    "PROJECT NOTE": "The docs/dict-access path is a directory; enumerate files first before opening one.",
    "NEW INSTRUCTION": "WHEN open_entire_file target is a directory THEN list files and open a specific file path"
}

[2025-12-08 18:02] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Attempted to open a directory as a file",
    "ROOT CAUSE": "Passed a directory path to open_entire_file, which only accepts file paths.",
    "PROJECT NOTE": "docs/dict-access is a directory; pick a specific file in it or search for relevant files first.",
    "NEW INSTRUCTION": "WHEN target path is a directory THEN choose a concrete file path inside before opening"
}

[2025-12-08 18:03] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Path is a directory, not a file",
    "ROOT CAUSE": "open_entire_file was called with a directory path (docs/dict-access) instead of a file path.",
    "PROJECT NOTE": "Under docs/, some entries are directories; select a concrete file (e.g., README.md) or list contents first.",
    "NEW INSTRUCTION": "WHEN open_entire_file reports 'Path is a directory' THEN list directory contents and open a specific file"
}

[2025-12-08 18:05] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Path is a directory, not a file",
    "ROOT CAUSE": "The tool was given a directory path (docs/dict-access) instead of a file path.",
    "PROJECT NOTE": "In this repo, docs/ and src/test/testData/ contain directories; open_entire_file requires a specific file (e.g., README.md or *.py), not a folder.",
    "NEW INSTRUCTION": "WHEN open_entire_file path points to a directory THEN select and open a specific file within"
}

[2025-12-08 18:05] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Attempted to open a directory as a file",
    "ROOT CAUSE": "Called open_entire_file with a directory path (docs/dict-access) instead of a file path.",
    "PROJECT NOTE": "To inspect a directory, first list or search for files (e.g., via search_project) under it, then open specific files.",
    "NEW INSTRUCTION": "WHEN path points to a directory THEN list or search files and open a specific file"
}

[2025-12-08 18:06] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "open_entire_file",
    "ERROR": "Path is a directory, not a file",
    "ROOT CAUSE": "A directory path was passed to a tool that requires a file path.",
    "PROJECT NOTE": "docs subfolders may contain multiple files; identify and open a specific file (e.g., README.md) rather than the folder.",
    "NEW INSTRUCTION": "WHEN open_entire_file target is a directory THEN choose and open a specific file within it"
}

[2025-12-08 18:13] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Tried to open a directory as a file",
    "ROOT CAUSE": "A directory path was passed to open_entire_file, which expects a file path.",
    "PROJECT NOTE": "docs/dict-access is a directory; target a concrete file under docs/ or find it via search before opening.",
    "NEW INSTRUCTION": "WHEN target path is a directory THEN select a specific file path before calling open_entire_file"
}

[2025-12-08 18:14] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Path is a directory, not a file",
    "ROOT CAUSE": "Attempted to open a directory with a file-opening tool that expects a file path.",
    "PROJECT NOTE": "docs/dict-access is a directory; open a specific file inside it (e.g., docs/dict-access/README.md) or list contents first.",
    "NEW INSTRUCTION": "WHEN open_entire_file reports directory path THEN open a concrete file inside or list contents first"
}

[2025-12-08 18:15] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "open_entire_file",
    "ERROR": "Attempted to open a directory as a file",
    "ROOT CAUSE": "The provided path points to a directory, but open_entire_file expects a file.",
    "PROJECT NOTE": "Path docs/dict-access is a directory; choose a specific file under it before opening.",
    "NEW INSTRUCTION": "WHEN open_entire_file path resolves to a directory THEN list entries and open a chosen file"
}

[2025-12-08 18:15] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "open_entire_file",
    "ERROR": "Provided path is a directory, not a file",
    "ROOT CAUSE": "The tool was invoked with a directory path, but it only opens files.",
    "PROJECT NOTE": "docs/dict-access is a directory; choose a specific file within it (e.g., README.md) or locate files via search before opening.",
    "NEW INSTRUCTION": "WHEN open_entire_file reports 'Path is a directory' THEN search for files under that path and open a specific file"
}

[2025-12-08 18:16] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "open_entire_file",
    "ERROR": "Path is a directory, not a file",
    "ROOT CAUSE": "Attempted to open a directory path using a file-only tool.",
    "PROJECT NOTE": "docs/dict-access is a folder; specify a concrete file inside (e.g., README.md) to open.",
    "NEW INSTRUCTION": "WHEN open_entire_file target is a directory path THEN select a file within and open that"
}

[2025-12-08 18:18] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "apply_patch",
    "ERROR": "Semantic errors: Unresolved reference 'KeyError'",
    "ROOT CAUSE": "The file validator mis-parsed intention testData Python and flagged false positives.",
    "PROJECT NOTE": "IntelliJ intention testData often contain <caret> markers and partial Python; validator warnings like unresolved references are expected and should not block progress.",
    "NEW INSTRUCTION": "WHEN post-create semantic validator flags testData Python THEN ignore warning and continue with tests"
}

[2025-12-08 20:09] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "search_project",
    "ERROR": "Search path invalid; directory does not exist",
    "ROOT CAUSE": "The provided search path was truncated, pointing outside the repository root.",
    "PROJECT NOTE": "Use the repository root; project sources live under src/main and tests under src/test with testData under src/test/testData.",
    "NEW INSTRUCTION": "WHEN search_project reports directory does not exist THEN set path to project root subdirectory"
}

[2025-12-08 20:37] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "apply_patch",
    "ERROR": "Introduced unresolved references and wrong SDK APIs",
    "ROOT CAUSE": "Used non-existent properties/methods for this PyCharm SDK version (e.g., LanguageLevel.versionString, PythonSdkUtil.getLanguageLevelForSdk, project overload of findPythonSdk).",
    "PROJECT NOTE": "Use com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(project|module) and PythonSdkType.getLanguageLevelForSdk(sdk); use LanguageLevel.toPythonVersion(), not versionString.",
    "NEW INSTRUCTION": "WHEN adding Python SDK/version checks THEN use legacy PythonSdkUtil and PythonSdkType.getLanguageLevelForSdk"
}

[2025-12-08 20:39] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "apply_patch",
    "ERROR": "Deprecated API used; marked for removal",
    "ROOT CAUSE": "PythonSdkType.getLanguageLevelForSdk was used and is deprecated/treated as an error here.",
    "PROJECT NOTE": "Project-level SDK must be resolved via ModuleManager.getInstance(project).modules then legacy PythonSdkUtil.findPythonSdk(module).",
    "NEW INSTRUCTION": "WHEN deriving LanguageLevel from SDK THEN use LanguageLevel.fromPythonVersion(sdk.versionString ?: return false)"
}

[2025-12-08 22:06] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention \"Unwrap int()\" not available",
    "ROOT CAUSE": "UnwrapToExpectedTypeIntention only offers when an expected type exists; redundant cast lacks context type.",
    "PROJECT NOTE": "Extend UnwrapToExpectedTypeIntention.kt to also offer unwrap for redundant builtin casts (int/str/float/bool) when the single argument’s inferred type already matches the wrapper type, even if ExpectedTypeInfo.expectedCtorName is null.",
    "NEW INSTRUCTION": "WHEN intention lookup misses 'Unwrap' for builtin cast THEN unwrap when wrapper equals argument type"
}

[2025-12-08 22:07] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention \"Unwrap int()\" not available at caret",
    "ROOT CAUSE": "UnwrapToExpectedTypeIntention requires an expected type; redundant cast in return without annotation yields null expected type so intention is not offered.",
    "PROJECT NOTE": "Update UnwrapToExpectedTypeIntention.kt to handle null ExpectedTypeInfo by recognizing builtin casts (int/str/float/bool) and comparing the inner expression type; ensure the intention text remains \"Unwrap int()\" for stdlib casts.",
    "NEW INSTRUCTION": "WHEN expectedCtorName(expr, ctx) is null and callee is builtin cast THEN compare inner type to cast type and offer unwrap"
}

[2025-12-08 22:11] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention not available at caret during test",
    "ROOT CAUSE": "The test likely used an intention name or caret position that does not match where UnwrapToExpectedTypeIntention is offered.",
    "PROJECT NOTE": "In this repo, doIntentionTest expects the caret on the wrapper call or its single argument, and the intention text must match the action’s presentation used in UnwrapBasicTest.",
    "NEW INSTRUCTION": "WHEN intention test cannot find action THEN place caret on wrapper call/arg and use exact intention text"
}

[2025-12-08 22:46] - Updated by Junie - Error analysis
{
    "TYPE": "invalid context",
    "TOOL": "-",
    "ERROR": "PSI write during preview triggered cancellation handler failure",
    "ROOT CAUSE": "CustomTypeGenerator.insertClass performed a real PSI modification during intention preview, causing documentChanged and BackgroundHighlighter cancellation to throw.",
    "PROJECT NOTE": "Ensure all PSI edits in IntroduceCustomTypeFromStdlibIntention preview path run inside IntentionPreviewUtils.write and operate on the preview PSI, not the live document.",
    "NEW INSTRUCTION": "WHEN generating intention preview THEN perform all PSI edits inside IntentionPreviewUtils.write"
}

[2025-12-08 22:47] - Updated by Junie - Error analysis
{
    "TYPE": "invalid context",
    "TOOL": "-",
    "ERROR": "PSI edits in preview triggered BackgroundHighlighter cancellation crash",
    "ROOT CAUSE": "Side-effectful operations ran during intention preview, causing document change cancellations to propagate.",
    "PROJECT NOTE": "In CustomTypeApplier.apply, guard non-essential actions (rename, project-wide rewrites/imports) with isPreview or IntentionPreviewUtils.isIntentionPreviewActive() so only preview PSI edits occur inside IntentionPreviewUtils.write.",
    "NEW INSTRUCTION": "WHEN IntentionPreviewUtils.isIntentionPreviewActive() THEN modify only preview PSI and skip side effects"
}

[2025-12-08 22:52] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "Missing j.u.l LogManager test-log.properties during IDE tests",
    "ROOT CAUSE": "The IntelliJ test harness expects a logging config file that is absent in the Gradle cache, causing the test run to error out.",
    "PROJECT NOTE": "This is an IntelliJ Platform plugin project; run tests from the project root via Gradle so the IntelliJ test framework and logging are initialized correctly.",
    "NEW INSTRUCTION": "WHEN run_test logs 'test-log.properties does not exist' THEN run './gradlew cleanTest test --info' from project root"
}

[2025-12-08 22:56] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "Build failed; missing IDE test LogManager config",
    "ROOT CAUSE": "IDE plugin tests were launched without the full Gradle test harness, so required test-log.properties and runtime setup were missing.",
    "PROJECT NOTE": "Run tests via the project root Gradle test tasks (or specify a test class name) so the IntelliJ test environment and logging properties are configured.",
    "NEW INSTRUCTION": "WHEN running tests for this project THEN execute Gradle test from project root without a path"
}

[2025-12-08 23:00] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "run_test",
    "ERROR": "Fix 'Add to __all__' not found",
    "ROOT CAUSE": "The inspection excludes regular top-level attributes, only recognizing typing.NewType aliases.",
    "PROJECT NOTE": "Update PyMissingInDunderAllInspection.kt (isExportable) to treat top-level PyTargetExpression assignments as exportable, not just NewType.",
    "NEW INSTRUCTION": "WHEN run_test reports \"Fix 'Add to __all__' not found\" THEN allow top-level PyTargetExpression in isExportable"
}

[2025-12-08 23:01] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Fix 'Add to __all__' not found",
    "ROOT CAUSE": "The inspection only treats NewType target assignments as exportable, so top-level attributes like my_attr are ignored and no quick fix is offered.",
    "PROJECT NOTE": "Update PyMissingInDunderAllInspection.isExportable in src/main/kotlin/.../inspections/PyMissingInDunderAllInspection.kt to allow PyTargetExpression except __all__.",
    "NEW INSTRUCTION": "WHEN run_test fails with 'Fix 'Add to __all__' not found' THEN broaden isExportable to export PyTargetExpression except __all__, then rerun tests"
}

[2025-12-08 23:01] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Fix 'Add to __all__' not found",
    "ROOT CAUSE": "The inspection excludes top-level attributes because isExportable only allows NewType targets.",
    "PROJECT NOTE": "Update src/main/kotlin/.../inspections/PyMissingInDunderAllInspection.kt: in isExportable, return true for any PyTargetExpression except '__all__' to enable exporting variables like my_attr.",
    "NEW INSTRUCTION": "WHEN element is PyTargetExpression for __all__ export check THEN treat as exportable unless name is '__all__'"
}

[2025-12-08 23:02] - Updated by Junie - Error analysis
{
    "TYPE": "logic bug",
    "TOOL": "run_test",
    "ERROR": "Quick fix 'Add to __all__' not found",
    "ROOT CAUSE": "isExportable only allowed NewType target expressions and excluded plain top-level attributes.",
    "PROJECT NOTE": "Update PyMissingInDunderAllInspection.kt:isExportable to treat any top-level PyTargetExpression except __all__ as exportable.",
    "NEW INSTRUCTION": "WHEN element is PyTargetExpression and name is not __all__ THEN return exportable true in isExportable"
}

[2025-12-08 23:10] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "create",
    "ERROR": "Unresolved reference 'git4idea' in test file",
    "ROOT CAUSE": "Git plugin classes were not on the test compile classpath; plugin dependency configured incorrectly.",
    "PROJECT NOTE": "To use Git classes in tests, declare git4idea under platformPlugins in gradle.properties (not platformBundledPlugins), or avoid direct git4idea imports and mock VCS behavior.",
    "NEW INSTRUCTION": "WHEN importing git4idea classes in tests THEN add 'git4idea' to platformPlugins before creating files"
}

[2025-12-08 23:10] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "create",
    "ERROR": "Unresolved reference to 'git4idea' in test file",
    "ROOT CAUSE": "Git plugin classes were not on the test classpath due to incorrect/insufficient plugin dependency setup.",
    "PROJECT NOTE": "To use git4idea types, declare 'git4idea' in gradle.properties (platformBundledPlugins or platformPlugins) and prefer VCS-agnostic tests when possible in this repo's fixtures.TestBase.",
    "NEW INSTRUCTION": "WHEN test imports git4idea.* THEN replace with VCS-agnostic mocks and remove git4idea usage"
}

[2025-12-08 23:11] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "create",
    "ERROR": "Unresolved reference 'git4idea' in test file",
    "ROOT CAUSE": "The test imported git4idea classes without adding the Git plugin to the test classpath, causing unresolved symbols.",
    "PROJECT NOTE": "Tests here compile against the configured IntelliJ platform; avoid direct git4idea dependencies or declare Git4Idea under platformPlugins and ensure Gradle sync before use.",
    "NEW INSTRUCTION": "WHEN writing tests that reference external plugin APIs THEN avoid them and use VCS-agnostic mocks"
}

[2025-12-08 23:11] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "create",
    "ERROR": "Semantic errors from incorrect platform API usage",
    "ROOT CAUSE": "New Kotlin code called platform APIs with wrong signatures and return-type assumptions (findMethodByName params; NavigationItem.navigate Unit).",
    "PROJECT NOTE": "In IntelliJ Platform: NavigationItem.navigate returns Unit; processSelectedItem should call navigate and return true. For PyClass, prefer scanning methods by name when findMethodByName overloads mismatch.",
    "NEW INSTRUCTION": "WHEN adding Kotlin code using IntelliJ/PyCharm APIs THEN verify method signatures and return types"
}

[2025-12-08 23:12] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "create",
    "ERROR": "Unresolved reference 'git4idea' in test file",
    "ROOT CAUSE": "Git plugin classes were not on the test compile classpath, so git4idea symbols could not resolve.",
    "PROJECT NOTE": "Do not rely on git4idea in tests here; use VCS-agnostic service/mocks, or declare Git4Idea as a bundled plugin dependency in the Gradle intellijPlatform pluginDependencies block if truly required.",
    "NEW INSTRUCTION": "WHEN external IDE plugin classes are unresolved in tests THEN replace with VCS-agnostic mocks or project APIs"
}

[2025-12-08 23:12] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "create",
    "ERROR": "Unresolved reference 'git4idea' in new test file",
    "ROOT CAUSE": "The test imports Git plugin APIs without the plugin on the test classpath.",
    "PROJECT NOTE": "If Git APIs are needed, add 'Git4Idea' to platformBundledPlugins in gradle.properties and run tests via Gradle; otherwise keep tests VCS-agnostic.",
    "NEW INSTRUCTION": "WHEN importing git4idea causes unresolved references THEN write a VCS-agnostic high-level test without plugin imports"
}

[2025-12-08 23:15] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Compared Unit? to Boolean in processSelectedItem",
    "ROOT CAUSE": "NavigationItem.navigate returns Unit, but code compared it to Boolean causing a type mismatch.",
    "PROJECT NOTE": "In IntelliJ Platform, NavigationItem.navigate(boolean requestFocus) returns Unit; do not compare its result.",
    "NEW INSTRUCTION": "WHEN processSelectedItem uses NavigationItem.navigate return value THEN call navigate(true) and return true without comparison"
}