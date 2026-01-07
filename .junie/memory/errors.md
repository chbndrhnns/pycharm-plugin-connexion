[2026-01-05 09:02] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PyResolveUtils.findMember",
    "ERROR": "IndexOutOfBoundsException accessing first resolve result for import",
    "ROOT CAUSE": "Code assumes el.multiResolve() returns at least one result and indexes [0] without checks.",
    "PROJECT NOTE": "In PyResolveUtils.kt (findMember), guard both from-import and import branches: use RatedResolveResult.sorted(results).firstOrNull()?.element and return null if empty.",
    "NEW INSTRUCTION": "WHEN multiResolve returns no results THEN return null instead of indexing the first element"
}

[2026-01-05 14:18] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong PsiTestUtil.addProjectLibrary overload used",
    "ROOT CAUSE": "A List<VirtualFile> was passed where the API expects VirtualFile varargs or List<String> paths.",
    "PROJECT NOTE": "In tests, call PsiTestUtil.addProjectLibrary(module, \"MyLib\", libDir) with VirtualFile varargs or use the overload that accepts String paths.",
    "NEW INSTRUCTION": "WHEN adding project library in tests THEN pass VirtualFile varargs, not List<VirtualFile>"
}

[2026-01-05 14:19] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong argument type for addProjectLibrary",
    "ROOT CAUSE": "Used a List<VirtualFile> for PsiTestUtil.addProjectLibrary instead of the vararg VirtualFile overload or String paths overload.",
    "PROJECT NOTE": "In tests, prefer PsiTestUtil.addProjectLibrary(module, name, virtualFile) using the vararg VirtualFile overload; do not pass List<VirtualFile>.",
    "NEW INSTRUCTION": "WHEN editing PsiTestUtil.addProjectLibrary call THEN use vararg VirtualFile parameters, not List"
}

[2026-01-05 14:22] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong arguments to PsiTestUtil.addProjectLibrary",
    "ROOT CAUSE": "Used the overload expecting vararg VirtualFile but passed a List instead.",
    "PROJECT NOTE": "In tests, call PsiTestUtil.addProjectLibrary(module, name, vararg roots: VirtualFile) or provide String paths for the paths-based overload; do not pass List<VirtualFile>.",
    "NEW INSTRUCTION": "WHEN adding a project library in tests THEN use vararg VirtualFile overload, not List"
}

[2026-01-05 14:24] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong argument types in addProjectLibrary call",
    "ROOT CAUSE": "Used List<VirtualFile> for addProjectLibrary where vararg VirtualFile or List<String> is required.",
    "PROJECT NOTE": "In tests, call PsiTestUtil.addProjectLibrary(module, name, vararg VirtualFile) or addProjectLibrary(module, name, listOf(String paths)); do not pass List<VirtualFile>.",
    "NEW INSTRUCTION": "WHEN adding project library in tests THEN pass vararg VirtualFile or String path list"
}

[2026-01-05 14:27] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong arguments for PsiTestUtil.addProjectLibrary",
    "ROOT CAUSE": "The edit passed List<VirtualFile> where the API expects vararg VirtualFile or List<String> paths.",
    "PROJECT NOTE": "In tests, call PsiTestUtil.addProjectLibrary(module, name, virtualFile) with VirtualFile vararg, or use addLibrary with String paths.",
    "NEW INSTRUCTION": "WHEN editing addProjectLibrary invocation THEN pass VirtualFile varargs instead of a List"
}

[2026-01-05 14:28] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch in PsiTestUtil.addProjectLibrary call",
    "ROOT CAUSE": "Edited test passed List<VirtualFile> to addProjectLibrary, but overload expects VirtualFile varargs or List<String> paths.",
    "PROJECT NOTE": "In tests, prefer PsiTestUtil.addProjectLibrary(module, name, vararg roots: VirtualFile) with VirtualFile arguments from tempDirFixture/findOrCreateDir.",
    "NEW INSTRUCTION": "WHEN addProjectLibrary call shows type mismatch THEN use VirtualFile vararg overload with VirtualFile arguments"
}

[2026-01-05 15:00] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "ToggleTypeAliasIntention",
    "ERROR": "Intention unavailable; isAvailable() returned false",
    "ROOT CAUSE": "isAvailable() does not recognize Python 3.12 PEP 695 'type' alias syntax, so it never enables.",
    "PROJECT NOTE": "Update ToggleTypeAliasIntention.isAvailable to handle PyTypeAliasStatement (PEP 695) when LanguageLevel >= PYTHON312 in src/main/kotlin/.../intention/typealias/ToggleTypeAliasIntention.kt.",
    "NEW INSTRUCTION": "WHEN code contains PEP 695 'type' alias and PYTHON312+ THEN enable intention by recognizing PyTypeAliasStatement"
}

[2026-01-05 15:06] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "PythonMockSdk cannot find testData directory",
    "ROOT CAUSE": "PythonMockSdk.createRoots reads a non-existent testData path (likely with stray whitespace), preventing proper SDK setup and making the intention unavailable.",
    "PROJECT NOTE": "Check fixtures.PythonTestSetup/PythonMockSdk for the testData path; remove trailing whitespace and ensure a testData directory exists at the project root.",
    "NEW INSTRUCTION": "WHEN test logs NoSuchFileException for testData in PythonMockSdk THEN fix path and ensure testData exists"
}

[2026-01-05 15:09] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "Missing testData directory for PythonMockSdk",
    "ROOT CAUSE": "PythonMockSdk.createRoots reads project-root/testData but the directory is absent/misnamed, causing NoSuchFileException.",
    "PROJECT NOTE": "Create a testData directory at the project root (src/testData if code expects that path) or update PythonMockSdk.kt (createRoots) to point to the actual test data location used by this repo.",
    "NEW INSTRUCTION": "WHEN logs show NoSuchFileException for project-root/testData THEN create folder or fix SDK path in PythonMockSdk"
}

