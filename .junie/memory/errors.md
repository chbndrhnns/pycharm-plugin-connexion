[2025-12-22 14:41] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "-",
    "ERROR": "Missing intention description.html resource",
    "ROOT CAUSE": "JumpToPytestNodeInTestTreeIntention was registered without the required description directory and files.",
    "PROJECT NOTE": "Place description files under src/main/resources/intentionDescriptions/JumpToPytestNodeInTestTreeIntention/ (description.html, before.py.template, after.py.template as needed) and ensure plugin.xml <intentionAction> is declared.",
    "NEW INSTRUCTION": "WHEN adding a new IntentionAction THEN create intentionDescriptions/<ClassName>/description.html (with example templates)"
}

[2025-12-22 22:36] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Caret element not found; imported symbol unresolved",
    "ROOT CAUSE": "PyResolveUtils.findMember only checks top-level declarations in a PyFile and ignores names brought in via import statements, so 'target_module.MyImportedClass' cannot resolve.",
    "PROJECT NOTE": "Update PyResolveUtils.findMember to also resolve symbols imported into a module (handle both 'from source import Name' and 'import source as alias', including aliasing), not just classes/functions/attributes defined in the file.",
    "NEW INSTRUCTION": "WHEN top-level member lookup returns null in module THEN search imported names and resolve their targets"
}

[2025-12-22 22:37] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "run_test",
    "ERROR": "getElementAtCaret failed: element not found",
    "ROOT CAUSE": "The PyMockPatch reference contributor is feature-flagged and not enabled in the test, so no reference exists at the caret.",
    "PROJECT NOTE": "Enable PluginSettingsState.state.enablePyMockPatchReference = true in test setUp when asserting patch-string resolution.",
    "NEW INSTRUCTION": "WHEN testing PyMockPatch string references THEN enable PluginSettingsState.enablePyMockPatchReference in setUp"
}

[2025-12-22 22:37] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "getElementAtCaret could not find element at caret",
    "ROOT CAUSE": "Imported symbols are not considered in resolution, so patch string segment didn't resolve.",
    "PROJECT NOTE": "Update src/main/kotlin/.../psi/PyResolveUtils.kt findMember to resolve names brought in via from-import and import-as in the target module.",
    "NEW INSTRUCTION": "WHEN resolving module member in PyResolveUtils.findMember THEN include names imported via from/import-as"
}

[2025-12-22 22:38] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Element at caret not found",
    "ROOT CAUSE": "Reference resolution ignores imported symbols; PyResolveUtils.findMember only checks top-level declarations.",
    "PROJECT NOTE": "In src/main/kotlin/.../psi/PyResolveUtils.kt, extend findMember to resolve names brought in via 'import' and 'from ... import ...' (and consider __all__).",
    "NEW INSTRUCTION": "WHEN resolving module members in PyResolveUtils.findMember THEN include names imported via import statements"
}

[2025-12-22 22:38] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Element at caret not found; no resolved target",
    "ROOT CAUSE": "Member resolution checks only top-level declarations in a PyFile and ignores imported symbols.",
    "PROJECT NOTE": "In PyResolveUtils.findMember, also inspect PyImportStatement and PyFromImportStatement to resolve names brought into the module when matching a dotted segment.",
    "NEW INSTRUCTION": "WHEN module member lookup yields no symbol THEN resolve names introduced by import and from-import statements"
}

[2025-12-22 22:45] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "run_test",
    "ERROR": "No tests found in provided directory path",
    "ROOT CAUSE": "run_test was pointed to src/main sources instead of a test-containing path.",
    "PROJECT NOTE": "Tests live under src/test/kotlin/com/github/chbndrhnns/intellijplatformplugincopy; run from project root or target specific test classes with Gradle.",
    "NEW INSTRUCTION": "WHEN run_test path points to src/main THEN run from project root or use src/test path"
}

[2025-12-22 22:47] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Assertion failed: private package offered publicizing quick fix",
    "ROOT CAUSE": "PyPrivateModuleImportInspection still proposes making symbols public for underscored (private) packages and does not ascend to the next public package.",
    "PROJECT NOTE": "Adjust logic in src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/exports/PyPrivateModuleImportInspection.kt to treat packages with leading underscore as private and avoid 'Make public' there.",
    "NEW INSTRUCTION": "WHEN target package name starts with '_' THEN do not offer 'make symbol public'"
}

[2025-12-22 22:49] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Inspection suggested fix for private package",
    "ROOT CAUSE": "PyPrivateModuleImportInspection considers private packages as candidates and only checks the immediate parent, causing a quick fix to be offered for _priv.",
    "PROJECT NOTE": "Update src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/exports/PyPrivateModuleImportInspection.kt to: (1) skip registering problems and quick fixes when the containing package is private (segment starts with '_'); (2) when searching __all__, walk up to the nearest public package and use its __init__.py.",
    "NEW INSTRUCTION": "WHEN inspected symbol is inside a private package THEN do not register problem or quick fixes"
}

[2025-12-22 22:49] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Reproduction tests failed due to incorrect inspection behavior",
    "ROOT CAUSE": "The inspection proposes making symbols public in private packages and doesn’t check the next public package for existing exports.",
    "PROJECT NOTE": "Adjust src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/exports/PyPrivateModuleImportInspection.kt to skip fixes for private packages and to walk up to the nearest public package when checking __all__.",
    "NEW INSTRUCTION": "WHEN inspected package is private THEN skip suggesting make-public and do not flag"
}

