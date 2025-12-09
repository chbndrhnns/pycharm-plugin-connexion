package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.ide.util.treeView.NodeDescriptor
import fixtures.TestBase
import javax.swing.tree.DefaultMutableTreeNode

class TestProxyExtractorTest : TestBase() {

    fun testExtractDirectProxy() {
        val proxy = FakeSMTestProxy("test", false)
        val node = DefaultMutableTreeNode(proxy)

        val extracted = TestProxyExtractor.getTestProxy(node)
        assertEquals(proxy, extracted)
    }

    fun testExtractFromDescriptor() {
        val proxy = FakeSMTestProxy("test", false)
        // NodeDescriptor constructor: NodeDescriptor(@Nullable Project project, @Nullable NodeDescriptor parentDescriptor)
        val descriptor = object : NodeDescriptor<SMTestProxy>(project, null) {
            override fun update(): Boolean = false
            override fun getElement(): SMTestProxy = proxy
        }
        val node = DefaultMutableTreeNode(descriptor)

        val extracted = TestProxyExtractor.getTestProxy(node)
        assertEquals(proxy, extracted)
    }

    fun testExtractFromNull() {
        val node = DefaultMutableTreeNode(null)
        val extracted = TestProxyExtractor.getTestProxy(node)
        assertNull(extracted)
    }

    fun testExtractFromOtherObject() {
        val node = DefaultMutableTreeNode("StringObject")
        val extracted = TestProxyExtractor.getTestProxy(node)
        assertNull(extracted)
    }

    class FakeSMTestProxy(name: String, isSuite: Boolean) : SMTestProxy(name, isSuite, null)
}
