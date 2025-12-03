[2025-12-01 21:29] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "run_test",
    "ERROR": "Build failed; error output truncated",
    "ROOT CAUSE": "The test/build failed but the error message was truncated, hiding the actual failure.",
    "PROJECT NOTE": "Re-run Gradle with --stacktrace or --info to get full compilation/test errors.",
    "NEW INSTRUCTION": "WHEN build failure output is truncated THEN rerun tests with verbose logging to capture details"
}

[2025-12-01 21:43] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "Gradle",
    "ERROR": "Immutable Gradle workspace modified; tests/build cannot proceed",
    "ROOT CAUSE": "The IntelliJ Platform Gradle Plugin immutable workspace became corrupted/modified, triggering IllegalStateException.",
    "PROJECT NOTE": "build.gradle.kts sets useInstaller = false for intellijPlatform.create, which uses an immutable workspace; toggling to true during tests can avoid this.",
    "NEW INSTRUCTION": "WHEN error message contains \"immutable workspace\" THEN remove IntelliJ Platform Gradle Plugin workspace directory and rerun with --no-configuration-cache"
}

[2025-12-01 22:56] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "search_replace",
    "ERROR": "Replacement introduced Kotlin syntax and semantic errors",
    "ROOT CAUSE": "Incorrect Kotlin string literal quoting for multi-line content caused parse errors.",
    "PROJECT NOTE": "myFixture.addFileToProject returns a PsiFile; do not cast VirtualFile to PyFile.",
    "NEW INSTRUCTION": "WHEN replacing code with multi-line Kotlin strings THEN use triple quotes and trimIndent"
}

[2025-12-01 23:04] - Updated by Junie - Error analysis
{
    "TYPE": "path error",
    "TOOL": "run_test",
    "ERROR": "Test data file not found at expected path",
    "ROOT CAUSE": "Test data files were created outside the project root, so the test runner could not locate them under src/test/testData.",
    "PROJECT NOTE": "Inspection tests load files from src/test/testData relative to the repository root; ensure created files live under that path within the project directory.",
    "NEW INSTRUCTION": "WHEN test reports 'Cannot find source file' under testData THEN create or move files under project src/test/testData path"
}

[2025-12-02 22:05] - Updated by Junie - Error analysis
{
    "TYPE": "env/setup",
    "TOOL": "run_test",
    "ERROR": "Missing j.u.l LogManager test-log.properties",
    "ROOT CAUSE": "IntelliJ test runtime expects a logging properties file in Gradle cache that is absent.",
    "PROJECT NOTE": "This IJ Platform EAP warning often appears but tests still run; rely on final summary, not the initial log warning.",
    "NEW INSTRUCTION": "WHEN run_test logs missing test-log.properties THEN rerun tests and rely on final summary"
}

[2025-12-02 22:25] - Updated by Junie - Error analysis
{
    "TYPE": "missing context",
    "TOOL": "run_test",
    "ERROR": "Intention not available at caret position",
    "ROOT CAUSE": "The caret was on the assignment variable name, where the intention is not offered.",
    "PROJECT NOTE": "This intention typically appears when the caret is on the RHS type usage (e.g., the constructor call) rather than the LHS variable identifier.",
    "NEW INSTRUCTION": "WHEN intention lookup returns not in available intentions THEN place caret on RHS type usage"
}

[2025-12-02 22:44] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "Gradle (:compileKotlin)",
    "ERROR": "Unresolved reference: TypeEvalContext; missing required args",
    "ROOT CAUSE": "WrapWithExpectedTypeIntention.kt uses TypeEvalContext without import and omits new context parameters in calls.",
    "PROJECT NOTE": "TypeEvalContext is in com.jetbrains.python.psi.types; ensure it is imported and a TypeEvalContext instance is created/passed to APIs whose signatures require it before running tests.",
    "NEW INSTRUCTION": "WHEN compileKotlin reports 'Unresolved reference' in Kotlin THEN add missing imports and update changed API parameters"
}

[2025-12-02 22:44] - Updated by Junie - Error analysis
{
    "TYPE": "build failure",
    "TOOL": "Gradle (:compileKotlin)",
    "ERROR": "Conflicting declarations: duplicate local 'context' variables",
    "ROOT CAUSE": "Automated text replacement introduced multiple TypeEvalContext declarations in the same scope.",
    "PROJECT NOTE": "In WrapWithExpectedTypeIntention.kt, declare a single TypeEvalContext per method and pass it to analyzer.analyzeAtCaret; remove duplicate 'context' vals added by bulk edits.",
    "NEW INSTRUCTION": "WHEN compileKotlin reports 'Conflicting declarations' for a variable THEN deduplicate variable declarations within scope"
}

[2025-12-02 22:47] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "bash",
    "ERROR": "zsh here-doc expansion caused 'number expected'",
    "ROOT CAUSE": "The heredoc was unquoted, so zsh expanded $/{} in Kotlin content and failed.",
    "PROJECT NOTE": "When writing Kotlin files via shell heredocs, use a single-quoted delimiter (<<'EOF') to avoid $ interpolation inside string templates.",
    "NEW INSTRUCTION": "WHEN writing file via heredoc containing $ or ${} THEN use <<'EOF' to disable expansion"
}

[2025-12-02 23:05] - Updated by Junie - Error analysis
{
    "TYPE": "invalid args",
    "TOOL": "create",
    "ERROR": "Unresolved references: LightJavaCodeInsightFixtureTestCase, myFixture, module",
    "ROOT CAUSE": "The new test extended the wrong base class and used nonexistent fields for this project.",
    "PROJECT NOTE": "In this repo, test classes must extend fixtures.TestBase and use myFixture.module when adding libraries.",
    "NEW INSTRUCTION": "WHEN creating new test classes THEN extend fixtures.TestBase and use myFixture.module"
}

[2025-12-03 12:49] - Updated by Junie - Error analysis
{
    "TYPE": "invalid context",
    "TOOL": "PopulateArgumentsService.populateArguments",
    "ERROR": "PSI modified outside command/write action via popup callback",
    "ROOT CAUSE": "The popup selection handler performs PSI writes without wrapping them in a WriteCommandAction/command.",
    "PROJECT NOTE": "In the unified PopulateArgumentsIntention using JbPopupHost, wrap argument insertion in WriteCommandAction.runWriteCommandAction(project) inside the popup callback; startInWriteAction on the Intention doesn't cover async callbacks.",
    "NEW INSTRUCTION": "WHEN popup callback performs PSI modifications THEN wrap code in WriteCommandAction.runWriteCommandAction(project)"
}

