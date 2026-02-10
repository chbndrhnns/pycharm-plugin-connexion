package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

class FixtureDetailPanel : JPanel(BorderLayout()) {

    private val titleLabel = JBLabel("Select a test to view fixtures")
    private val fixtureTree = Tree()

    init {
        border = JBUI.Borders.empty(5)
        add(titleLabel, BorderLayout.NORTH)
        add(JBScrollPane(fixtureTree), BorderLayout.CENTER)
        fixtureTree.isRootVisible = false
        fixtureTree.cellRenderer = PytestTreeCellRenderer()
    }

    fun showFixturesFor(test: CollectedTest, snapshot: CollectionSnapshot) {
        titleLabel.text = "Fixtures for ${test.functionName}"
        val fixtureMap = snapshot.fixtures.associateBy { it.name }
        val root = PytestExplorerTreeBuilder.buildFixtureTree(test.fixtures, fixtureMap)
        fixtureTree.model = DefaultTreeModel(root)
        for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
            fixtureTree.expandRow(i)
        }
    }

    fun clear() {
        titleLabel.text = "Select a test to view fixtures"
        fixtureTree.model = DefaultTreeModel(null)
    }
}
