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
                val textAttributes =
                    if (node.isSkipped) SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                append(node.path, textAttributes)
                if (node.isSkipped) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(skipped)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                }
            }

            is ClassTreeNode -> {
                icon = AllIcons.Nodes.Class
                val textAttributes =
                    if (node.isSkipped) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
                append(node.name, textAttributes)
                if (node.isSkipped) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(skipped)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                }
            }

            is TestTreeNode -> {
                icon = AllIcons.Nodes.Method
                val textAttributes =
                    if (node.test.isSkipped) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
                append(node.test.functionName, textAttributes)
                if (node.test.isSkipped) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(skipped)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                } else if (node.test.fixtures.isNotEmpty()) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(${node.test.fixtures.size} fixtures)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }

            is FlatTestTreeNode -> {
                icon = AllIcons.Nodes.Method
                val textAttributes =
                    if (node.test.isSkipped) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
                append(node.label, textAttributes)
                if (node.test.isSkipped) {
                    append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("(skipped)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                } else if (node.test.fixtures.isNotEmpty()) {
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

            is FixtureModuleGroupNode -> {
                icon = AllIcons.Nodes.Module
                append(node.modulePath, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
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

            is MarkerGroupNode -> {
                icon = AllIcons.Nodes.Tag
                append(node.markerName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("  (${node.testCount})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is MarkerTestNode -> {
                icon = AllIcons.Nodes.Method
                append(node.test.nodeId, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

            is String -> {
                append(node, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}
