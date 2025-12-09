package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.ide.util.treeView.NodeDescriptor
import javax.swing.tree.DefaultMutableTreeNode

object TestProxyExtractor {
    fun getTestProxy(node: DefaultMutableTreeNode): SMTestProxy? {
        val userObject = node.userObject ?: return null

        if (userObject is SMTestProxy) {
            return userObject
        }

        if (userObject is NodeDescriptor<*>) {
            val element = userObject.element
            if (element is SMTestProxy) {
                return element
            }
        }

        return null
    }
}
