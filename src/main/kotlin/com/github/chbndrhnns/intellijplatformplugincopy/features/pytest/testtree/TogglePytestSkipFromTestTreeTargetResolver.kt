package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.testtree

import com.github.chbndrhnns.intellijplatformplugincopy.core.pytest.PytestNaming
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import javax.swing.tree.DefaultMutableTreeNode

object TogglePytestSkipFromTestTreeTargetResolver {

    sealed class Target {
        data class Function(val function: PyFunction, val file: PyFile) : Target()
        data class Clazz(val clazz: PyClass, val file: PyFile) : Target()
        data class Module(val file: PyFile) : Target()
    }

    fun resolve(node: DefaultMutableTreeNode, project: Project): Target? {
        val proxy = TestProxyExtractor.getTestProxy(node) ?: return null

        if (!proxy.isSuite) {
            return targetFromProxy(proxy, project)
        }

        val direct = targetFromProxy(proxy, project)
        if (direct != null) return direct

        val leaf = firstResolvableLeaf(node, project) ?: return null
        val leafElement = leaf.psiElement
        val pyFile = leafElement.containingFile as? PyFile ?: return null

        return inferSuiteTarget(proxy, leafElement, pyFile)
    }

    private data class LeafResolution(val psiElement: PsiElement)

    private fun firstResolvableLeaf(node: DefaultMutableTreeNode, project: Project): LeafResolution? {
        val proxy = TestProxyExtractor.getTestProxy(node)
        if (proxy != null && proxy.isLeaf) {
            val record = PytestNodeIdGenerator.parseProxy(proxy, project)
            val element = record?.psiElement
            if (element != null) {
                return LeafResolution(element)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val found = firstResolvableLeaf(child, project)
            if (found != null) return found
        }

        return null
    }

    private fun inferSuiteTarget(proxy: SMTestProxy, leafElement: PsiElement, file: PyFile): Target? {
        val name = proxy.name

        if (name.endsWith(".py")) {
            return Target.Module(file)
        }

        if (PytestNaming.isTestClassName(name, allowLowercasePrefix = false)) {
            val clazz = PsiTreeUtil.getParentOfType(leafElement, PyClass::class.java, false) ?: return null
            if (!PytestTestContextUtils.isTestClass(clazz)) return null
            return Target.Clazz(clazz, file)
        }

        if (PytestNaming.isTestFunctionName(name)) {
            val function = PsiTreeUtil.getParentOfType(leafElement, PyFunction::class.java, false) ?: return null
            if (!PytestTestContextUtils.isTestFunction(function)) return null
            return Target.Function(function, file)
        }

        return null
    }

    private fun targetFromProxy(proxy: SMTestProxy, project: Project): Target? {
        val record = PytestNodeIdGenerator.parseProxy(proxy, project) ?: return null
        val element = record.psiElement ?: return null

        val pyFile = element.containingFile as? PyFile

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && PytestTestContextUtils.isTestFunction(function) && pyFile != null) {
            return Target.Function(function, pyFile)
        }

        val clazz = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        if (clazz != null && PytestTestContextUtils.isTestClass(clazz) && pyFile != null) {
            return Target.Clazz(clazz, pyFile)
        }

        if (element is PyFile) {
            return Target.Module(element)
        }

        return null
    }
}