[2026-01-05 15:36] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_project",
    "ERROR": "Query returned more than 100 results",
    "ROOT CAUSE": "The search term 'parameter object' is too broad for the repository.",
    "PROJECT NOTE": "Parameter-object code resides under src/main/kotlin/.../intention/parameterobject; search for IntroduceParameterObject or InlineParameterObject within that package.",
    "NEW INSTRUCTION": "WHEN search_project reports more than 100 results THEN refine query with file paths or class names and retry"
}

[2026-01-05 15:39] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "cat: illegal option -A on this system",
    "ROOT CAUSE": "Used GNU-specific cat flag (-A) in a BSD/macOS environment where it's unsupported.",
    "PROJECT NOTE": "Use open_entire_file for source inspection or POSIX-safe commands (cat|head|sed) without GNU-only flags.",
    "NEW INSTRUCTION": "WHEN bash command uses GNU-only cat flags THEN replace with POSIX-safe commands or open_entire_file"
}

[2026-01-05 15:39] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "cat: illegal option -A",
    "ROOT CAUSE": "Used GNU-specific cat flag -A on an environment where cat doesn't support it.",
    "PROJECT NOTE": "-",
    "NEW INSTRUCTION": "WHEN command includes 'cat -A' THEN use open_entire_file or sed -n to preview content"
}

[2026-01-05 15:40] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "cat: illegal option -A",
    "ROOT CAUSE": "The command used GNU-specific 'cat -A' which is unsupported by the system's cat.",
    "PROJECT NOTE": "Use open_entire_file for viewing files or stick to POSIX-compatible bash commands.",
    "NEW INSTRUCTION": "WHEN needing to preview a file content THEN use open_entire_file instead of cat"
}

[2026-01-05 16:07] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "run_test",
    "ERROR": "Unresolved reference 'FeatureDefinition' in build",
    "ROOT CAUSE": "IncubatingFeatureNotifier.kt references FeatureDefinition which is missing or renamed and not imported/defined.",
    "PROJECT NOTE": "Check settings/IncubatingFeatureNotifier.kt around line ~92; ensure a settings.FeatureDefinition (or the correct replacement type) exists under settings/ and update imports/usages accordingly.",
    "NEW INSTRUCTION": "WHEN compiler reports unresolved 'FeatureDefinition' THEN create/import correct type or update usages to existing API"
}

[2026-01-05 17:24] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "create",
    "ERROR": "Unresolved reference 'enableUnexportSymbolIntention' in test",
    "ROOT CAUSE": "The test referenced a new settings flag that was not yet defined in PluginSettingsState.",
    "PROJECT NOTE": "Feature toggles live in PluginSettingsState.State with @Feature metadata; add the boolean there before using it in tests.",
    "NEW INSTRUCTION": "WHEN adding tests referencing new settings flag THEN define flag in PluginSettingsState.State first"
}

[2026-01-05 17:26] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "create",
    "ERROR": "Unresolved reference enableUnexportSymbolIntention",
    "ROOT CAUSE": "The test enabled a settings flag that was not yet defined in PluginSettingsState.",
    "PROJECT NOTE": "Define feature flags in PluginSettingsState.State before referencing them in tests; keep plugin settings synchronized with new intentions.",
    "NEW INSTRUCTION": "WHEN adding a test toggling a new feature flag THEN add the flag to PluginSettingsState first"
}

[2026-01-05 17:28] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "create",
    "ERROR": "Test references undefined setting enableUnexportSymbolIntention",
    "ROOT CAUSE": "The test was added before defining the corresponding flag in PluginSettingsState.",
    "PROJECT NOTE": "Plugin settings flags are defined in src/main/kotlin/.../settings/PluginSettingsState.kt with @Feature metadata; add new booleans there before tests use them.",
    "NEW INSTRUCTION": "WHEN a test enables a new settings flag THEN add the flag to PluginSettingsState first"
}

[2026-01-05 18:58] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "NoSuchMethodError for PluginSettingsState.State constructor",
    "ROOT CAUSE": "PluginSettingsState.State constructor signature changed, breaking binary compatibility with test setup/serialization.",
    "PROJECT NOTE": "PersistentStateComponent expects a bean with a no-arg constructor and mutable properties; define State with no primary constructor (properties initialized inline) or add an explicit zero-arg secondary constructor to remain compatible.",
    "NEW INSTRUCTION": "WHEN modifying PluginSettingsState.State fields THEN preserve a zero-arg constructor and inline-initialized mutable properties"
}

[2026-01-06 11:20] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Added arg 'inlineAllOccurrences'; helper removeParameterObjectClass missing",
    "ROOT CAUSE": "Call site was updated without changing the callee signature or adding the new helper.",
    "PROJECT NOTE": "Update prepareCallSiteUpdates(...) signature in PyInlineParameterObjectProcessor.kt to include inlineAllOccurrences, and implement removeParameterObjectClass() to delete the parameter object class and clean up.",
    "NEW INSTRUCTION": "WHEN adding parameters to a method call THEN update the callee signature accordingly"
}

[2026-01-06 11:21] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Call passes inlineAllOccurrences; callee lacks parameter; removeClass handler missing",
    "ROOT CAUSE": "The call site was updated to pass inlineAllOccurrences and call removeParameterObjectClass(), but prepareCallSiteUpdates signature and the removeParameterObjectClass implementation were not added.",
    "PROJECT NOTE": "Update prepareCallSiteUpdates to accept inlineAllOccurrences and implement removeParameterObjectClass(plan) to delete the parameter object class PSI when requested.",
    "NEW INSTRUCTION": "WHEN adding a new argument or method call THEN update callee signature and implement method"
}

