To keep your logic clean (without production checks for test mode) while ensuring your tests pass, you should use **UI Interceptors**.

The IntelliJ Platform provides a mechanism to intercept modal dialogs during tests. Specifically, you can use `RenameDialogInterceptor` to simulate the user entering a new name and clicking "Refactor" without actually showing the UI.

### 1. Clean up the Production Code
Remove the `isUnitTestMode` check. This allows the same code path to be exercised in both production and tests.

```kotlin
private fun startInlineRename(project: Project, editor: Editor, inserted: PyClass, pyFile: PyFile) {
    // ✅ Check removed; logic is now clean
    val insertedPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(inserted)

    ApplicationManager.getApplication().invokeLater {
        val element = insertedPtr.element ?: return@invokeLater
        // RenameDialog handles the UI interaction safely on EDT
        val dialog = RenameDialog(project, element, null, editor)
        dialog.show() // This will be intercepted in tests
    }
}
```

### 2. Update the Test Code
In your unit test, register a `RenameDialogInterceptor` before triggering the action. This interceptor will catch the `RenameDialog.show()` call and automatically perform the rename with the name you specify.

```kotlin
import com.intellij.ui.RenameDialogInterceptor
import com.intellij.ui.UiInterceptors
import com.intellij.testFramework.PlatformTestUtil

fun testRenameFunctionality() {
    // Register the interceptor to provide the new name "MyNewName"
    UiInterceptors.register(RenameDialogInterceptor("MyNewName"))

    // Trigger the code that calls startInlineRename
    myFixture.type("class MyNewClass ...") // or however you trigger the creation/rename

    // Since startInlineRename uses invokeLater, ensure the EDT queue is flushed
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Assert that the rename happened
    myFixture.checkResult("...") // Verify the file content matches expectation
}
```

### How it works
*   **`UiInterceptors.register(...)`**: Queues an interceptor that listens for the next UI component to be shown.
*   **`RenameDialogInterceptor`**: Specifically looks for `RenameDialog` instances. When one attempts to show, the interceptor prevents the window from appearing and instead immediately calls `performRename("MyNewName")`.
*   **`dispatchAllEventsInIdeEventQueue()`**: Essential because your code uses `invokeLater`. The test needs to wait for that asynchronous block to execute before verifying the results.