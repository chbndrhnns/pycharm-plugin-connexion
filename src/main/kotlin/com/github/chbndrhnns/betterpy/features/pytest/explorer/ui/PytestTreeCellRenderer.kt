package com.github.chbndrhnns.betterpy.features.pytest.explorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class PytestTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject ?: return

        when (node) {
            is ModuleTreeNode -> {
                icon = AllIcons.Nodes.Module
                append(node.path, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }

            is ClassTreeNode -> {
                icon = AllIcons.Nodes.Class
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

            is TestTreeNode -> {
                icon = AllIcons.Nodes.Method
                append(node.test.functionName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (node.test.fixtures.isNotEmpty()) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(${node.test.fixtures.size} fixtures)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }

            is FlatTestTreeNode -> {
                icon = AllIcons.Nodes.Method
                append(node.label, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (node.test.fixtures.isNotEmpty()) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(${node.test.fixtures.size} fixtures)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }

            is ParametrizeTreeNode -> {
                icon = AllIcons.Nodes.Parameter
                append(node.parametrizeId, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

            is FixtureDisplayNode -> {
                icon = AllIcons.Nodes.Plugin
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append("  [${node.scope}]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is ScopeGroupNode -> {
                icon = AllIcons.Nodes.Folder
                append(node.scope, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }

            is OverrideGroupNode -> {
                icon = AllIcons.General.OverridingMethod
                append(node.fixtureName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append("  (${node.count} definitions)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is TestConsumerNode -> {
                icon = AllIcons.Nodes.Method
                append(node.test.nodeId, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is String -> {
                append(node, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}