[2025-12-22 22:50] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Repro tests failed: inspection suggests fix in private package",
    "ROOT CAUSE": "The inspection only checks the immediate parent package and still offers 'Make symbol public' for private packages instead of skipping or looking up to a public package.",
    "PROJECT NOTE": "Update src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/exports/PyPrivateModuleImportInspection.kt to ignore private packages and, if needed, search ancestor public packages for __all__ exports (see PyUseExportedSymbolFromPackageQuickFix).",
    "NEW INSTRUCTION": "WHEN parent package name starts with '_' THEN suppress 'Make symbol public' quick fix"
}

[2025-12-22 22:58] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Test references missing action id and setting symbol",
    "ROOT CAUSE": "The test was added before defining the settings flag, action class, and plugin.xml action id.",
    "PROJECT NOTE": "Register actions under Copy.Paste.Special in plugin.xml and use consistent action ids (e.g., MyPlugin.*). Add new toggles to PluginSettingsState.state before tests use them.",
    "NEW INSTRUCTION": "WHEN test references unknown action id or setting THEN define setting and register action first"
}

[2025-12-22 22:59] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Test references missing action and setting",
    "ROOT CAUSE": "The test file used a non-existent action id/class and a settings flag not yet defined.",
    "PROJECT NOTE": "Define new settings in PluginSettingsState, implement the action class, and register the action id in plugin.xml under Copy.Paste.Special before adding tests that reference them.",
    "NEW INSTRUCTION": "WHEN adding a test for a new action THEN define setting, implement action, and register id first"
}

[2025-12-22 22:59] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Test referenced undefined setting, action id, and class",
    "ROOT CAUSE": "The test file was created before the new action, setting flag, and plugin.xml registration existed.",
    "PROJECT NOTE": "Action IDs in this repo are registered in plugin.xml and referenced by performEditorAction; ensure the ID matches exactly.",
    "NEW INSTRUCTION": "WHEN creating tests referencing a new action id THEN implement action and register id in plugin.xml first"
}

[2025-12-22 23:22] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "Gradle :compileKotlin",
    "ERROR": "Unresolved reference 'isTypedDict' during compile",
    "ROOT CAUSE": "The code calls isTypedDict, which is not defined or imported in this SDK.",
    "PROJECT NOTE": "Update src/main/kotlin/.../intention/parameterobject/PyInlineParameterObjectProcessor.kt (~line 137) to use an existing Python typing API (e.g., PyTypedDictType check or a util like PyTypingTypeChecker/PyTypedDictUtil) or implement a local helper; ensure proper imports.",
    "NEW INSTRUCTION": "WHEN compileKotlin reports unresolved reference 'isTypedDict' THEN replace with available SDK typing check or implement helper"
}

[2025-12-22 23:45] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "Gradle :compileKotlin",
    "ERROR": "Unresolved reference 'isTypedDict' during compile",
    "ROOT CAUSE": "A file calls isTypedDict that exists only as a private helper inside PyInlineParameterObjectProcessor, making it invisible to other files.",
    "PROJECT NOTE": "isTypedDict is defined as private in PyInlineParameterObjectProcessor.kt (~line 350); callers like TypedDictGenerator.kt cannot access it.",
    "NEW INSTRUCTION": "WHEN shared helper is referenced across files THEN move it to public top-level util and import"
}

[2025-12-22 23:46] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "Gradle :compileKotlin",
    "ERROR": "Unresolved reference 'isTypedDict' causes compilation failure",
    "ROOT CAUSE": "Code references isTypedDict from another file where it is private and not imported or shared.",
    "PROJECT NOTE": "Extract the private isTypedDict from PyInlineParameterObjectProcessor into a shared top-level util (e.g., intention/parameterobject/TypedDictUtil.kt) and import it in TypedDictGenerator.kt.",
    "NEW INSTRUCTION": "WHEN compile error shows unresolved 'isTypedDict' THEN move helper to shared util and import"
}

[2025-12-22 23:47] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "Gradle :compileKotlin (test run)",
    "ERROR": "Unresolved reference: isTypedDict",
    "ROOT CAUSE": "A source file references isTypedDict which exists only as a private helper in another class, so it is not visible/importable.",
    "PROJECT NOTE": "Move isTypedDict(pyClass: PyClass) into a shared top-level util (e.g., intention/parameterobject/TypingUtils.kt) and update both PyInlineParameterObjectProcessor and TypedDictGenerator to use it.",
    "NEW INSTRUCTION": "WHEN Kotlin reports unresolved helper reference THEN define shared top-level util or import correct symbol"
}

[2025-12-22 23:48] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "Gradle :compileKotlin",
    "ERROR": "Unresolved reference 'isTypedDict' during Kotlin compilation",
    "ROOT CAUSE": "isTypedDict is used from another file but is declared private inside PyInlineParameterObjectProcessor, making it inaccessible.",
    "PROJECT NOTE": "TypedDictGenerator.kt references isTypedDict; expose a shared public helper (e.g., top‑level util or PyClass extension) and import it here.",
    "NEW INSTRUCTION": "WHEN referencing internal helper across files THEN move it to shared util and import"
}

[2025-12-22 23:58] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Result text mismatch after intention applied",
    "ROOT CAUSE": "The test expected PEP 604 union syntax (str | None) but the fixture language level likely renders Optional[str], causing a diff.",
    "PROJECT NOTE": "Tests here typically run with Python 3.8 language level; use typing.Optional[...] instead of PEP 604 unions in expected/initial test content.",
    "NEW INSTRUCTION": "WHEN writing Python union types in tests THEN use Optional[...] to match language level"
}

