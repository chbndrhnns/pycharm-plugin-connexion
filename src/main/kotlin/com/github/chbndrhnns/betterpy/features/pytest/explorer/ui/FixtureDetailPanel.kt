package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.github.chbndrhnns.betterpy.features.pytest.fixture.PytestFixtureResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class FixtureDetailPanel(
    private val project: Project,
    private val onFixtureNavigated: ((VirtualFile) -> Unit)? = null,
) : JPanel(BorderLayout()) {

    private val titleLabel = JBLabel("Select a test to view fixtures")
    private val fixtureTree = Tree()

    private var currentFixtureMap: Map<String, CollectedFixture> = emptyMap()
    private var currentTest: CollectedTest? = null

    init {
        border = JBUI.Borders.empty(5)
        add(titleLabel, BorderLayout.NORTH)
        add(JBScrollPane(fixtureTree), BorderLayout.CENTER)
        fixtureTree.isRootVisible = false
        fixtureTree.cellRenderer = PytestTreeCellRenderer()

        fixtureTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = fixtureTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    val userObj = node.userObject
                    if (userObj is FixtureDisplayNode) {
                        navigateToFixture(userObj)
                    }
                }
            }
        })
    }

    fun showFixturesFor(test: CollectedTest, snapshot: CollectionSnapshot) {
        titleLabel.text = "Fixtures for ${test.functionName}"
        currentTest = test

        // Show immediate tree with simple first-candidate mapping
        val quickMap = snapshot.fixtures.groupBy { it.name }.mapValues { (_, v) -> v.first() }
        currentFixtureMap = quickMap
        val quickRoot = PytestExplorerTreeBuilder.buildFixtureTree(test.fixtures, quickMap)
        fixtureTree.model = DefaultTreeModel(quickRoot)
        for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
            fixtureTree.expandRow(i)
        }

        // Resolve full PSI-based fixture map in background, then update UI
        val fixtures = snapshot.fixtures
        AppExecutorUtil.getAppExecutorService().execute {
            val resolvedMap = resolveFixtureMapViaPsi(test, fixtures)
            ApplicationManager.getApplication().invokeLater {
                if (currentTest == test) {
                    currentFixtureMap = resolvedMap
                    val root = PytestExplorerTreeBuilder.buildFixtureTree(test.fixtures, resolvedMap)
                    fixtureTree.model = DefaultTreeModel(root)
                    for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
                        fixtureTree.expandRow(i)
                    }
                }
            }
        }
    }

    /**
     * Resolves the best fixture for each name using [PytestFixtureResolver.findFixtureChain]
     * (PSI-based, follows full pytest precedence). Falls back to picking the first collected
     * candidate when PSI resolution is unavailable.
     */
    private fun resolveFixtureMapViaPsi(
        test: CollectedTest,
        fixtures: List<CollectedFixture>
    ): Map<String, CollectedFixture> {
        val fixturesByName = fixtures.groupBy { it.name }
        val testFunction = ReadAction.compute<PyFunction?, Throwable> {
            PytestPsiResolver.resolveTestElement(project, test)
        }

        if (testFunction == null) {
            // No PSI available — just pick first candidate per name
            return fixturesByName.mapValues { (_, candidates) -> candidates.first() }
        }

        return fixturesByName.mapValues { (fixtureName, candidates) ->
            if (candidates.size == 1) return@mapValues candidates.first()

            // Use the advanced resolver to find the winning fixture
            val resolved = ReadAction.compute<PyFunction?, Throwable> {
                val context = TypeEvalContext.codeAnalysis(project, testFunction.containingFile)
                PytestFixtureResolver.findFixtureChain(testFunction, fixtureName, context)
                    .firstOrNull()?.fixtureFunction
            }

            if (resolved != null) {
                // Match the resolved PSI function back to a CollectedFixture
                val resolvedPath = ReadAction.compute<String?, Throwable> {
                    resolved.containingFile?.virtualFile?.path
                }
                val resolvedName = ReadAction.compute<String?, Throwable> { resolved.name }
                candidates.firstOrNull { candidate ->
                    resolvedName == candidate.functionName && resolvedPath?.endsWith(
                        candidate.definedIn.substringBefore("::")
                    ) == true
                } ?: candidates.first()
            } else {
                candidates.first()
            }
        }
    }

    fun clear() {
        titleLabel.text = "Select a test to view fixtures"
        fixtureTree.model = DefaultTreeModel(null)
        currentFixtureMap = emptyMap()
        currentTest = null
    }

    private fun navigateToFixture(node: FixtureDisplayNode) {
        val fixtureName = node.name
        val test = currentTest

        // Try advanced resolver first: resolve from the test's PSI element
        if (test != null) {
            val resolved = ReadAction.compute<Navigatable?, Throwable> {
                val testFunction = PytestPsiResolver.resolveTestElement(project, test) ?: return@compute null
                val context = TypeEvalContext.codeAnalysis(project, testFunction.containingFile)
                val chain = PytestFixtureResolver.findFixtureChain(testFunction, fixtureName, context)
                chain.firstOrNull()?.fixtureFunction as? Navigatable
            }
            if (resolved != null) {
                notifyNavigation(resolved)
                resolved.navigate(true)
                return
            }
        }

        // Fallback: use collected fixture data (resolve in background to avoid EDT slowdown)
        val fixture = currentFixtureMap[fixtureName] ?: return
        AppExecutorUtil.getAppExecutorService().execute {
            val element = ReadAction.compute<PyFunction?, Throwable> {
                PytestPsiResolver.resolveFixtureElement(project, fixture)
            }
            val navigatable = element as? Navigatable ?: return@execute
            ApplicationManager.getApplication().invokeLater {
                notifyNavigation(navigatable)
                navigatable.navigate(true)
            }
        }
    }

    private fun notifyNavigation(target: Any) {
        if (onFixtureNavigated == null) return
        val file = ReadAction.compute<VirtualFile?, Throwable> {
            (target as? PyFunction)?.containingFile?.virtualFile
        }
        if (file != null) {
            onFixtureNavigated.invoke(file)
        }
    }
}