[2026-01-06 11:23] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Named parameter missing and helper method undefined",
    "ROOT CAUSE": "Call site passed inlineAllOccurrences and used removeParameterObjectClass before updating prepareCallSiteUpdates signature and defining the helper.",
    "PROJECT NOTE": "In PyInlineParameterObjectProcessor.kt, add inlineAllOccurrences to prepareCallSiteUpdates(...) and implement removeParameterObjectClass(plan) to delete the parameter object class PSI.",
    "NEW INSTRUCTION": "WHEN adding named arguments to a method call THEN update callee signature and define new helpers"
}

[2026-01-06 11:25] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "prepareCallSiteUpdates lacks inlineAllOccurrences; removeParameterObjectClass undefined",
    "ROOT CAUSE": "The call site added inlineAllOccurrences and class removal logic without updating the helper signature or implementing the removal method.",
    "PROJECT NOTE": "Update prepareCallSiteUpdates in PyInlineParameterObjectProcessor to accept inlineAllOccurrences and filter usages; add removeParameterObjectClass to delete the parameter object class under WriteCommandAction.",
    "NEW INSTRUCTION": "WHEN prepareCallSiteUpdates is called with inlineAllOccurrences THEN add parameter to method and adapt logic"
}

[2026-01-06 12:31] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PyInlineParameterObjectProcessor.countUsages",
    "ERROR": "Usage count returns 0 despite multiple parameter object usages",
    "ROOT CAUSE": "countUsages searches references to the function and filters call sites, but the dialog should be based on usages of the parameter object (constructor calls and typed parameters), so it returns 0 when there are no calls to the selected function.",
    "PROJECT NOTE": "Compute usages from the parameter object class: count constructor calls (FooParams(...)) and function/method parameters annotated as FooParams; for the selected function, still collect its call sites for inlining.",
    "NEW INSTRUCTION": "WHEN determining dialog usage count THEN search parameter object class references, not function references"
}

[2026-01-06 12:35] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PyInlineParameterObjectProcessor.countUsages",
    "ERROR": "Counting function calls instead of parameter object usages",
    "ROOT CAUSE": "countUsages() searches references to the selected function and tallies call sites, but the user’s scenario has multiple functions that accept the parameter object type and zero call sites, yielding 0.",
    "PROJECT NOTE": "When caret is on a parameter annotation or the parameter object class, resolve the class and count its usages as: (a) function parameters annotated with the class and (b) constructor calls passed at call sites; only fall back to function call counting when invoked on a function.",
    "NEW INSTRUCTION": "WHEN element indicates parameter object annotation or class THEN count functions using that class as a parameter"
}

[2026-01-06 12:39] - Updated by Junie - Error analysis
{
    "TYPE": "threading",
    "TOOL": "PyInlineParameterObjectProcessor.countUsages",
    "ERROR": "Blocking search invoked on EDT; requires background thread with progress",
    "ROOT CAUSE": "countUsages performs reference/PSI lookups on EDT which triggers runBlocking and fails.",
    "PROJECT NOTE": "In InlineParameterObjectRefactoringHandler.invoke, wrap usage counting in runWithModalProgressBlocking + readAction, or move that wrapping into countUsages itself.",
    "NEW INSTRUCTION": "WHEN running ReferencesSearch or PSI scans from handler THEN use runWithModalProgressBlocking(readAction)"
}

[2026-01-06 12:42] - Updated by Junie - Error analysis
{
    "TYPE": "threading",
    "TOOL": "InlineParameterObjectRefactoringHandler.invoke",
    "ERROR": "Blocking search called on EDT",
    "ROOT CAUSE": "countUsages triggers ReferencesSearch/findAll on EDT; background thread required.",
    "PROJECT NOTE": "Wrap usage counting in InlineParameterObjectRefactoringHandler (and any findAll in PyInlineParameterObjectProcessor) with runWithModalProgressBlocking(project, ...) { readAction { ... } } to avoid EDT violations (e.g., PyInlineParameterObjectProcessor.kt:54, handler invoke()).",
    "NEW INSTRUCTION": "WHEN performing PSI searches or counting usages THEN run via runWithModalProgressBlocking readAction"
}

[2026-01-06 12:44] - Updated by Junie - Error analysis
{
    "TYPE": "threading",
    "TOOL": "PyInlineParameterObjectProcessor.removeParameterObjectClass",
    "ERROR": "Blocking operation on EDT: PythonPackageManager.forSdk invoked",
    "ROOT CAUSE": "A blocking search/package query ran on the Event Dispatch Thread during refactoring.",
    "PROJECT NOTE": "Move all ReferencesSearch/Query.findAll and package/Sdk-related calls to a background read action via runWithModalProgressBlocking; keep PSI deletions in WriteCommandAction.",
    "NEW INSTRUCTION": "WHEN blocking PSI search or package manager call on EDT THEN wrap with runWithModalProgressBlocking(project){ readAction{ ... } }"
}

[2026-01-06 12:58] - Updated by Junie - Error analysis
{
    "TYPE": "semantic",
    "TOOL": "search_replace",
    "ERROR": "Used deprecated PyPackageManager API",
    "ROOT CAUSE": "The edit imported com.jetbrains.python.packaging.PyPackageManager which is deprecated and scheduled for removal.",
    "PROJECT NOTE": "Use PyPackageService (e.g., PyPackageService.getInstance(project).getPackages(sdk)) instead of PyPackageManager to obtain packages and their top-level modules.",
    "NEW INSTRUCTION": "WHEN import causes deprecation error in IDE THEN replace with the non-deprecated successor API"
}