[2025-12-22 23:59] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected and actual file contents did not match",
    "ROOT CAUSE": "The test expected a positional call do(\"x\"), but the intention preserves keyword style as do(arg=\"x\").",
    "PROJECT NOTE": "Parameter object inlining rewrites calls with keyword arguments; align test expectations to keyword-preserving rewrites.",
    "NEW INSTRUCTION": "WHEN crafting expected result for inline call rewrite THEN preserve keyword arguments in calls"
}

[2025-12-23 00:00] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Result text mismatch after intention execution",
    "ROOT CAUSE": "The new test's expected output didn't match the intention's actual transformation (likely call-site argument style).",
    "PROJECT NOTE": "Inline Parameter Object keeps keyword arguments at call sites; expected output should use named arguments (e.g., do(arg=\"x\")) and match plugin formatting.",
    "NEW INSTRUCTION": "WHEN doIntentionTest fails with FileComparisonFailedError THEN align expected text to actual call-site argument style"
}

[2025-12-23 00:01] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "FileComparisonFailedError: expected/actual code mismatch",
    "ROOT CAUSE": "The new test’s expected code does not match the intention’s actual output formatting/argument style.",
    "PROJECT NOTE": "Intention tests with myFixture.doIntentionTest compare full-file text; keep exact whitespace and preserve call-site argument style (e.g., keyword vs positional) per existing tests.",
    "NEW INSTRUCTION": "WHEN adding expected result for doIntentionTest THEN mirror actual intention output formatting and call-site style"
}

[2025-12-23 10:12] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected 'from .sub.mod', produced 'from .mod'",
    "ROOT CAUSE": "Import builder assumes source is a direct child of target; nested modules need full relative path.",
    "PROJECT NOTE": "Adjust PyAllExportUtil.addOrUpdateImportForModuleSymbol to derive a dotted path from targetFile's directory to sourceModule (e.g., sub.mod) instead of only using the source module filename.",
    "NEW INSTRUCTION": "WHEN target is ancestor of source module THEN compute dotted relative path from target to source"
}

[2025-12-23 10:12] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected 'from .sub.mod' but generated 'from .mod'",
    "ROOT CAUSE": "Import path generation assumes direct child module and ignores nested relative path segments.",
    "PROJECT NOTE": "Fix PyAllExportUtil.addOrUpdateImportForModuleSymbol to compute the dotted relative path from target package dir to source module (e.g., via VfsUtilCore.getRelativePath and replacing '/' with '.') before creating the from-import.",
    "NEW INSTRUCTION": "WHEN source module not direct child of target package THEN compute dotted relative module path and use it"
}

[2025-12-23 10:13] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected 'from .sub.mod' but generated 'from .mod' import",
    "ROOT CAUSE": "Import module path is derived as direct child of target, ignoring nested subpackages.",
    "PROJECT NOTE": "Adjust PyAllExportUtil.addOrUpdateImportForModuleSymbol to compute dotted module path from targetFile directory to sourceModule using relative VFS path segments.",
    "NEW INSTRUCTION": "WHEN sourceModule is not a direct sibling of targetFile THEN derive dotted relative module path"
}

[2025-12-23 10:14] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected from .sub.mod import, but got from .mod",
    "ROOT CAUSE": "Import module name was derived as direct child instead of full relative path from target to source.",
    "PROJECT NOTE": "Fix addOrUpdateImportForModuleSymbol in PyAllExportUtil.kt to compute the module name by deriving a dotted path from targetFile's directory to sourceModule (e.g., via VfsUtilCore.getRelativePath and replacing '/' with '.').",
    "NEW INSTRUCTION": "WHEN source module is not an immediate child of target directory THEN compute dotted relative path and use in from-import"
}

[2025-12-23 10:15] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected import from sub.mod, got from .mod",
    "ROOT CAUSE": "Import generation assumes direct parent and uses only module filename, ignoring relative subpackage path.",
    "PROJECT NOTE": "Fix in PyAllExportUtil.addOrUpdateImportForModuleSymbol: derive moduleName by computing the relative path from target __init__.py directory to source PyFile and dot-joining directories (e.g., using VfsUtilCore.getRelativePath).",
    "NEW INSTRUCTION": "WHEN relative path includes subdirectories THEN build dotted module path and use it"
}

[2025-12-23 10:23] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected trailing comma not present after adding skip",
    "ROOT CAUSE": "PytestSkipToggler.toggleOnParam adds marks/skip but doesn’t insert a trailing comma in the pytest.param argument list (and marks list), so result text mismatches expectations.",
    "PROJECT NOTE": "Update PytestSkipToggler.toggleOnParam (lines ~159–193) to ensure that when constructing/updating pytest.param(...), the last argument ends with a comma and marks lists include a trailing comma after appended skip.",
    "NEW INSTRUCTION": "WHEN adding skip mark in pytest.param THEN append trailing comma to argument list"
}

[2025-12-23 10:24] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected/actual file content mismatch",
    "ROOT CAUSE": "PytestSkipToggler.toggleOnParam does not add a trailing comma after adding marks.",
    "PROJECT NOTE": "Adjust src/main/kotlin/.../PytestSkipToggler.kt in toggleOnParam (lines ~159–193) to insert a trailing comma after the marks argument or within the marks list when modifying pytest.param.",
    "NEW INSTRUCTION": "WHEN adding marks to pytest.param arguments THEN append a trailing comma to the changed part"
}

