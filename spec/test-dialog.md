### Testing Features with Dialogs and User Interaction

IntelliJ Platform provides two main approaches for testing features that display dialogs and require user interaction. Both approaches allow tests to run without showing actual UI dialogs, which would be impossible in automated test environments.

---

### Approach 1: Using `TestDialogManager.setTestDialog()` for Simple Message Dialogs

This approach is used for simple message dialogs (like confirmation dialogs) that use `Messages.showYesNoDialog()` or similar methods.

#### How It Works:
- Register a `TestDialog` implementation before invoking the code that shows the dialog
- The test dialog intercepts the dialog call and returns a simulated user response
- Clean up by restoring the original dialog handler

#### Example from Python Tests:

```java
// From PyChangeSignatureTest.java
final TestDialog oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);
try {
  newFunction = PyChangeSignatureHandler.getSuperMethod(function);
}
finally {
  TestDialogManager.setTestDialog(oldTestDialog);
}
```

#### Built-in TestDialog Constants:
- `TestDialog.OK` - Always returns OK
- `TestDialog.YES` - Always returns YES
- `TestDialog.NO` - Always returns NO
- `TestDialog.DEFAULT` - Throws an exception (useful for detecting unexpected dialogs)

#### Custom TestDialog Implementation:

```java
// From PyIntentionTest.java
TestDialogManager.setTestInputDialog(new TestInputDialog() {
  @Override
  public String show(String message) {
    return "mc"; // Return custom input
  }
});
```

---

### Approach 2: Using `TestDialogHandler` for Complex DialogWrapper Instances

This approach is used for complex dialogs that extend `DialogWrapper` and have multiple fields, buttons, and custom logic.

#### How It Works:
- Create a `TestDialogHandler` that receives the dialog instance
- Modify dialog fields programmatically (set values, check states, etc.)
- Return the desired exit code (OK, CANCEL, etc.)
- The dialog is never actually shown on screen

#### Example from Git4Idea Tests:

```kotlin
// From GitHttpGuiAuthenticatorTest.kt
private fun registerDialogHandler(exitOk: Boolean, rememberPassword: Boolean = true) {
  dialogManager.registerDialogHandler(GitHttpLoginDialog::class.java, TestDialogHandler {
    dialogShown = true
    
    // Modify dialog fields
    it.username = TEST_LOGIN
    it.password = TEST_PASSWORD
    it.rememberPassword = rememberPassword
    
    // Return exit code simulating user action
    if (exitOk) DialogWrapper.OK_EXIT_CODE else DialogWrapper.CANCEL_EXIT_CODE
  })
}
```

#### Key Components:

**TestDialogHandler Interface:**
```java
public interface TestDialogHandler<T extends DialogWrapper> {
  /**
   * Do something with the dialog (modify its instance fields, for example)
   * and return the exit code - as if user pressed one of exit buttons.
   */
  int handleDialog(T dialog);
}
```

**TestDialogManager (Git4Idea version):**
```kotlin
class TestDialogManager : DialogManager() {
  private val myHandlers = hashMapOf<Class<out DialogWrapper>, (DialogWrapper) -> Int>()
  
  override fun showDialog(dialog: DialogWrapper) {
    var exitCode = DialogWrapper.OK_EXIT_CODE
    try {
      val handler = myHandlers[dialog.javaClass]
      if (handler != null) {
        exitCode = handler(dialog)
      }
      else {
        throw IllegalStateException("The dialog is not expected here: " + dialog.javaClass)
      }
    }
    finally {
      dialog.close(exitCode, exitCode == DialogWrapper.OK_EXIT_CODE)
    }
  }
  
  fun <T : DialogWrapper> onDialog(dialogClass: Class<T>, handler: (T) -> Int) {
    myHandlers.put(dialogClass, handler as (DialogWrapper) -> Int)
  }
}
```

---

### Complete Test Example

Here's how you would test a rename handler like `PyMagicLiteralRenameHandler`:

```java
public class PyMagicLiteralRenameTest extends PyTestCase {
  
  public void testRenameWithDialog() {
    // Setup: Configure test file
    myFixture.configureByText("test.py", "magic_string = 'some_<caret>value'");
    
    // Register dialog handler to simulate user clicking OK
    TestDialog oldDialog = TestDialogManager.setTestDialog(TestDialog.OK);
    try {
      // Invoke rename - this would normally show a dialog
      myFixture.renameElementAtCaret("new_value");
      
      // Verify the result
      myFixture.checkResult("magic_string = 'new_value'");
    }
    finally {
      // Cleanup: Restore original dialog handler
      TestDialogManager.setTestDialog(oldDialog);
    }
  }
  
  public void testRenameCancelled() {
    myFixture.configureByText("test.py", "magic_string = 'some_<caret>value'");
    
    // Simulate user clicking Cancel
    TestDialog oldDialog = TestDialogManager.setTestDialog(message -> DialogWrapper.CANCEL_EXIT_CODE);
    try {
      myFixture.renameElementAtCaret("new_value");
      
      // Verify nothing changed
      myFixture.checkResult("magic_string = 'some_value'");
    }
    finally {
      TestDialogManager.setTestDialog(oldDialog);
    }
  }
}
```

---

### Best Practices

1. **Always clean up**: Use try-finally blocks to restore the original dialog handler
2. **Use Disposable for automatic cleanup**: 
   ```java
   TestDialogManager.setTestDialog(TestDialog.OK, getTestRootDisposable());
   ```
3. **Fail on unexpected dialogs**: The default handlers throw exceptions, helping catch unexpected dialog invocations
4. **Test both paths**: Test both OK and Cancel scenarios to ensure proper handling
5. **Verify dialog was shown**: Use flags to verify the dialog was actually invoked when expected

---

### Summary

The IntelliJ Platform uses **test dialog interception** rather than UI automation:
- **Simple dialogs**: Use `TestDialogManager.setTestDialog()` with predefined or custom `TestDialog` implementations
- **Complex dialogs**: Use `TestDialogHandler` to programmatically interact with `DialogWrapper` instances
- **No actual UI**: Dialogs are never rendered, making tests fast and reliable
- **Full control**: Tests can simulate any user interaction by setting dialog fields and returning appropriate exit codes