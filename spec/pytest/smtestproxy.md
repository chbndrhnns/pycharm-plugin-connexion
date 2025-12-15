Accessing SMTestProxy from AnActionEvent
When implementing an AnAction for a right-click context menu on a test tree node in PyCharm's Run tool window Tests tab,
start from the
AnActionEvent in actionPerformed(AnActionEvent e) or update(AnActionEvent e).

The test tree is a JTree powered by SMTestProxy nodes in SMTRunnerConsoleView. The standard approach uses the context
component:

Step-by-Step Code
import com.intellij.execution.testframework.sm.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import javax.swing.JTree
import javax.swing.tree.TreePath
override fun actionPerformed(e: AnActionEvent) {
val dataContext = e.dataContext
val tree: JTree? = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) as? JTree
val proxy = getSelectedProxy(tree) // Helper below
if (proxy != null && proxy.isLeaf) {
// Extract parameters as per previous solution
val paramRegex = Regex("\\[(.+)]$")
val params = paramRegex.find(proxy.name)?.groupValues?.getOrNull(1) ?: ""
// Use params, proxy.name, proxy.locationUrl, etc.
}
}
private fun getSelectedProxy(tree: JTree?): SMTestProxy? {
if (tree == null) return null
val selectionPath = tree.selectionPath ?: return null
val node = selectionPath.lastPathComponent
return if (node is SMTestProxy) node else null
}

Key Points
• CONTEXT_COMPONENT: Provides the JTree test tree view when right-clicked on a node.
• TreePath.lastPathComponent: Directly yields the SMTestProxy leaf node for parametrized/single tests.
• Validation: Check proxy.isLeaf for individual test invocations parametrized leaves.
• Multi-selection: Use tree.selectionPaths for multiple SMTestProxy nodes.
• Update Method: Mirror in update(AnActionEvent e) to enable/disable based on selected proxy e.g.,
e.presentation.isEnabled = proxy !=
null && proxy.isLeaf.

Examples in Codebase
• PyRerunFailedTestsAction.kt python/src/com/jetbrains/python/testing/PyRerunFailedTestsAction.kt: Subclasses
AbstractRerunFailedTestsAction, which internally accesses proxies via the test model/console properties. It collects
getFailedTests(project) from model.failedTests.
• Platform actions e.g., "Run/Debug Test" use similar tree selection logic via data providers in SMTRunnerConsoleView.
• PyTest.kt python/src/com/jetbrains/python/testing/PyTest.kt: Defines PARAM_REGEX for parameter extraction from
proxy.name.

Alternatives
• Test Model: SMRunnerTestTreeView data provider: e.getData(TestTreeView.DATA_KEY)?.selectedTestProxies if registered.
• Console View: Traverse parents via RunContentManager to SMTRunnerConsoleView, then getTestTreeView().
• Metainfo: proxy.metainfo or proxy.locationUrl for full test ID/params.

This mirrors PyCharm's rerun and run actions. Test in EDT ApplicationManager.getApplication().invokeLater. For
structured params, parse
the extracted string further e.g., split by , for tuples.