[2025-12-23 10:26] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Fixture result mismatch: expected trailing comma not present",
    "ROOT CAUSE": "toggleOnParam in PytestSkipToggler.kt adds skip without appending a trailing comma in pytest.param arguments/marks.",
    "PROJECT NOTE": "Adjust src/main/.../PytestSkipToggler.toggleOnParam to append a trailing comma after inserting marks=[...pytest.mark.skip] or after extending existing marks.",
    "NEW INSTRUCTION": "WHEN modifying pytest.param arguments or marks list THEN append a trailing comma"
}

[2025-12-23 10:26] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected text mismatch in file comparison",
    "ROOT CAUSE": "PytestSkipToggler.toggleOnParam does not add a trailing comma after inserting skip mark.",
    "PROJECT NOTE": "Update PytestSkipToggler.toggleOnParam to append a trailing comma in pytest.param arguments and inside marks lists when adding pytest.mark.skip.",
    "NEW INSTRUCTION": "WHEN adding pytest.mark.skip to pytest.param THEN append a trailing comma to arguments"
}

[2025-12-23 10:26] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "FileComparisonFailedError: expected and actual text differ",
    "ROOT CAUSE": "toggleOnParam does not add a trailing comma when inserting skip mark(s).",
    "PROJECT NOTE": "Update PytestSkipToggler.toggleOnParam (lines ~159–193 in PytestSkipToggler.kt) to append commas after newly added marks and after the marks argument.",
    "NEW INSTRUCTION": "WHEN adding skip to pytest.param arguments THEN append a trailing comma to inserted elements"
}

[2025-12-23 10:27] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "ASTNode supplied to addAfter; PsiElement required",
    "ROOT CAUSE": "ensureTrailingComma used generator.createComma() (ASTNode) where addAfter expects PsiElement.",
    "PROJECT NOTE": "In this codebase, generator.createComma() returns an ASTNode; use .psi when inserting.",
    "NEW INSTRUCTION": "WHEN inserting a generated comma with addAfter THEN use generator.createComma().psi as element"
}

[2025-12-23 10:28] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch: ASTNode used where PsiElement required",
    "ROOT CAUSE": "Code passed generator.createComma() (ASTNode) to addAfter, which expects a PsiElement.",
    "PROJECT NOTE": "In this codebase, generator.createComma() returns an ASTNode; use .psi or create a comma PsiElement via createExpressionFromText if needed.",
    "NEW INSTRUCTION": "WHEN inserting a comma via generator.createComma THEN call addAfter(generator.createComma().psi, target)"
}

[2025-12-23 10:30] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "FileComparisonFailedError due to expected vs actual text mismatch",
    "ROOT CAUSE": "Tests were updated to expect a trailing comma but the intention implementation still emits no trailing comma.",
    "PROJECT NOTE": "Implement trailing comma emission in PytestSkipToggler.toggleOnParam (src/main/.../PytestSkipToggler.kt) when adding pytest.mark.skip to marks.",
    "NEW INSTRUCTION": "WHEN Parametrize skip mark format must change THEN modify PytestSkipToggler.toggleOnParam to emit trailing comma"
}

[2025-12-23 10:30] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Unbalanced quotes in search/replace strings",
    "ROOT CAUSE": "The edit added stray double quotes to Kotlin string literals in both search and replace, producing malformed patterns.",
    "PROJECT NOTE": "Prefer constructing new PSI via PyElementGenerator in PytestSkipToggler.toggleOnParam instead of raw text to avoid quoting/comma issues.",
    "NEW INSTRUCTION": "WHEN search_replace includes Kotlin string literals with quotes THEN verify escaping and balanced quotes first"
}

[2025-12-23 10:32] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Expected text mismatch in fixture result",
    "ROOT CAUSE": "Tests were updated to expect a trailing comma in marks, but PytestSkipToggler.toggleOnParam still generates marks without the trailing comma.",
    "PROJECT NOTE": "Adjust PytestSkipToggler.kt toggleOnParam to build marks lists and new marks args as '...[...,],'.",
    "NEW INSTRUCTION": "WHEN rebuilding or creating marks list text THEN append a trailing comma inside brackets"
}

[2025-12-23 10:33] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "FileComparisonFailedError: expected vs actual text mismatch",
    "ROOT CAUSE": "Tests were changed to expect a trailing comma while implementation still outputs without it.",
    "PROJECT NOTE": "Implement trailing comma in PytestSkipToggler.toggleOnParam by generating marks=[pytest.mark.skip,] via PSI; do not modify TogglePytestSkipIntentionParametrizeTest.",
    "NEW INSTRUCTION": "WHEN Parametrize skip requires trailing comma THEN generate marks=[pytest.mark.skip,] in toggleOnParam"
}

[2025-12-23 10:39] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "plugin.xml",
    "ERROR": "Cannot resolve group 'RefactoringPopupGroup'",
    "ROOT CAUSE": "An action was registered under a non-existent action group id in plugin.xml.",
    "PROJECT NOTE": "Use only known group ids; in this project 'RefactoringMenu' is valid, 'RefactoringPopupGroup' is not.",
    "NEW INSTRUCTION": "WHEN plugin.xml reports unresolved action group THEN remove or replace with valid 'RefactoringMenu' group id"
}

