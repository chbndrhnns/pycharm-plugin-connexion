### Plan

To support copying stack traces for failed tests, we will extend the previous plan. Since we now have three similar actions (Copy Node ID, Copy FQN, Copy Stacktrace) that all require traversing the selected nodes in the `TestTreeView`, it is best to refactor the common traversal logic into an abstract base class.

#### 1. Refactor: Abstract Base Action
Create an abstract class `AbstractCopyTestNodeAction` that handles:
1.  **Context Resolution**: Getting the `TestTreeView` from `AnActionEvent`.
2.  **Selection Retrieval**: Getting `TreePath`s from the view.
3.  **Traversal**: Recursively visiting `DefaultMutableTreeNode`s (respecting the current view filter).
4.  **Collection**: Calling an abstract method `getValuableText(SMTestProxy)` for each leaf node and collecting non-null results.
5.  **Clipboard Operation**: Joining results (with a configurable separator) and setting the clipboard content.

#### 2. Implement Specific Actions
1.  **`CopyPytestNodeIdAction`** (Existing): Implements `getValuableText` to return the Pytest Node ID.
2.  **`CopyFQNAction`** (Existing): Implements `getValuableText` to return the Python FQN.
3.  **`CopyStacktraceAction`** (New):
    - Implements `getValuableText` to return `proxy.getStacktrace()`.
    - **Condition**: Only returns text if `proxy.isDefect()` (failed/error) is true and stack trace is not null.
    - **Separator**: Uses `\n\n` instead of `\n` to clearly separate multiple stack traces.

#### 3. Update Registration
Register the new action in `plugin.xml` under the "Copy Special" group.

### Implementation Steps

#### Step 1: Abstract Base Class
```java
public abstract class AbstractCopyTestNodeAction extends AnAction {
    private final String separator;

    protected AbstractCopyTestNodeAction(String text, String separator) {
        super(text);
        this.separator = separator;
    }

    @Nullable
    protected abstract String getValuableText(@NotNull SMTestProxy proxy);

    @Override
    public void update(@NotNull AnActionEvent e) {
        TestTreeView view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) instanceof TestTreeView ? 
                            (TestTreeView) e.getData(PlatformDataKeys.CONTEXT_COMPONENT) : null;
        // Check if Python configuration if necessary, or generic
        e.getPresentation().setEnabledAndVisible(view != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        TestTreeView view = (TestTreeView) e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        if (view == null) return;

        List<String> result = new ArrayList<>();
        TreePath[] selectionPaths = view.getSelectionPaths();
        
        if (selectionPaths != null) {
            for (TreePath path : selectionPaths) {
                Object component = path.getLastPathComponent();
                if (component instanceof DefaultMutableTreeNode) {
                    collect((DefaultMutableTreeNode) component, result);
                }
            }
        }
        
        if (!result.isEmpty()) {
            CopyPasteManager.getInstance().setContents(new StringSelection(String.join(separator, result)));
        }
    }

    private void collect(DefaultMutableTreeNode node, List<String> result) {
        SMTestProxy proxy = SMTRunnerTestTreeView.getTestProxyFor(node);
        if (proxy != null && proxy.isLeaf()) {
            String text = getValuableText(proxy);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collect((DefaultMutableTreeNode) node.getChildAt(i), result);
        }
    }
}
```

#### Step 2: Implement CopyStacktraceAction
```java
public class CopyStacktraceAction extends AbstractCopyTestNodeAction {
    public CopyStacktraceAction() {
        super("Copy Stacktrace", "\n\n"); // Use double newline for separation
    }

    @Override
    protected String getValuableText(@NotNull SMTestProxy proxy) {
        // Only return stacktrace if the test is a defect (Failed or Error)
        if (proxy.isDefect()) {
            return proxy.getStacktrace();
        }
        return null;
    }
}
```

#### Step 3: `plugin.xml`
```xml
<group id="MyPlugin.CopySpecialGroup" text="Copy Special" popup="true">
    <add-to-group group-id="TestTreePopupMenu" anchor="last"/>
    <action id="MyPlugin.CopyPytestNodeIds" class="com.myplugin.CopyPytestNodeIdAction"/>
    <action id="MyPlugin.CopyFQNs" class="com.myplugin.CopyFQNAction"/>
    <action id="MyPlugin.CopyStacktrace" class="com.myplugin.CopyStacktraceAction"/>
</group>
```

### Test Cases

#### Scenario 1: Copy Stacktrace for Single Failed Test
*   **Setup**: Run tests. `test_fail` fails with a `AssertionError`.
*   **Action**: Select `test_fail`. Right-click -> Copy Special -> Copy Stacktrace.
*   **Expected**: Clipboard contains the full stack trace starting with the error message and traceback.

#### Scenario 2: Ignore Passed Tests
*   **Setup**: `test_pass` (Passed) and `test_fail` (Failed).
*   **Action**: Select both nodes (or their parent suite). Copy Stacktrace.
*   **Expected**: Clipboard contains **only** the stack trace for `test_fail`. `test_pass` is ignored.

#### Scenario 3: Multiple Failures
*   **Setup**: `test_fail_1` and `test_fail_2` both failed.
*   **Action**: Select the parent suite. Copy Stacktrace.
*   **Expected**: Clipboard contains stack trace for `test_fail_1`, followed by two newlines, followed by stack trace for `test_fail_2`.

#### Scenario 4: Copy on Passed Test (No-op)
*   **Setup**: Select `test_pass`.
*   **Action**: Copy Stacktrace.
*   **Expected**: Clipboard is not updated (or cleared), as there is no stack trace to copy. (Alternatively, user notification "No stack traces found").

#### Scenario 5: Filtered View
*   **Setup**: Enable "Show Passed" filter (so failed are hidden - unlikely but possible, or vice versa).
*   **Action**: If user hides failed tests and selects Root -> Copy Stacktrace.
*   **Expected**: Since failed nodes are not in the tree model (due to filter), nothing is copied.
*   **Action**: Enable "Show Failed" filter. Select Root -> Copy Stacktrace.
*   **Expected**: Copies stack traces for all visible failed tests.