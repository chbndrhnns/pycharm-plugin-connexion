package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.collection.PytestCollectorTask
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.CollectionListener
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.CollectionStartedListener
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.PytestExplorerService
import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
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

class PytestExplorerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {

    private val LOG = Logger.getInstance(PytestExplorerPanel::class.java)

    private val testTree = Tree()
    private val fixtureDetailPanel = FixtureDetailPanel(project)
    private val service = PytestExplorerService.getInstance(project)
    private val statusLabel = JLabel("Ready")

    private var lastErrors: List<String> = emptyList()
    private var scopeToCurrentFile = false
    private var flatView = false
    private var followCaret = false
    private var filterText: String? = null
    private var lastSnapshot: CollectionSnapshot? = null
    private var currentEditorFile: VirtualFile? = null
    private var caretListener: CaretListener? = null

    private val collectionListener = CollectionListener { snapshot ->
        SwingUtilities.invokeLater { updateTree(snapshot) }
    }

    private val collectionStartedListener = CollectionStartedListener {
        SwingUtilities.invokeLater {
            statusLabel.icon = null
            statusLabel.text = "Collecting..."
        }
    }

    init {
        currentEditorFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    currentEditorFile = event.newFile
                    if (scopeToCurrentFile) {
                        lastSnapshot?.let { applyTreeUpdate(it) }
                    }
                    reattachCaretListener()
                    if (followCaret) {
                        followCaretToNode()
                    }
                }
            }
        )

        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)

        val splitPane = JBSplitter(false, 0.5f).apply {
            firstComponent = JBScrollPane(testTree)
            secondComponent = fixtureDetailPanel
        }
        add(splitPane, BorderLayout.CENTER)

        statusLabel.border = JBUI.Borders.empty(2, 5)
        statusLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (lastErrors.isNotEmpty()) {
                    openErrorsInScratchFile()
                }
            }
        })
        add(statusLabel, BorderLayout.SOUTH)

        setupTree()

        service.addListener(collectionListener)
        service.addStartListener(collectionStartedListener)
        service.getSnapshot()?.let { updateTree(it) }
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ScopeToCurrentFileAction())
            add(FlatViewAction())
            add(FollowCaretAction())
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
        val speedSearch = TreeSpeedSearch.installOn(testTree)
        speedSearch.addChangeListener {
            val newFilter = if (speedSearch.isPopupActive) {
                val text = speedSearch.enteredPrefix
                if (text.isNullOrBlank()) null else text
            } else {
                null
            }
            if (newFilter != filterText) {
                filterText = newFilter
                lastSnapshot?.let { applyTreeUpdate(it) }
                if (newFilter != null) {
                    TreeUtil.expandAll(testTree)
                }
            }
        }

        testTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelectedTest()
                }
            }
        })

        testTree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToSelectedTest()
                    e.consume()
                }
            }
        })

        testTree.addTreeSelectionListener {
            val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val snapshot = service.getSnapshot() ?: return@addTreeSelectionListener
            when (val userObj = node.userObject) {
                is TestTreeNode -> fixtureDetailPanel.showFixturesFor(userObj.test, snapshot)
                is FlatTestTreeNode -> fixtureDetailPanel.showFixturesFor(userObj.test, snapshot)
                is ParametrizeTreeNode -> fixtureDetailPanel.showFixturesFor(userObj.test, snapshot)
                else -> fixtureDetailPanel.clear()
            }
        }
    }

    private fun updateTree(snapshot: CollectionSnapshot) {
        lastSnapshot = snapshot
        applyTreeUpdate(snapshot)
    }

    private fun applyTreeUpdate(snapshot: CollectionSnapshot) {
        LOG.debug("Updating tree: ${snapshot.tests.size} tests, ${snapshot.fixtures.size} fixtures")
        var displaySnapshot = if (scopeToCurrentFile) filterToCurrentFile(snapshot) else snapshot
        val query = filterText
        if (query != null) {
            val lowerQuery = query.lowercase()
            displaySnapshot = displaySnapshot.copy(
                tests = displaySnapshot.tests.filter { test ->
                    test.functionName.lowercase().contains(lowerQuery) ||
                            test.className?.lowercase()?.contains(lowerQuery) == true ||
                            test.modulePath.lowercase().contains(lowerQuery) ||
                            test.nodeId.lowercase().contains(lowerQuery)
                }
            )
        }
        val root = if (flatView) {
            PytestExplorerTreeBuilder.buildFlatTestTree(displaySnapshot)
        } else {
            PytestExplorerTreeBuilder.buildTestTree(displaySnapshot, collapseModuleNode = scopeToCurrentFile)
        }

        val expandedKeys = TreeStatePreserver.captureExpandedKeys(testTree)
        val selectedKey = TreeStatePreserver.captureSelectedKey(testTree)

        testTree.model = DefaultTreeModel(root)

        if (expandedKeys.isNotEmpty()) {
            TreeStatePreserver.restoreExpandedState(testTree, root, expandedKeys)
            selectedKey?.let { TreeStatePreserver.restoreSelectedState(testTree, root, it) }
        }

        lastErrors = displaySnapshot.errors
        if (displaySnapshot.errors.isNotEmpty()) {
            statusLabel.icon = AllIcons.General.Error
            statusLabel.text = "<html><a href=''>Collection completed with errors (click to view details)</a></html>"
            statusLabel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        } else {
            statusLabel.cursor = java.awt.Cursor.getDefaultCursor()
            statusLabel.icon = null
            val testCount = displaySnapshot.tests.size
            val fixtureCount = displaySnapshot.fixtures.size
            statusLabel.text = "$testCount tests, $fixtureCount fixtures collected"
        }
    }

    private fun navigateToSelectedTest() {
        val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val test = when (val userObj = node.userObject) {
            is TestTreeNode -> userObj.test
            is FlatTestTreeNode -> userObj.test
            is ParametrizeTreeNode -> userObj.test
            else -> return
        }
        navigateToTest(test)
    }

    private fun navigateToTest(test: CollectedTest) {
        LOG.debug("Navigating to test: ${test.nodeId}")
        val element = PytestPsiResolver.resolveTestElement(project, test)
        (element as? com.intellij.pom.Navigatable)?.navigate(true)
    }

    private fun openErrorsInScratchFile() {
        val content = lastErrors.joinToString("\n\n")
        val scratchFile = ScratchRootType.getInstance().createScratchFile(
            project,
            "pytest-collection-errors.txt",
            com.intellij.lang.Language.ANY,
            content,
            ScratchFileService.Option.create_if_missing,
        )
        if (scratchFile != null) {
            FileEditorManager.getInstance(project).openFile(scratchFile, true)
        }
    }

    override fun dispose() {
        service.removeListener(collectionListener)
        service.removeStartListener(collectionStartedListener)
    }

    private inner class RefreshAction :
        AnAction("Refresh", "Re-collect pytest tests and fixtures", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            LOG.info("Manual refresh triggered")
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

    private inner class FlatViewAction :
        ToggleAction("Flat View", "Show tests in a flat list", AllIcons.Actions.ShowAsTree) {
        override fun isSelected(e: AnActionEvent): Boolean = flatView

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            flatView = state
            lastSnapshot?.let { applyTreeUpdate(it) }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class CollapseAllAction : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
        override fun actionPerformed(e: AnActionEvent) {
            TreeUtil.collapseAll(testTree, 1)
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ScopeToCurrentFileAction :
        ToggleAction("Scope to Current File", "Show only tests from the current editor file", AllIcons.General.Filter) {

        override fun isSelected(e: AnActionEvent): Boolean = scopeToCurrentFile

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            scopeToCurrentFile = state
            lastSnapshot?.let { applyTreeUpdate(it) }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private fun reattachCaretListener() {
        // Remove old listener
        caretListener?.let { listener ->
            FileEditorManager.getInstance(project).allEditors
                .filterIsInstance<TextEditor>()
                .forEach { it.editor.caretModel.removeCaretListener(listener) }
        }
        if (!followCaret) {
            caretListener = null
            return
        }
        val editor = (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.editor ?: return
        val listener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                SwingUtilities.invokeLater { followCaretToNode() }
            }
        }
        caretListener = listener
        editor.caretModel.addCaretListener(listener)
    }

    private fun followCaretToNode() {
        if (!followCaret) return
        val editor = (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.editor ?: return
        val vFile = editor.virtualFile ?: return
        val offset = editor.caretModel.offset

        val match = ReadAction.compute<Pair<String, String?>?, Throwable> {
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@compute null
            val element = psiFile.findElementAt(offset) ?: return@compute null
            val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return@compute null
            val functionName = function.name ?: return@compute null
            val className = PsiTreeUtil.getParentOfType(function, PyClass::class.java)?.name
            functionName to className
        } ?: return

        // Find matching node in tree
        val root = testTree.model.root as? DefaultMutableTreeNode ?: return
        val targetNode = PytestExplorerTreeBuilder.findTestNode(root, match.first, match.second)
        if (targetNode != null) {
            val path = javax.swing.tree.TreePath(targetNode.path)
            testTree.selectionPath = path
            testTree.scrollPathToVisible(path)
        }
    }

    private inner class FollowCaretAction :
        ToggleAction("Follow Caret", "Select the test at the current caret position", AllIcons.General.Locate) {

        override fun isSelected(e: AnActionEvent): Boolean = followCaret

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            followCaret = state
            reattachCaretListener()
            if (state) followCaretToNode()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private fun filterToCurrentFile(snapshot: CollectionSnapshot): CollectionSnapshot {
        val file = currentEditorFile ?: return snapshot
        val basePath = project.basePath ?: return snapshot
        val relativePath = file.path.removePrefix("$basePath/")
        val filteredTests = snapshot.tests.filter { it.modulePath == relativePath }
        return snapshot.copy(tests = filteredTests)
    }
}