[2025-12-23 10:39] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Unknown action group 'RefactoringPopupGroup'",
    "ROOT CAUSE": "plugin.xml was updated to add actions to a non-existent group id 'RefactoringPopupGroup'.",
    "PROJECT NOTE": "In this project, only RefactoringMenu is a valid built-in group; 'RefactoringPopupGroup' is not defined. Actions in Refactor This appear when added to RefactoringMenu.",
    "NEW INSTRUCTION": "WHEN adding add-to-group in plugin.xml THEN use valid group ids like RefactoringMenu only"
}

[2025-12-23 10:43] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Cannot resolve extension point 'com.intellij.intentionAction'",
    "ROOT CAUSE": "IntentionsConfigurable.getDependencies uses a raw EP string not available in this SDK; use the SDK EP constant instead.",
    "PROJECT NOTE": "In IntentionsConfigurable.getDependencies, return listOf(com.intellij.codeInsight.intention.IntentionActionBean.EP_NAME) or remove the override if unneeded.",
    "NEW INSTRUCTION": "WHEN getDependencies uses raw 'com.intellij.intentionAction' EP string THEN return listOf(IntentionActionBean.EP_NAME)"
}

[2025-12-23 10:50] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Invalid MapDataContext import; class not found",
    "ROOT CAUSE": "The test helper imports com.intellij.testFramework.MapDataContext which doesn't exist in this SDK.",
    "PROJECT NOTE": "In tests, build AnActionEvent using com.intellij.openapi.actionSystem.impl.SimpleDataContext or DataManager.getDataContext(editor.component) instead of MapDataContext.",
    "NEW INSTRUCTION": "WHEN creating AnActionEvent in tests THEN use SimpleDataContext instead of MapDataContext"
}

[2025-12-23 10:53] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Unresolved import MapDataContext in test helper",
    "ROOT CAUSE": "The test helper uses MapDataContext from testFramework, which isn’t available in this SDK; SimpleDataContext should be used to build a DataContext.",
    "PROJECT NOTE": "For action tests, create AnActionEvent via SimpleDataContext (or TestActionEvent) with PROJECT, EDITOR, PSI_FILE, and VIRTUAL_FILE.",
    "NEW INSTRUCTION": "WHEN constructing DataContext for AnActionEvent in tests THEN use SimpleDataContext instead of MapDataContext"
}

[2025-12-23 10:54] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Unresolved test framework classes in new test helper",
    "ROOT CAUSE": "The helper uses MapDataContext and event creation APIs not available in this SDK; use SimpleDataContext/TestActionEvent instead.",
    "PROJECT NOTE": "In action tests, prefer com.intellij.openapi.actionSystem.impl.SimpleDataContext and com.intellij.testFramework.TestActionEvent over MapDataContext; call action.update(event) before actionPerformed.",
    "NEW INSTRUCTION": "WHEN creating AnAction test event THEN use TestActionEvent with SimpleDataContext instead of MapDataContext"
}

[2025-12-23 11:29] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention offered where it must be unavailable",
    "ROOT CAUSE": "IntroduceParameterObjectTarget.find returns the function for caret inside function body, enabling availability.",
    "PROJECT NOTE": "Limit availability in src/main/kotlin/.../IntroduceParameterObjectTarget.kt to function name, parameter list (including annotations), return annotation, or call sites; exclude general function body.",
    "NEW INSTRUCTION": "WHEN caret is inside function body (not name/params/return) THEN return null in find"
}

[2025-12-23 11:31] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Action unexpectedly available in function body",
    "ROOT CAUSE": "The availability finder returns a target for carets inside a function body, so isAvailable() stays true where it should be false.",
    "PROJECT NOTE": "Tighten IntroduceParameterObjectTarget.find/isAvailable (src/main/.../IntroduceParameterObjectTarget.kt) to only allow function header (name/parameters/return annotation) or explicit call sites, not arbitrary body elements.",
    "NEW INSTRUCTION": "WHEN caret is inside function body block THEN return false from isAvailable"
}

[2025-12-23 11:42] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention offered for __name__ in __main__ guard",
    "ROOT CAUSE": "Only a test was added; CreateLocalVariableIntention.isAvailable was not updated to exclude __name__ in if __name__ == '__main__':.",
    "PROJECT NOTE": "Implement the filter in src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/intention/localvariable/CreateLocalVariableIntention.kt: in isAvailable, detect a PyIfStatement with condition __name__ == '__main__' and return false when the caret is on __name__.",
    "NEW INSTRUCTION": "WHEN caret identifier is '__name__' inside if __name__ == '__main__' THEN return intention unavailable"
}

[2025-12-23 11:43] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Intention offered for __name__ in __main__ guard",
    "ROOT CAUSE": "CreateLocalVariableIntention.isAvailable still returns true on __name__ in if __name__ == '__main__' context.",
    "PROJECT NOTE": "Guard isAvailable in CreateLocalVariableIntention to return false for __name__ (and optionally other dunder names) especially inside equality comparisons to '__main__'.",
    "NEW INSTRUCTION": "WHEN run_test fails with message 'Should not offer local variable for __name__' THEN add explicit __name__ check in isAvailable and context check for __main__ guard"
}

[2025-12-23 11:44] - Updated by Junie - Error analysis
{
    "TYPE": "tool limitation",
    "TOOL": "bash",
    "ERROR": "Multi-line bash command not allowed here",
    "ROOT CAUSE": "The heredoc used multiple newline-separated commands, which this bash tool forbids.",
    "PROJECT NOTE": "Create or modify test files under src/test/kotlin using apply_patch instead of bash heredocs.",
    "NEW INSTRUCTION": "WHEN bash command includes unescaped newlines THEN use apply_patch to create or edit files"
}

