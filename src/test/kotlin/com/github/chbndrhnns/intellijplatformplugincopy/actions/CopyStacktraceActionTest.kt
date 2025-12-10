package com.github.chbndrhnns.intellijplatformplugincopy.actions

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import fixtures.TestBase
import javax.swing.tree.DefaultMutableTreeNode

class CopyStacktraceActionTest : TestBase() {

    fun testCopyStacktraceFromFailedTest() {
        val proxy = FakeSMTestProxy("test_fail", false, null)
        proxy.setTestFailed("Error message", "Stacktrace line 1\nStacktrace line 2", true)
        
        val node = DefaultMutableTreeNode(proxy)
        
        val result = mutableListOf<String>()
        CopyStacktraceAction().collect(node, result, project)
        
        assertEquals(1, result.size)
        // SMTestProxy.getStacktrace() returns stacktrace.
        assertEquals("Stacktrace line 1\nStacktrace line 2", result[0])
    }
    
    fun testCopyStacktraceFromPassedTest() {
        val proxy = FakeSMTestProxy("test_pass", false, null)
        proxy.setFinished()
        
        val node = DefaultMutableTreeNode(proxy)
        
        val result = mutableListOf<String>()
        CopyStacktraceAction().collect(node, result, project)
        
        assertEquals(0, result.size)
    }

    fun testCopyStacktraceMultiple() {
         val rootProxy = FakeSMTestProxy("Root", true, null)
         val rootNode = DefaultMutableTreeNode(rootProxy)
         
         val fail1 = FakeSMTestProxy("fail1", false, null)
         fail1.setTestFailed("Msg1", "Trace1", true)
         val fail1Node = DefaultMutableTreeNode(fail1)
         rootNode.add(fail1Node)
         
         val pass1 = FakeSMTestProxy("pass1", false, null)
         pass1.setFinished()
         val pass1Node = DefaultMutableTreeNode(pass1)
         rootNode.add(pass1Node)

         val fail2 = FakeSMTestProxy("fail2", false, null)
         fail2.setTestFailed("Msg2", "Trace2", true)
         val fail2Node = DefaultMutableTreeNode(fail2)
         rootNode.add(fail2Node)
         
         val result = mutableListOf<String>()
         CopyStacktraceAction().collect(rootNode, result, project)
         
         assertEquals(2, result.size)
         assertEquals("Trace1", result[0])
         assertEquals("Trace2", result[1])
    }

    private class FakeSMTestProxy(
        name: String,
        isSuite: Boolean,
        private val element: PsiElement?,
        locationUrl: String? = null
    ) : SMTestProxy(name, isSuite, locationUrl) {

        override fun getLocation(project: Project, scope: GlobalSearchScope): Location<*>? {
            return element?.let { PsiLocation(it) }
        }
    }
}
