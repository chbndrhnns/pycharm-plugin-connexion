package com.github.chbndrhnns.intellijplatformplugincopy.navbar

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression

/**
 * Navigation bar extension for Python that shows file members (classes, functions, methods)
 * in the navigation bar when "Show Members in Navigation Bar" is enabled.
 */
class PyNavBarModelExtension : StructureAwareNavBarModelExtension() {

    override val language: Language
        get() = PythonLanguage.getInstance()

    override fun getPresentableText(item: Any?): String? {
        return getPresentableText(item, true)
    }

    override fun getPresentableText(item: Any?, forCurrentFile: Boolean): String? {
        // Check if the feature is enabled in plugin settings
        if (!PluginSettingsState.instance().state.enablePythonNavigationBar) {
            return null
        }

        val element = item as? PsiElement ?: return null
        val name = when (element) {
            is PyFunction -> element.name
            is PyClass -> element.name
            is PyTargetExpression -> element.name
            else -> null
        } ?: return null

        if (name.isDunder()) {
            return null
        }

        return when (element) {
            is PyFunction -> if (element.isAsync) "async $name()" else "$name()"
            else -> name
        }
    }

    override fun processChildren(`object`: Any, rootElement: Any?, processor: Processor<Any>): Boolean {
        val psiElement = `object` as? PsiElement ?: return true

        // Check if the feature is enabled in plugin settings
        if (!PluginSettingsState.instance().state.enablePythonNavigationBar) {
            return true
        }

        val children = when (psiElement) {
            is PyFile -> {
                val list = mutableListOf<PsiElement>()
                list.addAll(PsiTreeUtil.getChildrenOfTypeAsList(psiElement, PyClass::class.java))
                list.addAll(PsiTreeUtil.getChildrenOfTypeAsList(psiElement, PyFunction::class.java))
                list.addAll(PsiTreeUtil.getChildrenOfTypeAsList(psiElement, PyTargetExpression::class.java))
                list
            }

            is PyClass -> {
                val list = mutableListOf<PsiElement>()
                list.addAll(psiElement.methods)
                list.addAll(psiElement.nestedClasses)
                list.addAll(psiElement.classAttributes)
                list
            }

            else -> emptyList()
        }

        for (child in children) {
            if (shouldInclude(child)) {
                if (!processor.process(child)) return false
            }
        }

        return true
    }

    private fun shouldInclude(element: PsiElement): Boolean {
        val name = when (element) {
            is PyFunction -> element.name
            is PyClass -> element.name
            is PyTargetExpression -> element.name
            else -> null
        } ?: return false

        return !name.isDunder()
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement {
        // Only adjust elements that belong to Python
        if (psiElement.language != language && psiElement !is PyFile) {
            return psiElement
        }

        var current = psiElement
        while (current !is PyFile && !current.isPythonMember()) {
            val parent = current.parent
            // Stop if we reach a non-Python element or null
            if (parent == null || (parent.language != language && parent !is PyFile)) {
                return current
            }
            current = parent
        }
        return current
    }

    override fun acceptParentFromModel(psiElement: PsiElement?): Boolean {
        return true
    }

    private fun PsiElement.isPythonMember(): Boolean =
        this is PyFunction || this is PyClass || this is PyTargetExpression

    private fun String.isDunder(): Boolean =
        startsWith("__") && endsWith("__")
}