[2025-12-23 11:45] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "Multi-line bash command not allowed; newline characters prohibited",
    "ROOT CAUSE": "The bash tool wrapper forbids newline-separated commands; a heredoc was used.",
    "PROJECT NOTE": "To add test files under src/test/kotlin/..., use apply_patch instead of bash heredocs.",
    "NEW INSTRUCTION": "WHEN bash command contains newline characters THEN use apply_patch to create or edit files"
}

[2025-12-23 12:40] - Updated by Junie - Error analysis
{
    "TYPE": "tool limitation",
    "TOOL": "get_file_structure",
    "ERROR": "File structure unavailable for this file type; parsing failed",
    "ROOT CAUSE": "Requested structure for plugin.xml, which the tool cannot parse into a displayable outline.",
    "PROJECT NOTE": "Inspect plugin.xml content directly to modify or register actions and intentions.",
    "NEW INSTRUCTION": "WHEN get_file_structure reports unsupported or parsing failed THEN read the file content directly"
}

[2025-12-23 13:35] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Cannot resolve extension point 'com.intellij.intentionAction'",
    "ROOT CAUSE": "IntentionsConfigurable.getDependencies uses a raw EP string not available in this SDK; it should reference the platform EP constant.",
    "PROJECT NOTE": "In settings/IntentionsConfigurable.kt, return listOf(com.intellij.codeInsight.intention.IntentionActionBean.EP_NAME) or remove getDependencies override if unnecessary.",
    "NEW INSTRUCTION": "WHEN referencing intention EP in Configurable.getDependencies THEN use IntentionActionBean.EP_NAME constant"
}

[2025-12-23 13:58] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Missing inspection description and undefined settings flag",
    "ROOT CAUSE": "The new inspection referenced a non-existent settings field and lacked its required description resource.",
    "PROJECT NOTE": "Inspections must have src/main/resources/inspectionDescriptions/<ClassName>.html and any feature flags must exist in PluginSettingsState before use.",
    "NEW INSTRUCTION": "WHEN create reports missing setting or inspection description THEN add setting and create description HTML"
}

[2025-12-23 13:58] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Missing inspection description and undefined settings flag",
    "ROOT CAUSE": "The new inspection referenced a non-existent settings property and lacked the required inspectionDescriptions/<ClassName>.html resource.",
    "PROJECT NOTE": "Inspection descriptions live under src/main/resources/inspectionDescriptions/<InspectionClassName>.html; add any new boolean flags to PluginSettingsState before use.",
    "NEW INSTRUCTION": "WHEN adding a new inspection class THEN define settings flag and add description HTML"
}

[2025-12-23 13:59] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Missing description and undefined settings flag",
    "ROOT CAUSE": "The inspection was created before adding its settings flag and required description resource.",
    "PROJECT NOTE": "Inspections here must have inspectionDescriptions/<ClassName>.html and any referenced settings must exist in PluginSettingsState before use.",
    "NEW INSTRUCTION": "WHEN create reports semantic errors for inspection THEN add setting and description HTML, then retry"
}

[2025-12-23 14:34] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Malformed multi-line replace; arguments truncated",
    "ROOT CAUSE": "The multi-line search/replace payload was not properly escaped and got truncated, so the tool could not apply the change.",
    "PROJECT NOTE": "Edits were targeting src/test/kotlin/fixtures/FakePopupHost.kt to track greyed-out indices and selection.",
    "NEW INSTRUCTION": "WHEN change spans multiple lines or contains quotes THEN use apply_patch with explicit diff"
}

[2025-12-23 14:36] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Used nonexistent JBPopupChooserBuilder method",
    "ROOT CAUSE": "The SDK lacks setSelectionCheck on JBPopupChooserBuilder, so the call is invalid.",
    "PROJECT NOTE": "In JbPopupHost.kt, append \"(already exported)\" via renderer and use disabled foreground; to skip greyed items, guard in itemChosenCallback or manage selection via a custom ListSelectionModel instead of unsupported builder APIs.",
    "NEW INSTRUCTION": "WHEN semantic errors mention unresolved builder method in JbPopupHost.kt THEN remove the call and handle via renderer and itemChosenCallback guards"
}

[2025-12-23 14:36] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Used nonexistent JBPopupChooserBuilder.setSelectionCheck",
    "ROOT CAUSE": "The project’s IntelliJ SDK does not provide setSelectionCheck on JBPopupChooserBuilder.",
    "PROJECT NOTE": "In JbPopupHost.kt, add the '(already exported)' suffix via the renderer. To skip greyed-out items, do not call setSelectionCheck; instead, handle skipping in onChosen or preselect the first non-greyed item before showing the popup.",
    "NEW INSTRUCTION": "WHEN JBPopupChooserBuilder method is missing in SDK THEN avoid it and handle in renderer/onChosen"
}

[2025-12-23 14:36] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Unresolved 'setSelectionCheck' in JBPopupChooserBuilder",
    "ROOT CAUSE": "The IntelliJ SDK in this project does not provide setSelectionCheck; a non-existent API was used.",
    "PROJECT NOTE": "In JbPopupHost.showChooserWithGreying, rely on the renderer for '(already exported)' text and disabled color; to skip greyed items, preselect the first non-greyed item or ignore greyed choices in the chosen-callback rather than calling missing builder methods.",
    "NEW INSTRUCTION": "WHEN builder method is an unresolved reference THEN remove it and implement behavior via callbacks"
}

