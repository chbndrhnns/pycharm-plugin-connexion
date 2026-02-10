package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.github.chbndrhnns.betterpy.features.pytest.fixture.PytestFixtureResolver
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.jetbrains.python.psi.types.TypeEvalContext
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class FixtureDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

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
        currentFixtureMap = resolveClosestFixtures(test, snapshot.fixtures)
        val root = PytestExplorerTreeBuilder.buildFixtureTree(test.fixtures, currentFixtureMap)
        fixtureTree.model = DefaultTreeModel(root)
        for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
            fixtureTree.expandRow(i)
        }
    }

    companion object {
        /**
         * For each fixture name, pick the definition whose directory is the closest
         * ancestor of the test's module directory. This follows pytest's fixture resolution
         * order: class-level > module-level > conftest in same dir > conftest in parent dir.
         */
        fun resolveClosestFixtures(
            test: CollectedTest,
            fixtures: List<CollectedFixture>
        ): Map<String, CollectedFixture> {
            val testDir = test.modulePath.substringBeforeLast("/", "")
            return fixtures
                .groupBy { it.name }
                .mapValues { (_, candidates) ->
                    candidates.maxByOrNull { candidate ->
                        val baseid = candidate.definedIn
                        // For class-level fixtures: baseid is "file.py::Class", use file part
                        val filePart = if ("::" in baseid) baseid.substringBefore("::") else baseid
                        val fixtureDir = filePart.substringBeforeLast("/", "")
                        if (testDir == fixtureDir || testDir.startsWith("$fixtureDir/") || (fixtureDir.isEmpty() && testDir.isNotEmpty())) {
                            // Same directory or ancestor directory — prefer closer (longer path)
                            fixtureDir.length * 1000 + if ("::" in baseid) 1 else 0
                        } else {
                            -1
                        }
                    } ?: candidates.last()
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
                resolved.navigate(true)
                return
            }
        }

        // Fallback: use collected fixture data
        val fixture = currentFixtureMap[fixtureName] ?: return
        val element = PytestPsiResolver.resolveFixtureElement(project, fixture)
        (element as? Navigatable)?.navigate(true)
    }
}
