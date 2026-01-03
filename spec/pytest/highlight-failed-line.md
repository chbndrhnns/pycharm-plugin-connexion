To add the feature where PyCharm highlights the line in the editor where a test failed (similar to how IntelliJ IDEA does for Java/Kotlin), you can implement a PyCharm plugin using the following plan. This plan leverages the existing IntelliJ Platform infrastructure for test failure tracking and code inspections.

### 1. Overview of the Mechanism
IntelliJ IDEA uses `TestFailedLineManager` to track failed tests and `TestFailedLineInspection` to highlight the failing lines in the editor. The highlighting is typically done using the `CodeInsightColors.RUNTIME_ERROR` attributes, which appear as a dotted line or a specific background in the editor.

For PyCharm, you need to:
- Provide an implementation of `TestFailedLineManager` for Python.
- Register a local inspection that identifies failing Python test elements and highlights them.

### 2. Implementation Plan

#### Step 1: Implement `TestFailedLineManager` for Python
You need a service that can map a `PsiElement` (like a Python function or a call expression) to the test failure information stored in `TestStateStorage`.

1.  **Storage Retrieval**: Use `TestStateStorage.getInstance(project).getState(url)` to get the `Record` for a specific test.
2.  **URL Mapping**: Python test URLs in PyCharm usually follow the `python<path_to_folder>://qualified.name` protocol. You'll need to reconstruct this URL from the `PsiElement`.
3.  **Failed Line Identification**: The `TestStateStorage.Record` contains a `failedLine`. You must check if the current `PsiElement` contains this line number.

#### Step 2: Create the `PyTestFailedLineInspection`
Implement a `LocalInspectionTool` that visits Python functions and call expressions.

1.  **Visitor**: Use a `PyElementVisitor`.
2.  **Detection**:
    - For each `PyFunction` (test method) or `PyCallExpression` (assertion), ask your `TestFailedLineManager` if there is failure info.
    - If the `PsiElement` corresponds to the `failedLine` in the test record, mark it.
3.  **Highlighting**:
    - Use `ProblemsHolder.registerProblem`.
    - Set the `ProblemHighlightType` to `GENERIC_ERROR_OR_WARNING`.
    - Apply `CodeInsightColors.RUNTIME_ERROR` text attributes to the `ProblemDescriptor` to get the "dotted line" effect.

#### Step 3: Add Quick Fixes (Optional but Recommended)
To match IntelliJ's behavior, provide quick fixes to:
- **Run Test**: Re-run the specific failing test.
- **Debug Test**: Re-run the test in debug mode (optionally setting a breakpoint at the failing line).

#### Step 4: Register in `plugin.xml`
Register your service and inspection in your plugin's configuration:

```xml
<extensions defaultExtensionNs="com.intellij">
    <projectService 
        serviceInterface="com.intellij.testIntegration.TestFailedLineManager"
        serviceImplementation="com.yourplugin.PyTestFailedLineManagerImpl"/>
        
    <localInspection 
        language="Python" 
        displayName="Test failed line" 
        groupName="Python" 
        enabledByDefault="true" 
        level="WARNING" 
        implementationClass="com.yourplugin.PyTestFailedLineInspection"/>
</extensions>
```

### 3. Technical Considerations
- **Test Locators**: PyCharm uses `SMTestLocator` (specifically `PyTestsLocator`) to map between test URLs and PSI elements. Your implementation should use these locators to ensure consistency.
- **Line Numbers**: Python's `TestStateStorage` records line numbers (1-based). Ensure you correctly map these to the 0-based offsets used by the IntelliJ `Document` API.
- **Performance**: Use `SmartPsiElementPointer` to cache failure locations if you implement a more complex tracking system, similar to how Java does it to handle document changes between test runs.