[2025-12-31 21:26] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml semantic errors: unknown language id 'Python'",
    "ROOT CAUSE": "plugin.xml validation requires Python language dependency; unresolved without Python plugin declared.",
    "PROJECT NOTE": "Add Python plugin dependency in plugin.xml, e.g., <depends>Pythonid</depends>, to resolve language=\"Python\" usages.",
    "NEW INSTRUCTION": "WHEN plugin.xml validation flags unknown Python language THEN declare <depends>Pythonid</depends> in plugin.xml"
}

[2026-01-01 09:58] - Updated by Junie - Error analysis
{
    "TYPE": "permission",
    "TOOL": "WrapTestInClassIntention",
    "ERROR": "PSI changed outside WriteCommandAction/CommandProcessor",
    "ROOT CAUSE": "Refactoring invokes PSI replace/insert without wrapping in a write command.",
    "PROJECT NOTE": "Wrap all PSI mutations in WriteCommandAction.runWriteCommandAction(project) and commit documents before/after changes.",
    "NEW INSTRUCTION": "WHEN performing PSI modifications in intention or handler THEN wrap logic in WriteCommandAction"
}

[2026-01-01 10:04] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'buildMethodFromFunction'.",
    "ROOT CAUSE": "invoke() was updated to call buildMethodFromFunction but the helper was never defined.",
    "PROJECT NOTE": "Add a helper to convert a top-level PyFunction into a class method (prepend self, keep decorators/body) similar to buildClassWithMethod.",
    "NEW INSTRUCTION": "WHEN semantic errors include Unresolved reference THEN define missing helper or add required import"
}

[2026-01-01 10:28] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "WrapTestInClassIntention",
    "ERROR": "Function inserted inside method due to wrong insertion anchor",
    "ROOT CAUSE": "The method was inserted at the caret/method body instead of the class statement list, causing nested def and bad indentation.",
    "PROJECT NOTE": "Create the method PSI with PyElementGenerator and add it to targetClass.getStatementList() via PyClassRefactoringUtil.insertMethodInProperPlace; do not insert by editor offset.",
    "NEW INSTRUCTION": "WHEN insertion point resolves inside a method body THEN add method to class statement list"
}

[2026-01-01 10:40] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "WrapTestInClassIntention",
    "ERROR": "Empty method body after removing ellipsis",
    "ROOT CAUSE": "Converting a one-line test with Ellipsis produced an empty suite without pass/ellipsis.",
    "PROJECT NOTE": "When building the new PyFunction PSI, ensure the body contains either the original Ellipsis expression statement or a PyPassStatement to keep Python syntax valid.",
    "NEW INSTRUCTION": "WHEN original body is Ellipsis or becomes empty THEN preserve ellipsis or insert 'pass' into method body"
}

[2026-01-01 11:02] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "run_test",
    "ERROR": "Headless tests fail due to interactive dialog in intention",
    "ROOT CAUSE": "invoke() shows a Swing dialog; unit test mode cannot handle interactive UI.",
    "PROJECT NOTE": "For IntelliJ plugin tests, gate dialogs with ApplicationManager.getApplication().isUnitTestMode and auto-select defaults.",
    "NEW INSTRUCTION": "WHEN unit test mode detected THEN bypass dialog and use default wrap settings"
}

[2026-01-01 11:15] - Updated by Junie - Error analysis
{
    "TYPE": "permission",
    "TOOL": "WrapTestInClassIntention",
    "ERROR": "Cannot modify a read-only PSI file during write action",
    "ROOT CAUSE": "Tests create read-only fixture files and the write command did not request write access via ReadonlyStatusHandler/FileModificationService.",
    "PROJECT NOTE": "Before PSI edits, call FileModificationService.getInstance().preparePsiElementsForWrite(listOf(file)) or ReadonlyStatusHandler.ensureFilesWritable(file.virtualFile) in tests and IDE.",
    "NEW INSTRUCTION": "WHEN write action targets PSI file THEN ensure file is writable using FileModificationService"
}

[2026-01-01 11:19] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "WrapTestInClassIntention.invoke",
    "ERROR": "Dialog shown while write-intent action is pending on EDT",
    "ROOT CAUSE": "DialogWrapper.showAndGet is invoked synchronously in invoke(), causing action updates during a pending write-intent read action on EDT.",
    "PROJECT NOTE": "In WrapTestInClassIntention.invoke at ~line 51, schedule dialog display via ApplicationManager.getApplication().invokeLater and continue logic inside that runnable.",
    "NEW INSTRUCTION": "WHEN needing to show a dialog from intention THEN open dialog via invokeLater and proceed inside"
}

[2026-01-01 11:42] - Updated by Junie - Error analysis
{
    "TYPE": "threading",
    "TOOL": "WrapTestInClassRefactoringHandler",
    "ERROR": "Dialog shown during write action on EDT",
    "ROOT CAUSE": "The refactoring handler opens DialogWrapper synchronously while a write-intent action is running or pending.",
    "PROJECT NOTE": "In WrapTestInClassRefactoringHandler.invoke, schedule dialog via ApplicationManager.getApplication().invokeLater and perform PSI edits inside WriteCommandAction.",
    "NEW INSTRUCTION": "WHEN refactoring handler needs to show dialog THEN open via invokeLater and run edits in WriteCommandAction"
}

