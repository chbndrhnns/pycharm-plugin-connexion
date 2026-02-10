package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
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
        currentFixtureMap = snapshot.fixtures.associateBy { it.name }
        val root = PytestExplorerTreeBuilder.buildFixtureTree(test.fixtures, currentFixtureMap)
        fixtureTree.model = DefaultTreeModel(root)
        for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
            fixtureTree.expandRow(i)
        }
    }

    fun clear() {
        titleLabel.text = "Select a test to view fixtures"
        fixtureTree.model = DefaultTreeModel(null)
        currentFixtureMap = emptyMap()
    }

    private fun navigateToFixture(node: FixtureDisplayNode) {
        val fixture = currentFixtureMap[node.name] ?: return
        ReadAction.run<Throwable> {
            val pointer = PytestPsiResolver.resolveFixture(project, fixture)
            pointer?.element?.let {
                (it as? Navigatable)?.navigate(true)
            }
        }
    }
}
