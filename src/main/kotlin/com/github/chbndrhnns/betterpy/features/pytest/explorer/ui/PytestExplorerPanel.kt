package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.CollectionListener
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.PytestExplorerService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class PytestExplorerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val LOG = Logger.getInstance(PytestExplorerPanel::class.java)

    private val testTree = Tree()
    private val fixtureDetailPanel = FixtureDetailPanel()
    private val service = PytestExplorerService.getInstance(project)
    private val statusLabel = JLabel("Ready")

    private val collectionListener = CollectionListener { snapshot ->
        SwingUtilities.invokeLater { updateTree(snapshot) }
    }

    init {
        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)

        val splitPane = JBSplitter(false, 0.5f).apply {
            firstComponent = JBScrollPane(testTree)
            secondComponent = fixtureDetailPanel
        }
        add(splitPane, BorderLayout.CENTER)

        statusLabel.border = JBUI.Borders.empty(2, 5)
        add(statusLabel, BorderLayout.SOUTH)

        setupTree()

        service.addListener(collectionListener)
        service.getSnapshot()?.let { updateTree(it) }
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(ExpandAllAction())
            add(CollapseAllAction())
        }
        return ActionManager.getInstance()
            .createActionToolbar("PytestExplorer", group, true)
    }

    private fun setupTree() {
        testTree.isRootVisible = false
        testTree.cellRenderer = PytestTreeCellRenderer()

        testTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    when (val userObj = node.userObject) {
                        is TestTreeNode -> navigateToTest(userObj)
                    }
                }
            }
        })

        testTree.addTreeSelectionListener {
            val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val snapshot = service.getSnapshot() ?: return@addTreeSelectionListener
            when (val userObj = node.userObject) {
                is TestTreeNode -> fixtureDetailPanel.showFixturesFor(userObj.test, snapshot)
                else -> fixtureDetailPanel.clear()
            }
        }
    }

    private fun updateTree(snapshot: CollectionSnapshot) {
        LOG.debug("Updating tree: ${snapshot.tests.size} tests, ${snapshot.fixtures.size} fixtures")
        val root = PytestExplorerTreeBuilder.buildTestTree(snapshot)
        testTree.model = DefaultTreeModel(root)
        val testCount = snapshot.tests.size
        val fixtureCount = snapshot.fixtures.size
        statusLabel.text = "$testCount tests, $fixtureCount fixtures collected"
    }

    private fun navigateToTest(node: TestTreeNode) {
        LOG.debug("Navigating to test: ${node.test.nodeId}")
        ReadAction.run<Throwable> {
            val pointer = PytestPsiResolver.resolveTest(project, node.test)
            pointer?.element?.let {
                (it as? com.intellij.pom.Navigatable)?.navigate(true)
            }
        }
    }

    override fun dispose() {
        service.removeListener(collectionListener)
    }

    private inner class RefreshAction :
        AnAction("Refresh", "Re-collect pytest tests and fixtures", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            LOG.info("Manual refresh triggered")
            statusLabel.text = "Collecting..."
            PytestCollectorTask(project).queue()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ExpandAllAction : AnAction("Expand All", null, AllIcons.Actions.Expandall) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.expandAll(testTree)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class CollapseAllAction : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.collapseAll(testTree, 1)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}
