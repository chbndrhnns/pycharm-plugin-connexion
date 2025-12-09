package com.github.chbndrhnns.intellijplatformplugincopy.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.Filter
import com.intellij.ide.util.treeView.smartTree.TreeElement

class MyPrivateMembersFilter : Filter {
    override fun isVisible(treeNode: TreeElement): Boolean {
        val presentation = treeNode.presentation
        val name = presentation.presentableText
        return name == null || !name.startsWith("_")
    }

    override fun isReverted(): Boolean {
        return true // true = "Show Private Members" (checked = show, unchecked = hide)
    }

    override fun getPresentation(): ActionPresentation {
        return ActionPresentationData("Show Private Members", "Show or hide private members", AllIcons.Nodes.Private)
    }

    override fun getName(): String {
        return "MY_SHOW_PRIVATE_MEMBERS"
    }
}