[2026-01-06 13:00] - Updated by Junie - Error analysis
{
    "TYPE": "invalid api",
    "TOOL": "search_replace",
    "ERROR": "Deprecated API marked for removal used",
    "ROOT CAUSE": "Code introduced com.jetbrains.python.packaging.PyPackageManager which is deprecated and flagged by semantic checks.",
    "PROJECT NOTE": "Prefer non-deprecated Python plugin APIs; if migration path is unclear, temporarily add @Suppress(\"DEPRECATION\") near usage and track a TODO.",
    "NEW INSTRUCTION": "WHEN semantic checker flags deprecated API marked for removal THEN replace with supported API or add @Suppress(\"DEPRECATION\")"
}

[2026-01-06 13:03] - Updated by Junie - Error analysis
{
    "TYPE": "semantic",
    "TOOL": "search_replace",
    "ERROR": "Deprecated API used: PyPackageManager marked for removal",
    "ROOT CAUSE": "The edit introduced com.jetbrains.python.packaging.PyPackageManager, which is deprecated and flagged as an error in this project SDK.",
    "PROJECT NOTE": "Use the newer packaging management API (e.g., com.jetbrains.python.packaging.management.PythonPackageManager.forSdk(sdk)) to list installed packages and read top-level modules.",
    "NEW INSTRUCTION": "WHEN semantic errors flag deprecated or removed API THEN replace with supported alternative from current SDK"
}

[2026-01-06 13:03] - Updated by Junie - Error analysis
{
    "TYPE": "semantic",
    "TOOL": "search_replace",
    "ERROR": "Deprecated API used: PyPackageManager",
    "ROOT CAUSE": "The edit introduced PyPackageManager which is deprecated and flagged by the project checker.",
    "PROJECT NOTE": "In imports/HideTransientImportProvider.kt, use PythonPackagingService.getInstance(sdk).getPackages() to list installed packages instead of PyPackageManager.",
    "NEW INSTRUCTION": "WHEN semantic errors mention 'deprecated and marked for removal' THEN replace PyPackageManager with PythonPackagingService.getInstance(sdk).getPackages()"
}

[2026-01-06 17:25] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch for runWithSourceRoots",
    "ROOT CAUSE": "runWithSourceRoots expects a List<VirtualFile> but a String was provided.",
    "PROJECT NOTE": "In fixtures.TestBase, obtain a VirtualFile via myFixture.tempDirFixture.findOrCreateDir(\"tests\") and call runWithSourceRoots(listOf(dir)).",
    "NEW INSTRUCTION": "WHEN adding test source roots THEN pass List<VirtualFile> from tempDirFixture.findOrCreateDir"
}

[2026-01-06 17:25] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch for runWithSourceRoots",
    "ROOT CAUSE": "runWithSourceRoots expects List<VirtualFile> but a String literal was passed.",
    "PROJECT NOTE": "Create a VirtualFile for the 'tests' dir (e.g., myFixture.tempDirFixture.findOrCreateDir(\"tests\")) and pass listOf(vFile) to runWithSourceRoots.",
    "NEW INSTRUCTION": "WHEN calling runWithSourceRoots in tests THEN pass List<VirtualFile> not String"
}

[2026-01-06 17:26] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "runWithSourceRoots expects List<VirtualFile>, received String literal",
    "ROOT CAUSE": "The test used runWithSourceRoots(\"tests\") but the helper requires VirtualFile roots, not a String.",
    "PROJECT NOTE": "Create the test root via myFixture.tempDirFixture.findOrCreateDir(\"tests\") (or addFileToProject to create it) and call runWithSourceRoots(listOf(dirVirtualFile)).",
    "NEW INSTRUCTION": "WHEN using runWithSourceRoots in tests THEN pass List<VirtualFile> obtained from tempDirFixture"
}

[2026-01-06 17:27] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Wrong argument type for runWithSourceRoots",
    "ROOT CAUSE": "The test passed a String to runWithSourceRoots, which expects a List<VirtualFile>.",
    "PROJECT NOTE": "In UseActualOutcomeAvailabilityTest, obtain a VirtualFile via myFixture.tempDirFixture.findOrCreateDir(\"tests\") (or addFileToProject and use parent) and call runWithSourceRoots(listOf(vf)).",
    "NEW INSTRUCTION": "WHEN calling runWithSourceRoots in tests THEN pass List<VirtualFile> from tempDirFixture"
}

[2026-01-06 17:28] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch for runWithSourceRoots",
    "ROOT CAUSE": "The test helper runWithSourceRoots expects a List<VirtualFile> but a String was passed.",
    "PROJECT NOTE": "In fixtures.TestBase, obtain a VirtualFile via myFixture.tempDirFixture.findOrCreateDir(\"tests\") and pass listOf(vf) to runWithSourceRoots.",
    "NEW INSTRUCTION": "WHEN using runWithSourceRoots in tests THEN pass List<VirtualFile> created from tempDir"
}

[2026-01-06 17:30] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Argument type mismatch for runWithSourceRoots parameter",
    "ROOT CAUSE": "runWithSourceRoots was called with a String instead of List<VirtualFile>.",
    "PROJECT NOTE": "In fixtures.TestBase, runWithSourceRoots expects List<VirtualFile>; obtain the tests root via myFixture.tempDirFixture.findOrCreateDir(\"tests\") (or similar) and pass listOf(virtualFile).",
    "NEW INSTRUCTION": "WHEN calling runWithSourceRoots THEN pass List<VirtualFile> obtained via tempDirFixture or VFS"
}

