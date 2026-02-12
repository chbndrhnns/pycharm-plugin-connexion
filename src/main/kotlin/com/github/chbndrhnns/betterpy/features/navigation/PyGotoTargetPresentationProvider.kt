package com.github.chbndrhnns.betterpy.features.navigation

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.navigation.GotoTargetPresentationProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class PyGotoTargetPresentationProvider : GotoTargetPresentationProvider {
    override fun getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
        if (!PluginSettingsState.instance().state.enablePyGotoTargetPresentation) return null
        return when (element) {
            is PyFunction -> presentationForFunction(element, differentNames)
            is PyClass -> presentationForClass(element)
            is PyTargetExpression -> presentationForAttribute(element)
            else -> null
        }
    }

    private fun presentationForFunction(element: PyFunction, differentNames: Boolean): TargetPresentation {
        val cls = element.containingClass
        val name = element.name ?: PyNames.UNNAMED_ELEMENT
        val classFqn = cls?.let {
            val classChain = buildClassChain(it)
            moduleName(it)?.let { module -> "$module.$classChain" } ?: classChain
        }
        val fqn =
            canonicalName(element, name) ?: classFqn?.let { "$it.$name" } ?: moduleName(element)?.let { "$it.$name" }

        val rawText = fqn ?: if (!differentNames && cls != null) "${cls.name}.$name" else name
        val text = if (!differentNames) stripCommonPrefix(rawText, moduleName(element)) else rawText

        return TargetPresentation.builder(text).containerText(if (fqn == null) cls?.name else null)
            .locationText(QualifiedNameFinder.findShortestImportableName(element, element.containingFile.virtualFile))
            .icon(element.getIcon(0)).presentation()
    }

    private fun presentationForClass(element: PyClass): TargetPresentation {
        val name = element.name ?: PyNames.UNNAMED_ELEMENT
        val classChain = buildClassChain(element)
        val fqn = canonicalName(element, name) ?: moduleName(element)?.let { "$it.$classChain" }
        val text = fqn ?: classChain

        return TargetPresentation.builder(text)
            .locationText(QualifiedNameFinder.findShortestImportableName(element, element.containingFile.virtualFile))
            .icon(element.getIcon(0)).presentation()
    }

    private fun presentationForAttribute(element: PyTargetExpression): TargetPresentation {
        val name = element.name ?: PyNames.UNNAMED_ELEMENT
        val containingClass = element.containingClass
        val classFqn = containingClass?.let {
            val classChain = buildClassChain(it)
            moduleName(it)?.let { module -> "$module.$classChain" } ?: classChain
        }
        val fqn =
            canonicalName(element, name) ?: classFqn?.let { "$it.$name" } ?: moduleName(element)?.let { "$it.$name" }
        val rawText = fqn ?: name
        val text = stripCommonPrefix(rawText, moduleName(element))

        return TargetPresentation.builder(text).containerText(if (fqn == null) containingClass?.name else null)
            .locationText(QualifiedNameFinder.findShortestImportableName(element, element.containingFile.virtualFile))
            .icon(element.getIcon(0))
            .presentation()
    }

    private fun canonicalName(element: PsiElement, expectedName: String): String? {
        val candidate = QualifiedNameFinder.findCanonicalImportPath(element, null)?.toString() ?: return null
        return if (candidate == expectedName || candidate.endsWith(".$expectedName")) candidate else null
    }

    private fun buildClassChain(cls: PyClass): String {
        val classNames = mutableListOf<String>()
        var current: PyClass? = cls
        while (current != null) {
            classNames.add(current.name ?: PyNames.UNNAMED_ELEMENT)
            current = PsiTreeUtil.getParentOfType(current, PyClass::class.java, true)
        }
        return classNames.asReversed().joinToString(".")
    }

    private fun moduleName(element: PsiElement): String? {
        val file = element.containingFile ?: return null
        return QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString()
    }

    private fun stripCommonPrefix(text: String, moduleName: String?): String {
        val module = moduleName?.takeIf { it.isNotBlank() } ?: return text
        val prefix = "$module."
        return if (text.startsWith(prefix) && text.length > prefix.length) text.removePrefix(prefix) else text
    }
}
