### Plan

To implement the "Copy Special" context menu with "Copy Pytest Node IDs" and "Copy FQNs" options that respect the current filter, we need to traverse the `TestTreeView`'s underlying Swing model. The tree structure in the test runner is dynamically updated based on the active filters (e.g., "Show Passed", "Show Ignored"). Therefore, traversing the `DefaultMutableTreeNode` hierarchy starting from the selected node(s) will automatically respect the filters (i.e., filtered-out tests are not present in the tree model).

#### 1. Define the Action Group
Create a new `ActionGroup` in `plugin.xml` and attach it to the `TestTreePopupMenu`.

#### 2. Implement the Actions
Create two actions: `CopyPytestNodeIdAction` and `CopyFQNAction`. Both will share logic for traversing the selected nodes and collecting the desired identifiers.

**Common Logic (Traversal):**
1.  Obtain the `TestTreeView` from the `AnActionEvent` (via `PlatformDataKeys.CONTEXT_COMPONENT`).
2.  Get the selected `TreePath`s from the tree.
3.  For each selected path:
    - Get the `DefaultMutableTreeNode` (the last path component).
    - Traverse the node's descendants (breadth-first or depth-first).
    - For each leaf node encountered:
        - Extract the `SMTestProxy` object using `SMTRunnerTestTreeView.getTestProxyFor(node)`.
        - If the proxy represents a test (is a leaf), generate its ID/FQN and add it to a list.
4.  Join the list with newlines and copy to the clipboard.

**Node ID Generation (Pytest):**
1.  Get the `Location` from `SMTestProxy`.
2.  If the location provides a `PsiElement`, determine its relative path and name hierarchy.
    - **File Path**: Use `VfsUtil.getRelativePath` from the project root.
    - **Hierarchy**: Traverse `PsiElement` parents (Classes) to build the `::ClassName::methodName` structure.
    - **Heuristic**: If PSI is unavailable, fallback to constructing the ID from `SMTestProxy` parent names, ensuring the path matches `path/to/file.py::Class::test_method`.
3.  Ensure the format matches `pytest` expectations (e.g., using `::` as separator).

**FQN Generation:**
1.  Use `SMTestProxy.getLocationUrl()`.
2.  Python test URLs typically follow the `python_uttestid://protocol`.
3.  Extract the FQN part (e.g., `pkg.module.Class.method`) from the URL.

#### 3. Update Logic
The actions should only be visible/enabled if:
1.  The context is a `TestTreeView`.
2.  The run configuration associated with the tests is a Python/Pytest configuration.

### Implementation Steps

#### Step 1: `plugin.xml` Registration
```xml
<actions>
    <group id="MyPlugin.CopySpecialGroup" text="Copy Special" popup="true">
        <add-to-group group-id="TestTreePopupMenu" anchor="last"/>
        <action id="MyPlugin.CopyPytestNodeIds" 
                class="com.myplugin.CopyPytestNodeIdAction" 
                text="Copy Pytest Node IDs"/>
        <action id="MyPlugin.CopyFQNs" 
                class="com.myplugin.CopyFQNAction" 
                text="Copy FQNs"/>
    </group>
</actions>
```

#### Step 2: Action Implementation (Pseudocode)

```java
public class CopyPytestNodeIdAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        // Check if we are in a TestTreeView and if it's a Python run
        TestTreeView view = e.getData(PlatformDataKeys.CONTEXT_COMPONENT) instanceof TestTreeView ? 
                            (TestTreeView) e.getData(PlatformDataKeys.CONTEXT_COMPONENT) : null;
        e.getPresentation().setEnabledAndVisible(view != null && isPythonConfiguration(view));
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
                    collectNodeIds((DefaultMutableTreeNode) component, result, view);
                }
            }
        }
        
        CopyPasteManager.getInstance().setContents(new StringSelection(String.join("\n", result)));
    }

    private void collectNodeIds(DefaultMutableTreeNode node, List<String> result, TestTreeView view) {
        SMTestProxy proxy = SMTRunnerTestTreeView.getTestProxyFor(node);
        if (proxy != null && proxy.isLeaf()) {
            String nodeId = generatePytestNodeId(proxy);
            if (nodeId != null) result.add(nodeId);
        }

        // Recursively traverse children (this respects the filter because the tree model is filtered)
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodeIds((DefaultMutableTreeNode) node.getChildAt(i), result, view);
        }
    }

    private String generatePytestNodeId(SMTestProxy proxy) {
        // Logic to reconstruct "path/to/file.py::Class::test"
        // 1. Try to get PsiElement from proxy.getLocation(project, scope)
        // 2. Build path relative to project root + separators
        // 3. Fallback to proxy.getName() hierarchy if PSI is missing
        return PytestNodeIdGenerator.getId(proxy);
    }
}
```

### Test Cases

#### Scenario 1: Copy Single Test ID
*   **Setup**: Run a pytest configuration. All tests passed.
*   **Action**: Select `test_example` in the tree. Right-click -> Copy Special -> Copy Pytest Node IDs.
*   **Expected**: Clipboard contains `tests/test_file.py::TestClass::test_example`.

#### Scenario 2: Copy Suite with Filter (Respecting Filters)
*   **Setup**: Run tests. `test_pass` passed, `test_fail` failed.
*   **Action**: Enable "Show Failed Only" filter (passed tests disappear from tree). Select the root node (or the file node). Right-click -> Copy Special -> Copy Pytest Node IDs.
*   **Expected**: Clipboard contains **only** `tests/test_file.py::TestClass::test_fail`. `test_pass` should NOT be included.

#### Scenario 3: Copy FQNs for Multiple Selection
*   **Setup**: Run tests.
*   **Action**: Select `test_A` and `test_B` using Ctrl/Cmd+Click. Right-click -> Copy Special -> Copy FQNs.
*   **Expected**: Clipboard contains two lines:
    ```
    my.pkg.TestClass.test_A
    my.pkg.TestClass.test_B
    ```

#### Scenario 4: Parameterized Tests
*   **Setup**: Run a parameterized test `test_param[1]`.
*   **Action**: Select the test node `test_param[1]`. Copy Pytest Node IDs.
*   **Expected**: Clipboard contains `tests/test_file.py::TestClass::test_param[1]` (verifying correct parameter formatting).

#### Scenario 5: Non-Python Run
*   **Setup**: Run a Java JUnit test.
*   **Action**: Right-click on the test tree.
*   **Expected**: "Copy Special" group is either not visible or disabled.