[2026-01-01 11:49] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "WrapTestInClassIntention.invoke",
    "ERROR": "showAndGet used on modeless dialog",
    "ROOT CAUSE": "WrapTestInClassDialog is non‑modal while invoke() calls DialogWrapper.showAndGet().",
    "PROJECT NOTE": "In WrapTestInClassDialog (src/main/.../WrapTestInClassDialog.kt), either make the dialog modal (setModal(true)) or avoid showAndGet and use show() with a result handler; in tests still bypass UI via unit test mode/TestDialogManager.",
    "NEW INSTRUCTION": "WHEN dialog is non-modal THEN avoid showAndGet; make modal or use show with handler"
}

[2026-01-01 11:59] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference setTestDialogHandler",
    "ROOT CAUSE": "The IntelliJ SDK in this project does not provide TestDialogManager.setTestDialogHandler; only setTestDialog is available.",
    "PROJECT NOTE": "In tests, auto-accept DialogWrapper via TestDialogManager.setTestDialog(TestDialog.OK) and reset to DEFAULT in tearDown; do not use setTestDialogHandler.",
    "NEW INSTRUCTION": "WHEN TestDialogManager.setTestDialogHandler is unavailable THEN use setTestDialog(TestDialog.OK)/DEFAULT"
}

[2026-01-02 14:41] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'ApplicationManager' after edit",
    "ROOT CAUSE": "The import for ApplicationManager was removed while the code still referenced it.",
    "PROJECT NOTE": "In WrapTestInClassIntention.kt, avoid headless/unit-test checks; tests should drive dialogs via TestDialogManager.setTestDialog(TestDialog.OK).",
    "NEW INSTRUCTION": "WHEN an edit removes an import symbol THEN remove or replace all remaining references immediately"
}

[2026-01-02 15:49] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Too many arguments for function after edit",
    "ROOT CAUSE": "The call site added a stdlibService argument but the function signature was not updated.",
    "PROJECT NOTE": "In HideTransientImportProvider.kt, ensure filterTransientCandidatesReflectively signature matches its invocation when adding stdlibService.",
    "NEW INSTRUCTION": "WHEN adding parameters to a method call THEN update the method signature and imports"
}

[2026-01-02 15:55] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong constructor arguments for PyMoveModuleMembersProcessor",
    "ROOT CAUSE": "The test used an incorrect constructor signature, passing project and a boolean.",
    "PROJECT NOTE": "Use PyMoveModuleMembersProcessor(arrayOf<PsiNamedElement>(symbol), destPath).run() with only elements and destination; run PSI changes in a write command.",
    "NEW INSTRUCTION": "WHEN adding SDK constructor calls THEN verify and match the actual parameter signature"
}

[2026-01-02 17:15] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "get_file_structure",
    "ERROR": "Cannot display structure for non-directory file",
    "ROOT CAUSE": "get_file_structure expects a directory path but was given a single XML file.",
    "PROJECT NOTE": "To inspect src/main/resources/META-INF/plugin.xml, view its parent directories or open the file content with get_file_preview.",
    "NEW INSTRUCTION": "WHEN calling get_file_structure on a path THEN ensure the path is a directory"
}

[2026-01-02 17:16] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "run_test",
    "ERROR": "Test failed: built-ins still suggested",
    "ROOT CAUSE": "Expected-type completion contributor does not filter out Python built-in types.",
    "PROJECT NOTE": "Implement filtering in src/main/kotlin/.../completion/PyExpectedTypeCompletionContributor.kt, method shouldSkipExpectedTypeSuggestion.",
    "NEW INSTRUCTION": "WHEN expected type candidate is a Python builtin THEN exclude it from suggestions"
}

[2026-01-02 22:42] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "run_test",
    "ERROR": "Local project symbol missing from import suggestion under transient filter",
    "ROOT CAUSE": "HideTransientImportProvider treats non-dependency candidates as transient and does not exempt current project modules.",
    "PROJECT NOTE": "Adjust src/main/kotlin/.../imports/HideTransientImportProvider.kt to detect candidates from the current module/project (e.g., via ModuleUtilCore.findModuleForPsiElement or VirtualFile under project base dir) and always keep them; include both source and test roots.",
    "NEW INSTRUCTION": "WHEN import candidate originates from current project/module THEN bypass transient filtering and keep it"
}

[2026-01-02 22:46] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch in getDirectDependencies call",
    "ROOT CAUSE": "Call site passed a Module while getDirectDependencies still expected a PsiReference.",
    "PROJECT NOTE": "In src/main/kotlin/.../imports/HideTransientImportProvider.kt, ensure getDirectDependencies signature and all invocations use the same parameter type.",
    "NEW INSTRUCTION": "WHEN changing method parameter types THEN update all call sites accordingly"
}