[2026-01-06 17:32] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "String passed to runWithSourceRoots; expected List<VirtualFile>",
    "ROOT CAUSE": "The test edit used runWithSourceRoots(\"tests\") instead of a list of VirtualFiles.",
    "PROJECT NOTE": "In tests, obtain VirtualFile roots via myFixture.tempDirFixture.getFile(\"tests\") and pass listOf(vf) to runWithSourceRoots.",
    "NEW INSTRUCTION": "WHEN adding source roots in tests THEN pass List<VirtualFile> from tempDirFixture.getFile"
}

[2026-01-06 17:35] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'getQName' in test file",
    "ROOT CAUSE": "The test uses the getQName extension but lacks the import for com.jetbrains.python.extensions.getQName.",
    "PROJECT NOTE": "When using Python PSI extension methods in tests, explicitly import the extension functions just like in main sources.",
    "NEW INSTRUCTION": "WHEN Kotlin test uses getQName extension THEN import com.jetbrains.python.extensions.getQName"
}

[2026-01-06 17:36] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'getQName' in test file",
    "ROOT CAUSE": "The test started using the getQName extension without importing its package.",
    "PROJECT NOTE": "Import com.jetbrains.python.extensions.getQName in tests when calling PyFile.getQName().",
    "NEW INSTRUCTION": "WHEN adding calls to getQName extension THEN import com.jetbrains.python.extensions.getQName"
}

[2026-01-06 17:37] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'getQName'",
    "ROOT CAUSE": "The test added calls to getQName without importing the extension function.",
    "PROJECT NOTE": "Kotlin uses extension imports explicitly; add import com.jetbrains.python.extensions.getQName in tests that call PyFile.getQName().",
    "NEW INSTRUCTION": "WHEN semantic errors show Unresolved reference 'getQName' THEN import com.jetbrains.python.extensions.getQName"
}

[2026-01-06 17:52] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestLocationUrlFactory.fromPyFunction",
    "ERROR": "Qualified name missing source root prefix",
    "ROOT CAUSE": "buildQualifiedName only prepends the root name for top-level files, but pytest keys include the source root directory (e.g., 'tests.') for all files under that root.",
    "PROJECT NOTE": "In PytestLocationUrlFactory.buildQualifiedName, for isSourceRoot=true always prefix root.name to the module path so tests/unit/test_.py becomes tests.unit.test_.<func>.",
    "NEW INSTRUCTION": "WHEN computing qualified name under a source root THEN always prefix source root directory name"
}

[2026-01-06 17:53] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestLocationUrlFactory.fromPyFunction",
    "ERROR": "Missing source root prefix in qualified name",
    "ROOT CAUSE": "buildQualifiedName prepends the source root name only for files directly under the root, but pytest keys include the root name for nested paths too.",
    "PROJECT NOTE": "In PytestLocationUrlFactory.buildQualifiedName (lines ~115-127), always prepend root.name when isSourceRoot is true; e.g., finalModulePath = if (isSourceRoot) \"${root.name}.$modulePath\" else modulePath.",
    "NEW INSTRUCTION": "WHEN building qualified name for source root THEN always prepend source root directory name"
}

[2026-01-06 20:37] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'Project' after edit",
    "ROOT CAUSE": "The edit introduced Project type usage without adding its import.",
    "PROJECT NOTE": "Add import com.intellij.openapi.project.Project to UseActualTestOutcomeFromTreeAction.kt when referencing Project.",
    "NEW INSTRUCTION": "WHEN adding a new type reference THEN add the corresponding import immediately"
}

[2026-01-06 20:37] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'Project' after edit",
    "ROOT CAUSE": "The edit introduced type Project without adding its import statement.",
    "PROJECT NOTE": "In UseActualTestOutcomeFromTreeAction.kt, import com.intellij.openapi.project.Project when referencing Project.",
    "NEW INSTRUCTION": "WHEN semantic errors show unresolved symbol after edit THEN add the necessary import immediately"
}

[2026-01-06 20:38] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'Project'",
    "ROOT CAUSE": "A new method introduced the Project type without adding its import.",
    "PROJECT NOTE": "In UseActualTestOutcomeFromTreeAction.kt, import com.intellij.openapi.project.Project when referencing Project in new methods.",
    "NEW INSTRUCTION": "WHEN added code references new classes THEN add their imports explicitly"
}

[2026-01-06 20:38] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'Project'",
    "ROOT CAUSE": "New code referenced Project in a signature without adding its import.",
    "PROJECT NOTE": "In UseActualTestOutcomeFromTreeAction.kt, import com.intellij.openapi.project.Project when using Project.",
    "NEW INSTRUCTION": "WHEN introducing new IntelliJ API types in edits THEN add the required import statements"
}

[2026-01-06 20:59] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "UseActualTestOutcomeFromTreeAction.update",
    "ERROR": "Action visible despite equal expected and actual",
    "ROOT CAUSE": "Expected value extraction fails by misparsing SMTestProxy.locationUrl as a file path, returns null, and the code defaults to showing the action.",
    "PROJECT NOTE": "SMTestProxy.locationUrl is python<sourceRoot>://<qualified.name>, not a filesystem path. In tree actions, obtain VirtualFile/PSI via proxy.getLocation(project, properties.scope) from TestTreeView.MODEL_DATA_KEY.",
    "NEW INSTRUCTION": "WHEN expected value extraction returns null THEN hide if diff.expected equals diff.actual"
}

[2026-01-06 21:04] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Semantic errors: extend final class and override nonexistent method",
    "ROOT CAUSE": "The test edit attempted to subclass a final action and override a non-existent getLocation method, introducing unresolved API references.",
    "PROJECT NOTE": "UseActualTestOutcomeFromTreeAction is a final Kotlin class; extract location/diff checks into a helper/service and inject or call it from tests instead of subclassing the action.",
    "NEW INSTRUCTION": "WHEN a test needs to customize action behavior THEN extract a helper interface and inject a fake"
}

