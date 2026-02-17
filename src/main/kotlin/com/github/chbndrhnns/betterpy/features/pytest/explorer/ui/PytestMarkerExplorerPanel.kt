package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard
import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
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

class PytestMarkerExplorerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val LOG = Logger.getInstance(PytestMarkerExplorerPanel::class.java)

    private val markerTree = Tree()
    private val service = PytestExplorerService.getInstance(project)
    private val statusLabel = JLabel("Ready")

    private var lastSnapshot: CollectionSnapshot? = null

    private val collectionListener = CollectionListener { snapshot ->
        SwingUtilities.invokeLater { updateTree(snapshot) }
    }

    init {
        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(markerTree), BorderLayout.CENTER)

        statusLabel.border = JBUI.Borders.empty(2, 5)
        add(statusLabel, BorderLayout.SOUTH)

        setupTree()

        service.addListener(collectionListener)

        if (!PythonVersionGuard.hasPythonSdk(project)) {
            statusLabel.text = "Waiting for Python SDK..."
        } else {
            service.getSnapshot()?.let { updateTree(it) }
        }
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(ExpandAllAction())
            add(CollapseAllAction())
        }
        return ActionManager.getInstance()
            .createActionToolbar("PytestMarkerExplorer", group, true)
    }

    private fun setupTree() {
        markerTree.isRootVisible = false
        markerTree.cellRenderer = PytestTreeCellRenderer()
        TreeSpeedSearch.installOn(markerTree)

        markerTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelected()
            }
        })
        markerTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToSelected()
                }
            }
        })
    }

    private fun updateTree(snapshot: CollectionSnapshot) {
        lastSnapshot = snapshot
        rebuildTree(snapshot)
    }

    private fun rebuildTree(snapshot: CollectionSnapshot) {
        val root = PytestExplorerTreeBuilder.buildMarkerTree(snapshot)
        markerTree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(markerTree)

        val markerCount = root.childCount
        val testCount = snapshot.tests.count { it.markers.isNotEmpty() }
        statusLabel.text = "$markerCount markers, $testCount marked tests"
    }

    private fun navigateToSelected() {
        val node = markerTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        when (val userObject = node.userObject) {
            is MarkerTestNode -> {
                val test = userObject.test
                val element = PytestPsiResolver.resolveTestElement(project, test)
                (element as? com.intellij.pom.Navigatable)?.navigate(true)
            }
        }
    }

    override fun dispose() {
        service.removeListener(collectionListener)
    }

    private inner class RefreshAction : AnAction(
        "Refresh",
        "Refresh marker list",
        AllIcons.Actions.Refresh
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            PytestCollectorTask(project).queue()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = PythonVersionGuard.hasPythonSdk(project)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ExpandAllAction : AnAction(
        "Expand All",
        "Expand all nodes",
        AllIcons.Actions.Expandall
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.expandAll(markerTree)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class CollapseAllAction : AnAction(
        "Collapse All",
        "Collapse all nodes",
        AllIcons.Actions.Collapseall
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.collapseAll(markerTree, 1)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}
