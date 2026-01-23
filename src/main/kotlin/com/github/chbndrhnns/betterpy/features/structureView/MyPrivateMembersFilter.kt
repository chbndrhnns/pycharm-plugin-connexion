package com.github.chbndrhnns.betterpy.features.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.util.FileStructureFilter
import com.intellij.ide.util.treeView.smartTree.ActionPresentation
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.actionSystem.Shortcut

class MyPrivateMembersFilter : FileStructureFilter {
    override fun isVisible(treeNode: TreeElement): Boolean {
        val presentation = treeNode.presentation
        val name = presentation.presentableText
        return name == null || !name.startsWith("_")
    }

    override fun isReverted(): Boolean {
        return true // true = "Show Private Members" (checked = show, unchecked = hide)
    }

    override fun getPresentation(): ActionPresentation {
        return ActionPresentationData("BetterPy: Show Private Members", "Show or hide private members", AllIcons.Nodes.Private)
    }

    override fun getName(): String {
        return "MY_SHOW_PRIVATE_MEMBERS"
    }

    override fun getCheckBoxText(): String {
        return "BetterPy: Show private members"
    }

    override fun getShortcut(): Array<Shortcut> {
        return emptyArray()
    }
}