[2026-01-06 21:06] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Edit introduced Kotlin semantic errors",
    "ROOT CAUSE": "The test tried to subclass a final action and override a non-existent method while adding unresolved API references.",
    "PROJECT NOTE": "UseActualTestOutcomeFromTreeAction is final; do not subclass it in tests. Validate availability logic through UseActualOutcomeUseCase or extract a reusable helper.",
    "NEW INSTRUCTION": "WHEN search_replace reports semantic errors THEN undo edit and test UseActualOutcomeUseCase directly"
}

[2026-01-06 21:08] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Tried overriding methods on a final class in tests",
    "ROOT CAUSE": "The test edit attempted to subclass a final AnAction and override a non-existent method, introducing unresolved references.",
    "PROJECT NOTE": "Do not subclass UseActualTestOutcomeFromTreeAction in tests; instead test UseActualOutcomeUseCase directly or extract a non-final, injectable helper used by the action.",
    "NEW INSTRUCTION": "WHEN a test needs to alter action behavior THEN extract injectable helper and test the helper"
}

[2026-01-06 22:36] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "run_test",
    "ERROR": "Intention offered for @classmethod methods",
    "ROOT CAUSE": "AddSelfParameterIntention.isAvailable does not exclude functions decorated with @classmethod.",
    "PROJECT NOTE": "Update src/main/kotlin/.../intention/AddSelfParameterIntention.kt isAvailable() to check the PyFunction decorator list for 'classmethod' in addition to 'staticmethod' and return false.",
    "NEW INSTRUCTION": "WHEN method decorators include 'classmethod' THEN return false in isAvailable"
}

[2026-01-06 22:43] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "get_file_structure",
    "ERROR": "Cannot display plugin.xml structure; unsupported file type or parsing failed",
    "ROOT CAUSE": "The file-structure tool cannot parse large or non-code XML files like plugin.xml.",
    "PROJECT NOTE": "plugin.xml is under src/main/resources/META-INF/plugin.xml; view/edit via full-content reading instead of structure.",
    "NEW INSTRUCTION": "WHEN get_file_structure reports unsupported or parsing failure THEN open entire file content with open_entire_file"
}

[2026-01-06 22:44] - Updated by Junie - Error analysis
{
    "TYPE": "tool failure",
    "TOOL": "get_file_structure",
    "ERROR": "Cannot parse plugin.xml file structure",
    "ROOT CAUSE": "get_file_structure does not support XML files like plugin.xml and returned a parse failure.",
    "PROJECT NOTE": "Open plugin.xml with the open tool to inspect and edit extension registrations (e.g., lineMarkerProvider, runLineMarkerContributor).",
    "NEW INSTRUCTION": "WHEN get_file_structure says cannot display file structure THEN use open to view the file content"
}

[2026-01-06 22:46] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Semantic errors after signature change",
    "ROOT CAUSE": "The edit introduced a new PsiElement parameter without adding its import and altered code paths, causing unresolved types and mismatched calls.",
    "PROJECT NOTE": "When touching UseActualOutcomeUseCase.kt, import com.intellij.psi.PsiElement and ensure PyAssertStatement API usage matches existing code; avoid partial block replacements that drop required imports.",
    "NEW INSTRUCTION": "WHEN search_replace adds new types or parameters THEN add necessary imports and update all usages"
}

[2026-01-06 22:47] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Unresolved PsiElement and type mismatch after edit",
    "ROOT CAUSE": "The edit added an overload using PsiElement but did not import it, causing element to be Any and breaking PsiTreeUtil calls.",
    "PROJECT NOTE": "In UseActualOutcomeUseCase.kt, import com.intellij.psi.PsiElement when adding an isAvailable(project, element, file) overload.",
    "NEW INSTRUCTION": "WHEN semantic errors show 'Unresolved reference PsiElement' THEN add import com.intellij.psi.PsiElement and retype parameters"
}

[2026-01-06 22:47] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Semantic errors after edit: unresolved references and type mismatch",
    "ROOT CAUSE": "Method signature change introduced new types without adding required imports, causing unresolved references and cascading type inference errors.",
    "PROJECT NOTE": "In UseActualOutcomeUseCase.kt, ensure imports for com.intellij.psi.PsiElement, com.intellij.psi.util.PsiTreeUtil, and Python PSI classes (PyAssertStatement, PyBinaryExpression, PyFunction) are present after edits.",
    "NEW INSTRUCTION": "WHEN semantic errors report 'Unresolved reference' after edit THEN add missing imports and correct types"
}

[2026-01-06 22:48] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Unresolved reference 'PsiElement' after edit",
    "ROOT CAUSE": "The edit added a PsiElement-typed parameter without importing com.intellij.psi.PsiElement, causing type inference to degrade and subsequent errors.",
    "PROJECT NOTE": "In pytest/outcome/UseActualOutcomeUseCase.kt, import com.intellij.psi.PsiElement when overloading isAvailable with a PsiElement parameter.",
    "NEW INSTRUCTION": "WHEN semantic errors show 'Unresolved reference PsiElement' THEN import com.intellij.psi.PsiElement in the file"
}

[2026-01-06 22:50] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Missing import for PsiElement after signature change",
    "ROOT CAUSE": "Refactor introduced parameter type PsiElement without adding its import, causing cascading type inference errors.",
    "PROJECT NOTE": "When introducing IntelliJ PSI types in Kotlin files, always add the corresponding com.intellij.psi.* imports to avoid 'Any' inference and unresolved references.",
    "NEW INSTRUCTION": "WHEN adding PsiElement to a Kotlin signature THEN import com.intellij.psi.PsiElement in that file"
}