[2025-12-27 17:42] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "NoSuchMethodError for PluginSettingsState.State constructor",
    "ROOT CAUSE": "Stale compiled classes on test classpath conflict after adding a new State field.",
    "PROJECT NOTE": "Project contains bin/main compiled outputs; these can shadow fresh classes and cause linkage errors after changing PluginSettingsState.State.",
    "NEW INSTRUCTION": "WHEN NoSuchMethodError mentions PluginSettingsState.State THEN run Gradle cleanTest test"
}

[2025-12-27 17:43] - Updated by Junie - Error analysis
{
    "TYPE": "runtime/linkage",
    "TOOL": "run_test",
    "ERROR": "NoSuchMethodError in PluginSettingsState.State constructor",
    "ROOT CAUSE": "Adding a new boolean field changed PluginSettingsState.State's constructor signature, breaking callers/deserialization expecting the old signature.",
    "PROJECT NOTE": "PluginSettingsState.State is instantiated/deserialized across the plugin; changing its primary constructor without compatibility guarantees causes NoSuchMethodError during tests.",
    "NEW INSTRUCTION": "WHEN adding a field to PluginSettingsState.State THEN keep defaults and add no-arg factory used by all callers"
}

[2025-12-27 17:45] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Method signature mismatch and unresolved reference in new file",
    "ROOT CAUSE": "Used wrong getCallType signature for PyTypeProviderBase and referenced PyMockType before defining it.",
    "PROJECT NOTE": "Override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: TypeEvalContext): Ref<PyType?>? and wrap results with Ref.create(...); implement PyMockType before referencing it.",
    "NEW INSTRUCTION": "WHEN overriding PyTypeProvider.getCallType THEN use PyCallSiteExpression and return Ref.create(type)"
}

[2025-12-27 17:46] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "getCallType overrides nothing; wrong signature and missing PyMockType class",
    "ROOT CAUSE": "Used PyCallExpression and PyType return instead of PyCallSiteExpression and Ref<PyType?>?, and referenced PyMockType before creating it.",
    "PROJECT NOTE": "In PyMockTypeProvider.kt, override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: TypeEvalContext): Ref<PyType?>? and return Ref.create(yourType); import com.intellij.openapi.util.Ref. Implement PyMockType in src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/type/PyMockType.kt before referencing it.",
    "NEW INSTRUCTION": "WHEN getCallType override error appears THEN use PyCallSiteExpression and return Ref.create(type)"
}

[2025-12-27 17:47] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Override signature mismatch; missing PyMockType class",
    "ROOT CAUSE": "Used an outdated getCallType signature and referenced PyMockType before implementing it.",
    "PROJECT NOTE": "In this SDK override fun getCallType(function, callSite: PyCallSiteExpression, context): Ref<PyType?> and return Ref.create(type); implement PyMockType (or adjust usage) before referencing it.",
    "NEW INSTRUCTION": "WHEN overriding PyTypeProviderBase.getCallType THEN use PyCallSiteExpression and return Ref<PyType?>"
}

[2025-12-27 17:48] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Wrong override signature and missing referenced type",
    "ROOT CAUSE": "getCallType used an outdated signature and returned PyType instead of Ref<PyType>, and PyMockType was referenced before being defined.",
    "PROJECT NOTE": "In this SDK, PyTypeProviderBase.getCallType signature is getCallType(PyFunction, PyCallSiteExpression, TypeEvalContext): Ref<PyType?>?; import com.intellij.openapi.util.Ref and wrap result with Ref.create(...).",
    "NEW INSTRUCTION": "WHEN overriding getCallType in PyTypeProviderBase THEN use PyCallSiteExpression and return Ref<PyType?>"
}

[2025-12-27 17:49] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "create",
    "ERROR": "Wrong getCallType signature; PyMockType unresolved",
    "ROOT CAUSE": "Used outdated PyTypeProviderBase.getCallType signature and referenced PyMockType before implementing/importing it.",
    "PROJECT NOTE": "In this SDK, override getCallType(function, callSite: PyCallSiteExpression, context) and return Ref<PyType?> (use Ref.create(...)); import com.intellij.openapi.util.Ref and com.jetbrains.python.psi.PyCallSiteExpression. Implement PyMockType before referencing it.",
    "NEW INSTRUCTION": "WHEN overriding getCallType in PyTypeProviderBase THEN use PyCallSiteExpression and return Ref<PyType?>"
}

[2025-12-27 17:53] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "create",
    "ERROR": "Wrong override and unresolved type reference",
    "ROOT CAUSE": "getCallType was implemented with an outdated signature and referenced undefined PyMockType.",
    "PROJECT NOTE": "In this SDK, override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: TypeEvalContext): Ref<PyType?>? and return Ref.create(type). Import com.jetbrains.python.psi.PyCallSiteExpression and com.intellij.openapi.util.Ref. Define PyMockType.kt before use.",
    "NEW INSTRUCTION": "WHEN overriding PyTypeProviderBase.getCallType THEN use SDK signature and return Ref.create"
}

[2025-12-27 17:57] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "create",
    "ERROR": "Wrong override signature and missing PyMockType",
    "ROOT CAUSE": "getCallType used PyCallExpression and returned PyType instead of Ref<PyType?>, and referenced PyMockType before defining it.",
    "PROJECT NOTE": "In this SDK, override fun getCallType(function: PyFunction, callSite: PyCallSiteExpression, context: TypeEvalContext): Ref<PyType?>? and import com.intellij.openapi.util.Ref; also ensure PyMockType is implemented before referencing.",
    "NEW INSTRUCTION": "WHEN overriding PyTypeProviderBase.getCallType THEN use PyCallSiteExpression and return Ref<PyType?>"
}

