package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.CollectionListener
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.PytestExplorerService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class PytestFixtureExplorerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val LOG = Logger.getInstance(PytestFixtureExplorerPanel::class.java)

    private val fixtureTree = Tree()
    private val service = PytestExplorerService.getInstance(project)
    private val statusLabel = JLabel("Ready")
    private var lastSnapshot: CollectionSnapshot? = null

    private val collectionListener = CollectionListener { snapshot ->
        SwingUtilities.invokeLater { updateTree(snapshot) }
    }

    init {
        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(fixtureTree), BorderLayout.CENTER)

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
            .createActionToolbar("PytestFixtureExplorer", group, true)
    }

    private fun setupTree() {
        fixtureTree.isRootVisible = false
        fixtureTree.cellRenderer = PytestTreeCellRenderer()
        TreeSpeedSearch.installOn(fixtureTree)

        fixtureTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelected()
            }
        })
        fixtureTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToSelected()
                    e.consume()
                }
            }
        })
    }

    private fun updateTree(snapshot: CollectionSnapshot) {
        lastSnapshot = snapshot
        val root = PytestExplorerTreeBuilder.buildFixtureExplorerTree(snapshot)
        fixtureTree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(fixtureTree)
        val fixtureCount = snapshot.fixtures.size
        statusLabel.text = "$fixtureCount fixture${if (fixtureCount != 1) "s" else ""}"
    }

    private fun navigateToSelected() {
        val node = fixtureTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        when (val userObj = node.userObject) {
            is FixtureDisplayNode -> {
                val fixture =
                    CollectedFixture(userObj.name, userObj.scope, userObj.definedIn, userObj.name, emptyList())
                val pointer = PytestPsiResolver.resolveFixture(project, fixture)
                pointer?.element?.navigate(true)
            }

            is TestConsumerNode -> {
                val pointer = PytestPsiResolver.resolveTest(project, userObj.test)
                pointer?.element?.navigate(true)
            }
        }
    }

    override fun dispose() {
        service.removeListener(collectionListener)
    }

    private inner class RefreshAction : AnAction("Refresh", "Re-collect fixtures", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            PytestCollectorTask(project).queue()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ExpandAllAction : AnAction("Expand All", null, AllIcons.Actions.Expandall) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.expandAll(fixtureTree)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class CollapseAllAction : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.collapseAll(fixtureTree, 0)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}