[2026-01-06 22:52] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "search_replace",
    "ERROR": "Refactor introduced unresolved PsiElement and type mismatches",
    "ROOT CAUSE": "The method signature was changed to use PsiElement without adding its import, making element effectively Any and breaking downstream calls.",
    "PROJECT NOTE": "When editing Kotlin signatures in existing files, ensure all newly referenced types are imported (e.g., com.intellij.psi.PsiElement) before relying on them in PsiTreeUtil and Py* APIs.",
    "NEW INSTRUCTION": "WHEN changing a Kotlin signature to new types THEN add required imports immediately"
}

[2026-01-06 23:15] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "get_file_structure",
    "ERROR": "Requested file does not exist",
    "ROOT CAUSE": "Attempted to open non-existent PsiExtensions.kt under util due to wrong path assumption.",
    "PROJECT NOTE": "The util package currently contains only OwnCodeUtil.kt; no PsiExtensions.kt file exists there.",
    "NEW INSTRUCTION": "WHEN get_file_structure target is uncertain THEN list directory or search before opening"
}

[2026-01-06 23:17] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml unresolved language id 'Python'",
    "ROOT CAUSE": "plugin.xml lacks a dependency on the Python plugin, so language=\"Python\" entries cannot be resolved.",
    "PROJECT NOTE": "Add <depends>Pythonid</depends> (or the appropriate Python plugin id used in this repo) to plugin.xml so language=\"Python\" and Python-specific extensions validate.",
    "NEW INSTRUCTION": "WHEN plugin.xml shows unknown language 'Python' THEN add Python plugin dependency to plugin.xml"
}

[2026-01-06 23:19] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml semantic errors: unresolved Python language",
    "ROOT CAUSE": "A Python-specific intention was registered in main plugin.xml instead of the Python-gated optional config, causing unresolved 'Python' language in this SDK.",
    "PROJECT NOTE": "Register Python extensions in python-support.xml behind <depends optional=\"true\" config-file=\"python-support.xml\">com.intellij.modules.python</depends>; keep main plugin.xml free of Python-specific language entries.",
    "NEW INSTRUCTION": "WHEN adding Python-specific extensions THEN register them in python-support.xml under com.intellij.modules.python depends"
}

[2026-01-06 23:20] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml: Cannot resolve language id 'Python'",
    "ROOT CAUSE": "plugin.xml registers Python-language extensions without declaring a dependency on the Python plugin.",
    "PROJECT NOTE": "Add <depends>com.jetbrains.python</depends> (or the appropriate Python module id) in plugin.xml before using language=\"Python\" extensions.",
    "NEW INSTRUCTION": "WHEN editing plugin.xml with language=\"Python\" entries THEN declare depends on com.jetbrains.python"
}

[2026-01-06 23:21] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml: Cannot resolve language id 'Python'",
    "ROOT CAUSE": "Python-specific extensions are registered but the plugin.xml lacks a dependency on com.jetbrains.python, so language='Python' is unresolved.",
    "PROJECT NOTE": "Ensure plugin.xml declares <depends>com.jetbrains.python</depends> (non-optional) so all Python language extensions and contributors resolve.",
    "NEW INSTRUCTION": "WHEN plugin.xml shows unresolved language 'Python' THEN add depends on com.jetbrains.python in plugin.xml"
}

[2026-01-06 23:21] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml: Cannot resolve language id 'Python'",
    "ROOT CAUSE": "plugin.xml lacks a dependency on the Python plugin, so 'Python' language IDs are unresolved.",
    "PROJECT NOTE": "Add <depends>com.jetbrains.python</depends> (or optional with config-file) in plugin.xml before registering Python-language extensions.",
    "NEW INSTRUCTION": "WHEN plugin.xml registers Python language extensions THEN add <depends>com.jetbrains.python</depends>"
}

[2026-01-06 23:23] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "search_replace",
    "ERROR": "plugin.xml: Cannot resolve language id 'Python'",
    "ROOT CAUSE": "plugin.xml lacks a dependency on the Python plugin/module, so language=\"Python\" extensions cannot be resolved.",
    "PROJECT NOTE": "Add <depends>com.intellij.modules.python</depends> (or the appropriate Python plugin id) in plugin.xml to use language=\"Python\" extension points.",
    "NEW INSTRUCTION": "WHEN registering extensions with language=\"Python\" in plugin.xml THEN add depends com.intellij.modules.python"
}

[2026-01-07 07:39] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestStacktraceParser",
    "ERROR": "Failed to extract line number from pytest stacktrace",
    "ROOT CAUSE": "Filename was derived from the innermost class name instead of the module file.",
    "PROJECT NOTE": "For location URLs like python<root>://tests.test_.ClassA.ClassB.test, use test_.py as the filename when matching 'file:(\\d+):' patterns.",
    "NEW INSTRUCTION": "WHEN locationUrl includes classes after module THEN use module name to form '<module>.py'"
}

[2026-01-07 07:40] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestStacktraceParser",
    "ERROR": "Wrong filename derived from locationUrl; line number not found",
    "ROOT CAUSE": "extractFileNameFromLocationUrl selects the last class segment 'TestGrandChild' as filename instead of the module 'test_.py', so subsequent 'filename:(\\d+):' searches fail.",
    "PROJECT NOTE": "In PytestStacktraceParser.extractFileNameFromLocationUrl, map the qualified name to its module part (package.module[.classes][.function]) and build 'module.py'; ignore class/function segments and parameter ids.",
    "NEW INSTRUCTION": "WHEN locationUrl contains package.module.class.function THEN build filename from module only"
}

[2026-01-07 07:42] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestStacktraceParser.parseFailedLine",
    "ERROR": "Could not extract line number from stacktrace",
    "ROOT CAUSE": "The parser searches for 'filename:(\\d+):' near the '>' snippet, but pytest reports the line number on the preceding traceback frame line instead.",
    "PROJECT NOTE": "Pytest frames include '<path>/.../TestGrandChild.py:NN: in <test_name>'; extract NN from that frame rather than the assertion snippet.",
    "NEW INSTRUCTION": "WHEN marker found but no line number THEN scan traceback for '<filename>:(\\d+):' and use it"
}