[2025-12-27 17:57] - Updated by Junie - Error analysis
{
    "TYPE": "compilation",
    "TOOL": "create",
    "ERROR": "Wrong override signature and missing type class",
    "ROOT CAUSE": "getCallType used PyCallExpression and returned PyType instead of SDK’s required PyCallSiteExpression and Ref<PyType?>; PyMockType class not yet implemented.",
    "PROJECT NOTE": "In this SDK, PyTypeProviderBase.getCallType(function, callSite, context) must return Ref<PyType?> and use PyCallSiteExpression; wrap results with Ref.create. Implement PyMockType before referencing it.",
    "NEW INSTRUCTION": "WHEN implementing PyTypeProvider.getCallType THEN use PyCallSiteExpression and return Ref.create(result)"
}

[2025-12-28 14:22] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "bash",
    "ERROR": "Tests failed with ComparisonFailure (3 failures)",
    "ROOT CAUSE": "Recent type resolution change caused expected vs actual output mismatches in PyMock tests.",
    "PROJECT NOTE": "Gradle writes detailed failure reports under build/reports/tests/test/; open the HTML to inspect diffs.",
    "NEW INSTRUCTION": "WHEN Gradle test output shows ComparisonFailure THEN rerun tests without tail to view full diffs"
}

[2025-12-28 14:24] - Updated by Junie - Error analysis
{
    "TYPE": "test failure",
    "TOOL": "bash: ./gradlew test",
    "ERROR": "JUnit ComparisonFailure: unexpected 'got Mock instead' warning",
    "ROOT CAUSE": "Changing spec to instance with toInstance() altered PyMockType callability, triggering a type mismatch warning in tests.",
    "PROJECT NOTE": "Edits in src/main/kotlin/.../type/PyMockTypeProvider.kt and PyMockType.kt must preserve previous callable semantics; PyMockTypeProviderFeaturesTest asserts no warning around Mock(spec=Foo).",
    "NEW INSTRUCTION": "WHEN changing Mock spec type handling THEN run PyMockTypeProviderFeaturesTest and verify no warnings"
}

[2025-12-28 14:26] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "Gradle :test",
    "ERROR": "ComparisonFailure in PyMockTypeProviderFeaturesTest",
    "ROOT CAUSE": "Returning PyMockType for Mock(spec=Class) made the expression appear non-callable, triggering a callable/Mock mismatch warning.",
    "PROJECT NOTE": "Feature tests under src/test/.../type/PyMockTypeProviderFeaturesTest.kt expect no warning highlight around Mock(spec=Foo) assignments.",
    "NEW INSTRUCTION": "WHEN test output shows \"Expected type '(...) -> Any', got 'Mock' instead\" THEN ensure PyMockType is callable only if specType is callable"
}

[2025-12-28 14:35] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Highlighting test failed: unexpected type mismatch warning",
    "ROOT CAUSE": "The test named 'NoWarning' still annotates mock_service with <warning>, so a warning is expected and single-test run fails when asserting no warning.",
    "PROJECT NOTE": "In PyMockTypeProviderFeaturesTest, use <warning> tags only for warnings you intend to assert; to assert clean code, remove them and let checkHighlighting detect unexpected warnings.",
    "NEW INSTRUCTION": "WHEN a test asserts no warning THEN remove <warning> tags around code under test"
}

[2025-12-28 14:37] - Updated by Junie - Error analysis
{
    "TYPE": "test assertion",
    "TOOL": "run_test",
    "ERROR": "Type mismatch warning caused test failure",
    "ROOT CAUSE": "The new test expected no warning, but PyTypeChecker still treats PyMockType as incompatible with its spec, yielding '() -> ExternalService' vs 'ExternalService'.",
    "PROJECT NOTE": "Highlighting tests must match current behavior; until assignability is implemented, annotate the expected warning or implement PyMockType compatibility with its spec type.",
    "NEW INSTRUCTION": "WHEN Mock(spec=T) passed to parameter typed T shows mismatch THEN implement type matching making PyMockType assignable to specType"
}

[2025-12-28 14:38] - Updated by Junie - Error analysis
{
    "TYPE": "semantic error",
    "TOOL": "search_replace",
    "ERROR": "Added PyClassLikeType without implementing abstract methods",
    "ROOT CAUSE": "The edit made PyMockType implement PyClassLikeType but omitted mandatory overrides, causing compilation errors.",
    "PROJECT NOTE": "PyMockType currently uses PyType and PyCallableType; avoid adding PyClassLikeType unless you implement all required methods.",
    "NEW INSTRUCTION": "WHEN adding a new interface to a class THEN implement all required abstract methods"
}

[2025-12-28 14:51] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "plugin.xml registration",
    "ERROR": "Cannot resolve language id 'Python' in plugin.xml",
    "ROOT CAUSE": "Python language is unavailable because the plugin/module dependency is not declared or loaded.",
    "PROJECT NOTE": "Declare the Python plugin dependency in src/main/resources/META-INF/plugin.xml, e.g., <depends>com.intellij.modules.python</depends> (or the correct Python plugin id for this platform).",
    "NEW INSTRUCTION": "WHEN adding plugin.xml extensions for Python language THEN declare Python plugin/module dependency in plugin.xml"
}