### Plan for Implementing "Copy with Dependencies" Intention

To achieve the goal of copying a method or block along with its local dependencies (helper functions, global variables) and imports, we need to perform a static analysis of the selected code to identify references, resolve them, and collect the necessary definitions.

#### 1. Analysis Strategy
The core logic involves a recursive dependency search starting from the user's selection:

1.  **Identify Initial Scope**: Determine the `PsiElement` to copy (e.g., the `PyFunction` under the caret or the selected statements).
2.  **Collect Dependencies**:
    *   Maintain a queue of elements to process (`Queue`) and a set of visited elements (`Visited`).
    *   Scan the current element for `PyReferenceExpression`s.
    *   **Resolve** each reference:
        *   **Local Definitions**: If it resolves to a top-level definition (Function, Class, Constant) in the *same file*, add it to the `Queue` (if not already visited).
        *   **Imports**: If it resolves to an external symbol, identify the `PyImportStatement` or `PyFromImportStatement` in the current file that provides this symbol.
3.  **Format Output**:
    *   Gather all collected import statements.
    *   Gather all collected local definitions (sorting them by their original position in the file to maintain readability).
    *   Combine them into a single string.
4.  **Action**: Copy the result to the clipboard.

#### 2. Implementation Details

**Class Structure:**
*   **`CopyBlockWithDependenciesIntention`**: Extends `PyBaseIntentionAction` (or `PsiElementBaseIntentionAction` in Kotlin).
    *   `isAvailable()`: Checks if the caret is inside a function or valid block in a Python file.
    *   `invoke()`: Orchestrates the analysis and copying.

**Key Helpers:**
*   **`PyRecursiveElementVisitor`**: To traverse the AST of the collected elements and find references.
*   **`ScopeUtil` / `PsiTreeUtil`**: To find the top-level container (function/class) of a resolved local element.
*   **`PyResolveUtil`**: For resolving references.

**Handling Imports:**
*   Pre-calculate a map of `Imported Name -> Import Statement` for the file to quickly identify which import is needed for a given name usage.

#### 3. Test Plan

We will use `PyIntentionTestCase` to verify the feature. Since standard intention tests usually check for file modifications, we need a custom helper to verify the Clipboard content.

### Proposed Test Implementation

Create a file `python/testSrc/com/jetbrains/python/intentions/CopyBlockWithDependenciesIntentionTest.kt`:

```kotlin
package com.jetbrains.python.intentions

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor
import com.jetbrains.python.fixtures.PyTestCase

class CopyBlockWithDependenciesIntentionTest : PyTestCase() {

  fun testSimpleFunction() {
    doCopyTest(
      """
      def <caret>foo():
          print("Hello")
      """,
      """
      def foo():
          print("Hello")
      """
    )
  }

  fun testFunctionWithImport() {
    doCopyTest(
      """
      import os
      
      def <caret>foo():
          print(os.getcwd())
      """,
      """
      import os
      
      def foo():
          print(os.getcwd())
      """
    )
  }

  fun testFunctionWithHelper() {
    doCopyTest(
      """
      def helper():
          return "Help"
          
      def <caret>foo():
          print(helper())
      """,
      """
      def helper():
          return "Help"
          
      def foo():
          print(helper())
      """
    )
  }

  fun testTransitiveDependency() {
    doCopyTest(
      """
      import math
      
      def sub_helper():
          return math.pi
          
      def helper():
          return sub_helper()
          
      def <caret>foo():
          print(helper())
      """,
      """
      import math

      def sub_helper():
          return math.pi
          
      def helper():
          return sub_helper()
          
      def foo():
          print(helper())
      """
    )
  }
  
  fun testIgnoresUnusedImportsAndHelpers() {
    doCopyTest(
      """
      import sys
      import os
      
      def unused_helper():
          pass
          
      def <caret>foo():
          print(os.name)
      """,
      """
      import os
      
      def foo():
          print(os.name)
      """
    )
  }

  private fun doCopyTest(fileText: String, expectedClipboard: String) {
    myFixture.configureByText("a.py", fileText.trimIndent())
    
    // Simulate user invoking the intention
    val intentionName = "Copy method with dependencies" // Adjust to match actual name
    val intention = myFixture.availableIntentions.find { it.text == intentionName }
        ?: error("Intention '$intentionName' not found")
        
    myFixture.launchAction(intention)
    
    // Verify clipboard content
    val content = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
    assertNotNull("Clipboard should not be empty", content)
    
    // Normalize newlines for comparison
    assertEquals(expectedClipboard.trimIndent().trim(), content!!.trim())
  }
}
```

### Integration Steps

1.  **Register Intention**: Add the intention to `plugin.xml` (or `python-plugin-common.xml`):
    ```xml
    <extensions defaultExtensionNs="com.intellij">
      <intentionAction>
        <className>com.jetbrains.python.codeInsight.intentions.CopyBlockWithDependenciesIntention</className>
        <category>Python</category>
      </intentionAction>
    </extensions>
    ```

2.  **Dependencies**: Ensure the plugin has access to `com.intellij.openapi.ide.CopyPasteManager` and Python PSI classes (`PyFunction`, `PyImportStatement`, etc.).