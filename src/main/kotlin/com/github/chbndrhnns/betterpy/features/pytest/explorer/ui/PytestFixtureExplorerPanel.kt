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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
    private var grouping: FixtureGrouping = FixtureGrouping.BY_SCOPE
    private var scopeToCurrentModule = false
    private var currentEditorFile: VirtualFile? = null

    private val collectionListener = CollectionListener { snapshot ->
        SwingUtilities.invokeLater { updateTree(snapshot) }
    }

    init {
        currentEditorFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    currentEditorFile = event.newFile
                    if (scopeToCurrentModule) {
                        lastSnapshot?.let { rebuildTree(it) }
                    }
                }
            }
        )

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
            add(GroupingDropdownAction())
            add(ScopeToCurrentModuleAction())
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
        rebuildTree(snapshot)
    }

    private fun rebuildTree(snapshot: CollectionSnapshot) {
        val scopeModule = if (scopeToCurrentModule) {
            currentEditorFile?.let { resolveModulePath(it) }
        } else {
            null
        }
        val root = PytestExplorerTreeBuilder.buildFixtureExplorerTree(snapshot, grouping, scopeModule)
        fixtureTree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(fixtureTree)

        val fixtureCount = snapshot.fixtures.size
        val suffix = if (scopeModule != null) " (scoped to $scopeModule)" else ""
        statusLabel.text = "$fixtureCount fixture${if (fixtureCount != 1) "s" else ""}$suffix"
    }

    private fun resolveModulePath(file: VirtualFile): String? {
        val basePath = project.basePath ?: return file.name
        val filePath = file.path
        return if (filePath.startsWith(basePath)) {
            filePath.removePrefix(basePath).removePrefix("/")
        } else {
            file.name
        }
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

    private inner class GroupingDropdownAction : DefaultActionGroup(
        "Group By", true
    ) {
        init {
            templatePresentation.icon = AllIcons.Actions.GroupBy
            add(SetGroupingAction("By Scope", FixtureGrouping.BY_SCOPE))
            add(SetGroupingAction("By Test Module", FixtureGrouping.BY_TEST_MODULE))
            add(SetGroupingAction("Flat List", FixtureGrouping.FLAT))
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class SetGroupingAction(
        text: String,
        private val target: FixtureGrouping,
    ) : ToggleAction(text) {
        override fun isSelected(e: AnActionEvent): Boolean = grouping == target

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                grouping = target
                lastSnapshot?.let { rebuildTree(it) }
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ScopeToCurrentModuleAction : ToggleAction(
        "Scope to Current Module",
        "Show only fixtures used by the current test module",
        AllIcons.General.Filter,
    ) {
        override fun isSelected(e: AnActionEvent): Boolean = scopeToCurrentModule

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            scopeToCurrentModule = state
            lastSnapshot?.let { rebuildTree(it) }
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