[2026-01-07 07:42] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestStacktraceParser",
    "ERROR": "Wrong filename extracted from location URL",
    "ROOT CAUSE": "The parser picked the last class name 'TestGrandChild' as file name instead of the module 'test_.py', so no 'file.py:line:' match was found and line stayed -1.",
    "PROJECT NOTE": "In extractFileNameFromLocationUrl, treat parts as [pkg... , module, class..., function]; map the module segment to '<module>.py' and ignore subsequent class segments when deriving the filename.",
    "NEW INSTRUCTION": "WHEN locationUrl splits into package, module, classes, function THEN derive filename from module segment only"
}

[2026-01-07 07:54] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "-",
    "ERROR": "Failed to extract pytest failed line number",
    "ROOT CAUSE": "Parser only searches for 'filename:line:' patterns and ignores code-only '>' marker excerpts.",
    "PROJECT NOTE": "Enhance PytestStacktraceParser to, when no 'file.py:line:' match is found, read the code line after the '>' marker (e.g., '>       assert 1 == 2'), normalize whitespace, and search the resolved test file to compute the 1-based line number.",
    "NEW INSTRUCTION": "WHEN pytest stacktrace has only '>' marker THEN locate marker code line in file to get line"
}

[2026-01-07 08:31] - Updated by Junie - Error analysis
{
    "TYPE": "invalid api",
    "TOOL": "create",
    "ERROR": "Wrong TestFailedLineManager API methods implemented",
    "ROOT CAUSE": "The implementation used a non-existent getTestFailureInfo and missed required SDK methods, indicating a mismatch with the current TestFailedLineManager API.",
    "PROJECT NOTE": "Register as a project service in plugin.xml: <projectService serviceInterface=\"com.intellij.testIntegration.TestFailedLineManager\" serviceImplementation=\"...pytest.outcome.PyTestFailedLineManager\"/>; implement getTestInfo, getRunQuickFix, getDebugQuickFix per SDK.",
    "NEW INSTRUCTION": "WHEN implementing TestFailedLineManager THEN implement getTestInfo/getRunQuickFix/getDebugQuickFix per SDK"
}

[2026-01-07 08:31] - Updated by Junie - Error analysis
{
    "TYPE": "semantic",
    "TOOL": "create",
    "ERROR": "Wrong TestFailedLineManager API implemented",
    "ROOT CAUSE": "Used nonexistent getTestFailureInfo/TestFailureInfo and omitted required interface methods and service registration.",
    "PROJECT NOTE": "Use TestFailedLineManager#getTestInfo, getRunQuickFix, getDebugQuickFix; reference LocalQuickFix; register as project-level service in plugin.xml with serviceInterface=\"com.intellij.testIntegration.TestFailedLineManager\" and serviceImplementation set to your class.",
    "NEW INSTRUCTION": "WHEN implementing TestFailedLineManager THEN implement required methods and register service in plugin.xml"
}

[2026-01-07 08:32] - Updated by Junie - Error analysis
{
    "TYPE": "invalid api",
    "TOOL": "create",
    "ERROR": "Implemented wrong TestFailedLineManager methods; missing service registration",
    "ROOT CAUSE": "The implementation targeted a non-existent getTestFailureInfo API and omitted required TestFailedLineManager methods and plugin.xml service registration.",
    "PROJECT NOTE": "Implement com.intellij.testIntegration.TestFailedLineManager with getTestInfo(PsiElement), getRunQuickFix(PsiElement), getDebugQuickFix(PsiElement,String); then register a projectService in plugin.xml: <projectService serviceInterface=\"com.intellij.testIntegration.TestFailedLineManager\" serviceImplementation=\"...PyTestFailedLineManager\"/>.",
    "NEW INSTRUCTION": "WHEN semantic checker reports unimplemented TestFailedLineManager members THEN implement getTestInfo and quick fixes; register service in plugin.xml"
}

[2026-01-07 08:33] - Updated by Junie - Error analysis
{
    "TYPE": "invalid api",
    "TOOL": "create",
    "ERROR": "Wrong API methods and missing service registration",
    "ROOT CAUSE": "Implemented non-existent getTestFailureInfo and omitted required TestFailedLineManager methods and service registration.",
    "PROJECT NOTE": "Register in plugin.xml: <projectService serviceInterface=\"com.intellij.testIntegration.TestFailedLineManager\" serviceImplementation=\"...PyTestFailedLineManager\"/>; implement getTestInfo, getRunQuickFix, getDebugQuickFix and use TestFailedLineManager.TestInfo.",
    "NEW INSTRUCTION": "WHEN implementing TestFailedLineManager THEN implement required methods and register project service in plugin.xml"
}

[2026-01-07 09:13] - Updated by Junie - Error analysis
{
    "TYPE": "logic",
    "TOOL": "PytestLocationUrlFactory",
    "ERROR": "Location URL mismatch for nested classes",
    "ROOT CAUSE": "URLs built from PSI omit or inconsistently include the full nested class chain, so they don't match the SMTestProxy key stored in TestOutcomeDiffService.",
    "PROJECT NOTE": "Ensure PytestLocationUrlFactory.buildQualifiedName returns module.Test1.Test2.func for nested classes and also provides alternative module.Test2.func when PyTest flattens class hierarchies; pass all variants to TestOutcomeDiffService.findWithKeys.",
    "NEW INSTRUCTION": "WHEN function belongs to nested classes THEN include full nested class chain in locationUrl"
}